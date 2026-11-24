// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.data

import app.nijiyome.link.AesGcmKeyCipher
import app.nijiyome.model.AppLog
import app.nijiyome.model.SecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.spec.SecretKeySpec

/**
 * Where the desktop secret store keeps its 256-bit master key. The secrets themselves (list tokens, debrid and translation keys,
 * extension secrets) live in `<data>/secrets.bin`, sealed with AES-GCM under that key ([SealedFileSecretStore], the same file
 * format as Android); only the key goes to the system keyring, so the keyring holds one item and is asked once per run.
 */
interface MasterKeyVault {
    /** Recorded in `secrets.backend` so the next run reads the key from the same place. */
    val id: String

    /** The stored key, or null when there is none. Throws [KeyringUnavailable] when the vault cannot be asked right now. */
    fun load(): ByteArray?

    /** Stores (replaces) the key. Throws [KeyringUnavailable] when the vault cannot store it. */
    fun store(key: ByteArray)

    /** Removes the key (sign-out of everything, reset). Best effort. */
    fun delete()

    /**
     * 1.1.2: the key of a vault that, before 1.1.2, was shared by every data folder (the Secret Service item without a folder
     * attribute); null when there is none or this vault never had one (files and DPAPI were always per folder). Throws
     * [KeyringUnavailable] like [load].
     */
    fun loadLegacy(): ByteArray? = null
}

