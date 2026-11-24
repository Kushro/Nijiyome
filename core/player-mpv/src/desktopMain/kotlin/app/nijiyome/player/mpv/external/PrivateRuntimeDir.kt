// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv.external

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom

// Hito 4 (review): where the external mpv's IPC socket and the lsfg-vk config live. Both must be out of other local users' reach (a
// replaced `lsfg-vk.toml` would load another DLL into mpv; a pre-created socket would receive mpv's commands), so they go in a folder
// only this user can open: `$XDG_RUNTIME_DIR/nijiyome` (that runtime dir is already private), else a fresh `nijiyome-<random>` folder
// in the temp dir created with mode 0700, never a fixed name in a shared folder.

internal object PrivateRuntimeDir {
    private val OWNER_ONLY: Set<PosixFilePermission> = PosixFilePermissions.fromString("rwx------")

    /** This process's folder (made once). */
    val default: File by lazy { resolve(System.getenv(), File(System.getProperty("java.io.tmpdir"))) }

    /** [env]'s `XDG_RUNTIME_DIR` / `nijiyome` when it is safe to use, else a new private folder under [tmp]. */
    fun resolve(env: Map<String, String>, tmp: File): File {
        env["XDG_RUNTIME_DIR"]?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isDirectory }?.let { base ->
            ownerOnlyDir(File(base, "nijiyome").toPath())?.let { return it }
        }
        return newPrivateTempDir(tmp)
    }

    /** [path] as a private folder of this user (created or tightened to 0700), or null if it is a link or someone else's. */
    private fun ownerOnlyDir(path: Path): File? = runCatching {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            if (posix(path.parent)) Files.createDirectory(path, PosixFilePermissions.asFileAttribute(OWNER_ONLY)) else Files.createDirectory(path)
        }
        if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) return null
        if (posix(path)) {
            val me = System.getProperty("user.name")
            if (Files.getOwner(path, LinkOption.NOFOLLOW_LINKS).name != me) return null
            Files.setPosixFilePermissions(path, OWNER_ONLY)
        }
        path.toFile()
    }.getOrNull()

    /** A new `nijiyome-<random>` folder under [tmp], created atomically with mode 0700 where the file system has POSIX modes. */
    fun newPrivateTempDir(tmp: File): File {
        val dir = tmp.toPath()
        return if (posix(dir)) {
            Files.createTempDirectory(dir, "nijiyome-", PosixFilePermissions.asFileAttribute(OWNER_ONLY)).toFile()
        } else {
            Files.createTempDirectory(dir, "nijiyome-").toFile()
        }.also { it.deleteOnExit() }
    }

    private fun posix(p: Path): Boolean = runCatching { p.fileSystem.supportedFileAttributeViews().contains("posix") }.getOrDefault(false)

    private val random = SecureRandom()

    /** A random token for names that must not be guessed. */
    fun token(): String = ByteArray(8).also(random::nextBytes).joinToString("") { "%02x".format(it) }
}
