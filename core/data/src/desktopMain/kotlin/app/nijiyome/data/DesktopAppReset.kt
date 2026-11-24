// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.room.execSQL
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import app.nijiyome.database.NijiyomeDatabase
import app.nijiyome.model.AppDataWiper
import app.nijiyome.model.AppError
import app.nijiyome.model.AppException
import app.nijiyome.model.AppLog
import app.nijiyome.model.ExtensionRepository
import app.nijiyome.model.LinkRepository
import app.nijiyome.model.LogRepository
import app.nijiyome.model.MetadataRepository
import app.nijiyome.model.SecretStore
import app.nijiyome.model.SelectiveAppReset
import app.nijiyome.model.torrent.TorrentRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 * "Restablecer la app" on desktop: the same steps as Android's `AppResetImpl`, in the same order and best effort (the first failure
 * is reported after all ran): extensions, Link pairing, secrets (which also removes the key from the system keyring), metadata cache,
 * the database tables, the app log, every module's [AppDataWiper] (downloads, followed shows, reader progress... and, bound by the
 * desktop app, the autostart entry), the cache directory, then whatever else is left in the data directory (the desktop has no
 * per-app folder the system clears; Tatakai Next's `reset.rs` did this sweep), and the settings last, so `onboardingDone` reads
 * false only when the rest is gone. The window then restarts and shows the first-run wizard.
 *
 * Kept: the log directory (it holds no personal data and records the reset), the open database and settings files (emptied above,
 * not deleted while open) and [keep] (the instance lock and socket). What cannot be deleted now (Windows keeps open files locked)
 * is listed in `reset.pending` and removed by [finishPending] on the next start, before anything opens it.
 *
 * With `keepTorrents = false` ("Conservar los torrents descargados" off, Tatakai Next's `reset_app(keep_downloads)`), every torrent of
 * [torrents] is removed with its files first: that reaches download folders outside the data directory without touching anything
 * else there.
 */
class DesktopAppReset(
    private val dataDir: File,
    private val cacheDir: File,
    private val settingsStore: DataStore<Preferences>,
    private val database: NijiyomeDatabase,
    private val secrets: SecretStore,
    private val metadata: MetadataRepository? = null,
    private val extensions: ExtensionRepository? = null,
    private val link: LinkRepository? = null,
    private val logs: LogRepository? = null,
    private val wipers: List<AppDataWiper> = emptyList(),
    /**
     * Top-level names in [dataDir] that stay: the database and settings files, the lock, the instance socket, logs, plus the
     * folders holding the user's downloads when they live inside the data directory (no Downloads folder on that system).
     */
    private val keep: Set<String> = DEFAULT_KEEP,
    /** The torrent engine, asked only when the torrents go too (getting it may start it). */
    private val torrents: () -> TorrentRepository? = { null },
) : SelectiveAppReset {
    override suspend fun resetAll(keepTorrents: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
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
        if (!keepTorrents) {
            torrents()?.let { repo -> repo.library.value.forEach { item -> step { repo.remove(item.id, deleteFiles = true) } } }
        }
        step { extensions?.let { repo -> repo.installed.value.forEach { ext -> repo.remove(ext.id) } } }
        step { link?.forget() }
        step { secrets.clear() }
        step { metadata?.clearCache() }
        step { clearTables(database) }
        step { logs?.clear() }
        wipers.forEach { wiper -> step { wiper.wipe() } }
        step { sweep(cacheDir, emptySet()) }
        step { sweep(dataDir, keep + cacheDir.name) }
        step { settingsStore.edit { it.clear() } }
        AppLog.i("reset", "app reset to first run (torrents ${if (keepTorrents) "kept" else "removed with their files"})")
        failure?.let { Result.failure(AppException(AppError.Unknown("reset:${it::class.java.simpleName}"), it)) } ?: Result.success(Unit)
    }

    companion object {
        /**
         * Empties every table of the database (Room's `clearAllTables` is Android-only): one write transaction of `DELETE FROM`
         * over the user tables, leaving Room's own metadata table.
         */
        suspend fun clearTables(database: NijiyomeDatabase) {
            database.useWriterConnection { transactor ->
                val tables = transactor.usePrepared(
                    "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' " +
                        "AND name NOT IN ('room_master_table', 'android_metadata')",
                ) { st -> buildList { while (st.step()) add(st.getText(0)) } }
                transactor.immediateTransaction { tables.forEach { execSQL("DELETE FROM `${it.replace("`", "``")}`") } }
            }
        }

        const val PENDING_FILE = "reset.pending"
        val DEFAULT_KEEP = setOf(
            "logs", "nijiyome.db", "nijiyome.db-wal", "nijiyome.db-shm", "nijiyome.db-journal",
            "settings.preferences_pb", ".lock", "instance.sock", PENDING_FILE,
        )
        private val json = Json
        private val NAMES = ListSerializer(String.serializer())

        /**
         * Deletes the entries of [dir] except [keep]; what cannot be deleted now is added to `<dir>/reset.pending`. A missing [dir]
         * is not an error. Symbolic links are removed, never followed.
         */
        fun sweep(dir: File, keep: Set<String>) {
            val entries = dir.listFiles() ?: return
            val failed = mutableListOf<String>()
            for (entry in entries) {
                if (entry.name in keep || entry.name == PENDING_FILE) continue
                if (!removeAny(entry)) failed += entry.name
            }
            if (failed.isNotEmpty()) {
                val pending = File(dir, PENDING_FILE)
                val before = runCatching { json.decodeFromString(NAMES, pending.readText()) }.getOrDefault(emptyList())
                pending.writeText(json.encodeToString(NAMES, (before + failed).distinct()))
            }
        }

        /** Finishes a reset whose files were locked: removes what `reset.pending` lists (plain names inside [dir] only), then it. */
        fun finishPending(dir: File) {
            val pending = File(dir, PENDING_FILE)
            if (!pending.isFile) return
            val names = runCatching { json.decodeFromString(NAMES, pending.readText()) }.getOrDefault(emptyList())
            for (name in names) {
                if (name.isEmpty() || name == "." || name == ".." || name.contains('/') || name.contains('\\') || name == "logs") continue
                removeAny(File(dir, name))
            }
            pending.delete()
        }

        /** Deletes a file, a link (never what it points to) or a directory tree without following links inside it. */
        internal fun removeAny(file: File): Boolean {
            val root = file.toPath()
            if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return true
            return runCatching {
                Files.walkFileTree(
                    root,
                    object : SimpleFileVisitor<Path>() {
                        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                            Files.deleteIfExists(file)
                            return FileVisitResult.CONTINUE
                        }

                        override fun postVisitDirectory(dir: Path, exc: java.io.IOException?): FileVisitResult {
                            Files.deleteIfExists(dir)
                            return FileVisitResult.CONTINUE
                        }
                    },
                )
            }.isSuccess && !Files.exists(root, LinkOption.NOFOLLOW_LINKS)
        }
    }
}