/** The keyring is missing, locked or refused (no Secret Service on the bus, the user cancelled the unlock prompt...). */
class KeyringUnavailable(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Linux: the freedesktop Secret Service (GNOME Keyring, KWallet, KeePassXC...) on the session D-Bus, through `secret-tool`, the
 * command-line client of libsecret. One item per data folder (1.1.2): `application=app.nijiyome`, `purpose=data-folder-key` and
 * `data-folder=<id>`, where the id is the random [DataFolderId] kept in the folder (so moving the folder keeps its item, and a second
 * folder, such as a test run with another `NIJIYOME_DATA_DIR`, never touches the first one's key). The value is the key in base64,
 * written on the tool's standard input (never on a command line). [command] and [env] are injectable for tests (a private D-Bus
 * session with its own keyring daemon).
 *
 * Before 1.1.2 there was one item for every folder (`application=app.nijiyome`, `purpose=secrets-key`): [loadLegacy] reads it so
 * [DesktopSecretStore] can copy it to the folder whose file it opens. The new items use another `purpose` on purpose: Secret Service
 * lookups and clears match every item that has the given attributes, so a lookup or a clear of the old pair (by 1.1.1) never
 * reaches a folder's own item.
 */
class SecretServiceVault(
    /** The program and any leading arguments (`secret-tool`; tests run a stand-in that never answers). */
    private val command: List<String> = listOf("secret-tool"),
    private val env: Map<String, String> = emptyMap(),
    private val timeoutSeconds: Long = 60,
    /** The id of the data folder whose key this vault keeps (read when first needed: [DataFolderId.of]). */
    private val folderId: () -> String,
) : MasterKeyVault {
    override val id: String = ID

    private val attributes: List<String> by lazy { listOf("application", APPLICATION, "purpose", PURPOSE, FOLDER_ATTRIBUTE, folderId()) }

    override fun load(): ByteArray? = lookup(attributes)

    override fun store(key: ByteArray) {
        val result = run(listOf("store", "--label=$LABEL") + attributes, stdin = Base64.getEncoder().encodeToString(key))
        if (result.exit != 0) throw KeyringUnavailable("secret-tool store: ${result.stderr.trim().take(200)}")
    }

    override fun delete() {
        runCatching { run(listOf("clear") + attributes) }
    }

    override fun loadLegacy(): ByteArray? = lookup(LEGACY_ATTRIBUTES)

    private fun lookup(attrs: List<String>): ByteArray? {
        val result = run(listOf("lookup") + attrs)
        // secret-tool answers 1 and prints nothing for "no such item"; anything on stderr is a real failure (no service, locked).
        if (result.exit == 0) return decode(result.stdout)
        val stderr = withoutJvmWarnings(result.stderr)
        if (stderr.isBlank()) return null
        throw KeyringUnavailable("secret-tool lookup: ${stderr.trim().take(200)}")
    }

    private fun decode(text: String): ByteArray? =
        runCatching { Base64.getDecoder().decode(text.trim()) }.getOrNull()?.takeIf { it.size == KEY_BYTES }

    private data class Outcome(val exit: Int, val stdout: String, val stderr: String)

    /**
     * Runs the tool with a hard deadline: its output is collected on two reader threads, so a `secret-tool` that never answers
     * (a locked keyring waiting on an unlock prompt, a stuck D-Bus) is killed after [timeoutSeconds] instead of blocking the
     * caller (and the secret store's lock) for ever. Output is capped at 64 KiB per stream.
     */
    private fun run(args: List<String>, stdin: String? = null): Outcome {
        val process = try {
            ProcessBuilder(command + args).apply {
                environment().putAll(env)
                withoutJsig(environment())
            }.start()
        } catch (e: IOException) {
            throw KeyringUnavailable("${command.first()} is not available", e)
        }
        try {
            val stdout = collect(process.inputStream)
            val stderr = collect(process.errorStream)
            runCatching { process.outputStream.use { out -> if (stdin != null) out.write(stdin.toByteArray(Charsets.UTF_8)) } }
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) throw KeyringUnavailable("${command.first()} timed out")
            return Outcome(process.exitValue(), stdout.get(), stderr.get())
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }

    /** Reads [input] on a daemon thread; [get] waits briefly for the rest once the process has ended. */
    private class Collected(private val thread: Thread, private val out: java.io.ByteArrayOutputStream) {
        fun get(): String {
            thread.join(READ_GRACE_MS)
            return synchronized(out) { out.toString(Charsets.UTF_8) }
        }
    }

    private fun collect(input: java.io.InputStream): Collected {
        val out = java.io.ByteArrayOutputStream()
        val thread = Thread({
            runCatching {
                input.use {
                    val buf = ByteArray(4096)
                    while (true) {
                        val n = it.read(buf)
                        if (n < 0) break
                        synchronized(out) { if (out.size() < MAX_OUTPUT) out.write(buf, 0, minOf(n, MAX_OUTPUT - out.size())) }
                    }
                }
            }
        }, "secret-tool-output")
        thread.isDaemon = true
        thread.start()
        return Collected(thread, out)
    }

    companion object {
        const val ID = "secret-service"
        private const val LABEL = "Nijiyome secrets key"
        const val APPLICATION = "app.nijiyome"
        const val PURPOSE = "data-folder-key"
        const val FOLDER_ATTRIBUTE = "data-folder"
        /** The single item of 1.0 to 1.1.1, shared by every data folder. */
        val LEGACY_ATTRIBUTES = listOf("application", APPLICATION, "purpose", "secrets-key")
        private const val MAX_OUTPUT = 64 * 1024

        /**
         * 1.1.2: on Linux the app runs with `LD_PRELOAD=<java.home>/lib/libjsig.so` (SignalChaining). Inherited by `secret-tool`,
         * libjsig prints "OpenJDK VM warning: the use of signal() ..." on its stderr, which made "no such item" (exit 1, empty
         * stderr) look like a keyring failure. The tool runs without libjsig, and such warnings are not taken as errors anyway.
         */
        internal fun withoutJsig(environment: MutableMap<String, String>) {
            val preload = environment["LD_PRELOAD"] ?: return
            val kept = preload.split(' ', ':').filter { it.isNotBlank() && !it.substringAfterLast('/').startsWith("libjsig") }
            if (kept.isEmpty()) environment.remove("LD_PRELOAD") else environment["LD_PRELOAD"] = kept.joinToString(" ")
        }

        internal fun withoutJvmWarnings(stderr: String): String =
            stderr.lineSequence().filterNot { JVM_WARNING.containsMatchIn(it) }.joinToString("\n")

        private val JVM_WARNING = Regex("^(OpenJDK|Java HotSpot\\(TM\\)) .*VM warning:")
        private const val READ_GRACE_MS = 2_000L
    }
}

/**
 * The random id of a data folder, in `<data>/secrets.folder-id` (created on first use). It names the folder's own keyring item
 * ([SecretServiceVault]); a random id rather than a hash of the path, so a moved or renamed folder still finds its key.
 */
object DataFolderId {
    const val FILE = "secrets.folder-id"

    @Synchronized
    fun of(dataDir: File): String {
        val file = File(dataDir, FILE)
        file.takeIf { it.isFile }?.readText()?.trim()?.takeIf { VALID.matches(it) }?.let { return it }
        val id = ByteArray(16).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
        writeAtomically(file, id.toByteArray(Charsets.US_ASCII))
        return id
    }

    private val VALID = Regex("[0-9a-f]{32}")
}

/**
 * Windows: the key sealed with DPAPI for the current user (`CryptProtectData`, the API behind Credential Manager's per-user
 * protection) in `<data>/secrets.key.dpapi`. Only this Windows account can unseal it. Already one key per data folder: the sealed
 * key is a file of the folder, nothing is shared through a system store.
 */
class DpapiVault(private val file: File) : MasterKeyVault {
    override val id: String = ID

    override fun load(): ByteArray? {
        if (!file.isFile) return null
        return try {
            com.sun.jna.platform.win32.Crypt32Util.cryptUnprotectData(file.readBytes())
        } catch (e: Throwable) {
            throw KeyringUnavailable("DPAPI unprotect failed", e)
        }
    }

    override fun store(key: ByteArray) {
        val sealed = try {
            com.sun.jna.platform.win32.Crypt32Util.cryptProtectData(key)
        } catch (e: Throwable) {
            throw KeyringUnavailable("DPAPI protect failed", e)
        }
        writeAtomically(file, sealed)
    }

    override fun delete() {
        file.delete()
    }

    companion object {
        const val ID = "dpapi"
    }
}

/**
 * Last resort when there is no keyring (a desktop without a Secret Service provider, a minimal window manager): the key in
 * `<data>/secrets.key`, readable only by the user (0600 on POSIX). It protects as much as the user's account does, like an
 * unencrypted keyring; Settings says so when this vault is in use.
 */
class FileVault(private val file: File) : MasterKeyVault {
    override val id: String = ID

    override fun load(): ByteArray? = file.takeIf { it.isFile }?.readBytes()?.takeIf { it.size == KEY_BYTES }

    override fun store(key: ByteArray) = writeAtomically(file, key, ownerOnly = true)

    override fun delete() {
        file.delete()
    }

    companion object {
        const val ID = "file"
    }
}

private const val KEY_BYTES = 32

private fun writeAtomically(file: File, bytes: ByteArray, ownerOnly: Boolean = false) {
    file.parentFile?.mkdirs()
    val tmp = File(file.parentFile, file.name + ".tmp")
    tmp.delete()
    if (ownerOnly) restrictToOwner(tmp)
    tmp.writeBytes(bytes)
    if (!tmp.renameTo(file)) {
        file.delete()
        if (!tmp.renameTo(file)) throw IOException("cannot write ${file.name}")
    }
}

/** Creates [file] empty with owner-only permissions (POSIX); on Windows the per-user data folder already is private. */
private fun restrictToOwner(file: File) {
    runCatching {
        Files.createFile(file.toPath(), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
    }.onFailure {
        file.createNewFile()
        file.setReadable(false, false)
        file.setWritable(false, false)
        file.setReadable(true, true)
        file.setWritable(true, true)
    }
}

/**
 * The desktop [SecretStore]: [SealedFileSecretStore] over `<dir>/secrets.bin` with a master key kept in the first [vaults] entry
 * that works (Secret Service or DPAPI, then [FileVault]), one key per data folder. The vault that holds the key is recorded in
 * `<dir>/secrets.backend` and always asked again on later runs; when it cannot be asked (keyring locked, prompt cancelled) reads
 * answer null and writes fail, so nothing already stored is ever overwritten with a new key.
 *
 * 1.1.2: a key is only used after checking that it opens the folder's `secrets.bin`. When no key opens an existing, non-empty file
 * (the keyring was wiped, or before 1.1.2 another data folder replaced the shared keyring item), the file is never read as empty
 * nor overwritten: it is renamed to `secrets.bin.unreadable-<date>`, logged, and [unreadableBackup] tells Settings to ask for the
 * accounts to be linked again; only then a new key starts a new file. The shared pre-1.1.2 keyring item is moved to this folder's own
 * item when it opens this folder's file ([MasterKeyVault.loadLegacy]); the shared item itself is left in place, so another folder
 * that still uses it (a copy of the data folder) or a return to 1.1.1 keeps working.
 */
class DesktopSecretStore(
    private val dir: File,
    private val vaults: List<MasterKeyVault>,
    private val clock: () -> java.time.LocalDateTime = java.time.LocalDateTime::now,
) : SecretStore {
    private val mutex = Mutex()
    private var inner: SealedFileSecretStore? = null
    private val backendFile get() = File(dir, BACKEND_FILE)
    private val secretsFile get() = File(dir, SECRETS_FILE)
    private val noticeFile get() = File(dir, UNREADABLE_NOTICE_FILE)

    /** The vault in use after the first access ("secret-service", "dpapi" or "file"); null before, or when none could be used. */
    @Volatile
    var backend: String? = null
        private set

    /**
     * The name of the `secrets.bin.unreadable-<date>` file set aside because no key opened it, until the user dismisses the notice
     * ([dismissUnreadable]); null when nothing was set aside. Kept in `secrets.unreadable` so the notice survives a restart.
     */
    val unreadableBackup: String?
        get() = noticeFile.takeIf { it.isFile }?.let { f -> runCatching { f.readText().trim() }.getOrNull()?.ifEmpty { null } }

    /** Settings › Datos: "OK" on the notice. The set-aside file stays where it is. */
    fun dismissUnreadable() {
        noticeFile.delete()
    }

    /** Opens the store now (asks the vault, checks the file) so [backend] and [unreadableBackup] are known; true when it is usable. */
    suspend fun prepare(): Boolean = store() != null

    override suspend fun get(key: String): String? = store()?.get(key)

    override suspend fun put(key: String, value: String?) {
        val store = store() ?: throw KeyringUnavailable("the keyring is not available; the secret was not saved")
        store.put(key, value)
    }

    override suspend fun clear() = mutex.withLock {
        withContext(Dispatchers.IO) {
            (inner ?: SealedFileSecretStore(dir, AesGcmKeyCipher { error("no key") })).clear()
            vaults.forEach { it.delete() }
            backendFile.delete()
        }
        inner = null
        backend = null
    }

    private suspend fun store(): SealedFileSecretStore? = mutex.withLock {
        inner?.let { return@withLock it }
        val key = withContext(Dispatchers.IO) {
            try {
                resolveKey()
            } catch (e: KeyringUnavailable) {
                AppLog.w(TAG, "secret store unavailable: ${e.message}")
                null
            }
        } ?: return@withLock null
        SealedFileSecretStore(dir, cipherOf(key)).also { inner = it }
    }

    private fun resolveKey(): ByteArray {
        val recorded = backendFile.takeIf { it.isFile }?.readText()?.trim()
        val vault = recorded?.let { id -> vaults.firstOrNull { it.id == id } }
        if (vault != null) {
            vault.load()?.let { key ->
                backend = vault.id
                if (!opensSecrets(key)) setAsideUnreadable("the key in ${vault.id} does not open secrets.bin")
                return key
            }
            migrateLegacy(vault)?.let { return it }
            AppLog.w(TAG, "the ${vault.id} vault has no key for this data folder")
        } else {
            // A new folder, or one whose secrets.backend is gone: this folder's own key, or a pre-1.1.2 shared key, that opens
            // this folder's file is taken over.
            var unavailable = false
            for (candidate in vaults) {
                val found = try {
                    candidate.load()?.takeIf { hasSecrets() && opensSecrets(it) }?.also { recordBackend(candidate) }
                        ?: migrateLegacy(candidate)
                } catch (e: KeyringUnavailable) {
                    AppLog.i(TAG, "${candidate.id} vault not usable: ${e.message}")
                    unavailable = true
                    null
                }
                if (found != null) return found
            }
            // A vault that could not be asked may hold the key of the existing file: do not set it aside on a guess.
            if (unavailable && hasSecrets()) throw KeyringUnavailable("the keyring that may hold the key of secrets.bin cannot be asked")
        }
        if (hasSecrets()) setAsideUnreadable("no key for this data folder opens secrets.bin")
        val key = ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes)
        for (candidate in vaults) {
            try {
                candidate.store(key)
                recordBackend(candidate)
                AppLog.i(TAG, "secrets key kept in ${candidate.id}")
                return key
            } catch (e: KeyringUnavailable) {
                AppLog.i(TAG, "${candidate.id} vault not usable: ${e.message}")
            }
        }
        throw KeyringUnavailable("no vault could keep the secrets key")
    }

    /**
     * The shared pre-1.1.2 key of [vault], copied to this folder's own item when it opens this folder's `secrets.bin` (a key that
     * opens nothing here belongs to another folder and is left alone). The shared item is never removed: another data folder (a
     * copy made before 1.1.2) may still need it, and 1.1.1 reads only that one.
     */
    private fun migrateLegacy(vault: MasterKeyVault): ByteArray? {
        if (!hasSecrets()) return null
        val legacy = vault.loadLegacy() ?: return null
        if (!opensSecrets(legacy)) {
            AppLog.w(TAG, "the shared ${vault.id} key of an older version does not open this folder's secrets.bin; not used")
            return null
        }
        vault.store(legacy)
        val readBack = vault.load()
        if (readBack == null || !readBack.contentEquals(legacy)) {
            // The copy did not land: keep using the shared item this run and try again on the next one.
            AppLog.w(TAG, "the ${vault.id} key could not be copied to this folder's own item; the shared one stays")
            backend = vault.id
            return legacy
        }
        recordBackend(vault)
        AppLog.i(TAG, "secrets key copied to this data folder's own ${vault.id} item")
        return legacy
    }

    private fun recordBackend(vault: MasterKeyVault) {
        dir.mkdirs()
        backendFile.writeText(vault.id)
        backend = vault.id
    }

    private fun hasSecrets(): Boolean = secretsFile.isFile && secretsFile.length() > 0

    /** True when there is nothing to open, or [key] opens `secrets.bin` (authenticates and holds the JSON map). */
    private fun opensSecrets(key: ByteArray): Boolean {
        if (!hasSecrets()) return true
        val plain = runCatching { cipherOf(key).open(secretsFile.readBytes()) }.getOrNull() ?: return false
        return runCatching { kotlinx.serialization.json.Json.parseToJsonElement(plain.decodeToString()) is kotlinx.serialization.json.JsonObject }
            .getOrDefault(false)
    }

    /** Moves the unreadable `secrets.bin` aside (never deleted, never overwritten) and records the notice. */
    private fun setAsideUnreadable(reason: String) {
        if (!hasSecrets()) return
        val stamp = clock().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        var target = File(dir, "$SECRETS_FILE.unreadable-$stamp")
        var n = 2
        while (target.exists()) target = File(dir, "$SECRETS_FILE.unreadable-$stamp-${n++}")
        if (!secretsFile.renameTo(target)) {
            // Without setting it aside, a new key would write over it: refuse instead.
            throw KeyringUnavailable("secrets.bin cannot be opened nor set aside")
        }
        runCatching { noticeFile.writeText(target.name) }
        AppLog.w(TAG, "$reason; set aside as ${target.name}, accounts must be linked again")
    }

    private fun cipherOf(key: ByteArray): AesGcmKeyCipher {
        val spec = SecretKeySpec(key, "AES")
        return AesGcmKeyCipher { spec }
    }

    companion object {
        private const val TAG = "secrets"
        const val BACKEND_FILE = "secrets.backend"
        const val SECRETS_FILE = "secrets.bin"
        const val UNREADABLE_NOTICE_FILE = "secrets.unreadable"

        /**
         * The vaults of this platform, best first. `NIJIYOME_SECRETS=file` keeps the key in the data directory only (tests and
         * development runs that must never reach a keyring).
         */
        fun defaultVaults(dataDir: File, windows: Boolean, env: (String) -> String? = System::getenv): List<MasterKeyVault> =
            if (env("NIJIYOME_SECRETS") == "file") {
                listOf(FileVault(File(dataDir, "secrets.key")))
            } else if (windows) {
                listOf(DpapiVault(File(dataDir, "secrets.key.dpapi")), FileVault(File(dataDir, "secrets.key")))
            } else {
                listOf(SecretServiceVault(folderId = { DataFolderId.of(dataDir) }), FileVault(File(dataDir, "secrets.key")))
            }
    }
}
