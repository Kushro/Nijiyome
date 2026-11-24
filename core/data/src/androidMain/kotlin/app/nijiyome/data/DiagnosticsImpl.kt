// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.nijiyome.database.NijiyomeDatabase
import app.nijiyome.link.AesGcmKeyCipher
import app.nijiyome.link.KeyCipher
import app.nijiyome.model.AppDataWiper
import app.nijiyome.model.AppError
import app.nijiyome.model.AppException
import app.nijiyome.model.AppReset
import app.nijiyome.model.ExtensionRepository
import app.nijiyome.model.LinkRepository
import app.nijiyome.model.LogLevel
import app.nijiyome.model.LogRepository
import app.nijiyome.model.LogSink
import app.nijiyome.model.MetadataRepository
import app.nijiyome.model.SecretStore
import app.nijiyome.model.UpdateInfo
import app.nijiyome.model.UpdateRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.KeyStore
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

// Parity wave A (docs/redesign/15-parity-wave-a-contract-android.md §3.4). Owner: worker q.

// ==================================================================== app log

// AppLogStore lives in commonMain (AppLogStore.kt): desktop uses the same file log.

// ==================================================================== secrets

/** The non-exportable AES key of the secret store (alias [ALIAS]) in AndroidKeyStore; created on first use. */
object SecretsKeystoreKey {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    const val ALIAS = "nijiyome-secrets"

    fun cipher(): KeyCipher = AesGcmKeyCipher(::secretKey)

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }
}

/**
 * [SealedFileSecretStore] with the AndroidKeyStore AES-GCM key ([SecretsKeystoreKey], alias `nijiyome-secrets`) under `noBackupFilesDir`.
 * [cipher] is injectable so the JVM tests use a plain AES key.
 */
class KeystoreSecretStore(
    dir: File,
    cipher: KeyCipher = SecretsKeystoreKey.cipher(),
) : SealedFileSecretStore(dir, cipher)

// ==================================================================== reset

/**
 * "Restablecer la app" (checklist system.reset-app): every step is best effort (one failing collaborator never leaves the app half
 * reset without trying the rest) and the first failure is reported after all ran. Order: extensions, Link pairing (`forget`, which also
 * revokes this phone on the PC when reachable), history cursor, secrets, metadata cache, Room (`clearAllTables`), app log, shaders,
 * other modules' SharedPreferences, image cache, then the settings DataStore last (so `onboardingDone` reads false only when the rest
 * is gone). The UI recreates the activity afterwards and the shell shows the first-run wizard again.
 */
class AppResetImpl(
    private val context: Context,
    private val settingsStore: DataStore<Preferences>,
    private val database: NijiyomeDatabase,
    private val secrets: SecretStore,
    private val metadata: MetadataRepository? = null,
    private val extensions: ExtensionRepository? = null,
    private val link: LinkRepository? = null,
    private val logs: LogRepository? = null,
    private val history: HistorySyncer? = null,
    /** parity wave B (16 §3.8): module-private stores of the new core modules (`getAll<AppDataWiper>()` in dataModule). */
    private val wipers: List<AppDataWiper> = emptyList(),
) : AppReset {
    override suspend fun resetAll(): Result<Unit> = withContext(Dispatchers.IO) {
        var failure: Throwable? = null
        suspend fun step(block: suspend () -> Unit) {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (failure == null) failure = e
            }
        }
        step { extensions?.let { repo -> repo.installed.value.forEach { ext -> repo.remove(ext.id) } } }
        step { link?.forget() }
        step { history?.resetStamp() }
        step { secrets.clear() }
        step { metadata?.clearCache() }
        step { database.clearAllTables() }
        step { logs?.clear() }
        step { File(context.filesDir, "shaders").deleteRecursively() }
        // Subtitle fonts imported in the player settings (worker p, `SubtitleFonts`).
        step { File(context.filesDir, "fonts").deleteRecursively() }
        // parity wave B: downloads, followed shows, reader progress, chapter downloads, Stremio addons, social caches (each module's wiper).
        wipers.forEach { wiper -> step { wiper.wipe() } }
        step {
            val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
            prefsDir.listFiles()?.filter { it.name.endsWith(".xml") }?.forEach { f ->
                val name = f.name.removeSuffix(".xml")
                context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
                context.deleteSharedPreferences(name)
            }
        }
        step { context.cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
        step {
            settingsStore.edit { it.clear() }
            // `ls files/` shows no settings file afterwards; DataStore recreates it on the next write.
            File(context.filesDir, "datastore/settings.preferences_pb").delete()
        }
        AppLogAfterReset.note()
        failure?.let { Result.failure(AppException(AppError.Unknown("reset:${it::class.java.simpleName}"), it)) } ?: Result.success(Unit)
    }
}

/** Writes one line into the (now empty) log so a shared log shows that the app was reset. */
private object AppLogAfterReset {
    fun note() = app.nijiyome.model.AppLog.i("reset", "app reset to first run")
}
