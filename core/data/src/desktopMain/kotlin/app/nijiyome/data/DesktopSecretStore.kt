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
}

/** The keyring is missing, locked or refused (no Secret Service on the bus, the user cancelled the unlock prompt...). */
class KeyringUnavailable(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Linux: the freedesktop Secret Service (GNOME Keyring, KWallet, KeePassXC...) on the session D-Bus, through `secret-tool`, the
 * command-line client of libsecret. One item with the attributes `application=app.nijiyome` and `purpose=secrets-key`; the value is
 * the key in base64, written on the tool's standard input (never on a command line). [command] and [env] are injectable for tests
 * (a private D-Bus session with its own keyring daemon).
 */
class SecretServiceVault(
    /** The program and any leading arguments (`secret-tool`; tests run a stand-in that never answers). */
    private val command: List<String> = listOf("secret-tool"),
    private val env: Map<String, String> = emptyMap(),
    private val timeoutSeconds: Long = 60,
) : MasterKeyVault {
    override val id: String = ID

    override fun load(): ByteArray? {
        val result = run(listOf("lookup") + ATTRIBUTES)
        // secret-tool answers 1 and prints nothing for "no such item"; anything on stderr is a real failure (no service, locked).
        if (result.exit == 0) return decode(result.stdout)
        if (result.stderr.isBlank()) return null
        throw KeyringUnavailable("secret-tool lookup: ${result.stderr.trim().take(200)}")
    }

    override fun store(key: ByteArray) {
        val result = run(listOf("store", "--label=$LABEL") + ATTRIBUTES, stdin = Base64.getEncoder().encodeToString(key))
        if (result.exit != 0) throw KeyringUnavailable("secret-tool store: ${result.stderr.trim().take(200)}")
    }

    override fun delete() {
        runCatching { run(listOf("clear") + ATTRIBUTES) }
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
            ProcessBuilder(command + args).apply { environment().putAll(env) }.start()
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
        private val ATTRIBUTES = listOf("application", "app.nijiyome", "purpose", "secrets-key")
        private const val MAX_OUTPUT = 64 * 1024
        private const val READ_GRACE_MS = 2_000L
    }
}

/**
 * Windows: the key sealed with DPAPI for the current user (`CryptProtectData`, the API behind Credential Manager's per-user
 * protection) in `<data>/secrets.key.dpapi`. Only this Windows account can unseal it.
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
 * that works (Secret Service or DPAPI, then [FileVault]). The vault that holds the key is recorded in `<dir>/secrets.backend` and
 * always asked again on later runs; when it cannot be asked (keyring locked, prompt cancelled) reads answer null and writes fail,
 * so nothing already stored is ever overwritten with a new key. A recorded vault that no longer has the key (keyring wiped) starts
 * a new key: the old file cannot be opened any more and reads as empty, like Android after a restore to another device.
 */
class DesktopSecretStore(
    private val dir: File,
    private val vaults: List<MasterKeyVault>,
) : SecretStore {
    private val mutex = Mutex()
    private var inner: SealedFileSecretStore? = null
    private val backendFile get() = File(dir, BACKEND_FILE)

    /** The vault in use after the first access ("secret-service", "dpapi" or "file"); null before, or when none could be used. */
    @Volatile
    var backend: String? = null
        private set

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
        val spec = SecretKeySpec(key, "AES")
        SealedFileSecretStore(dir, AesGcmKeyCipher { spec }).also { inner = it }
    }

    private fun resolveKey(): ByteArray {
        val recorded = backendFile.takeIf { it.isFile }?.readText()?.trim()
        val vault = recorded?.let { id -> vaults.firstOrNull { it.id == id } }
        if (vault != null) {
            vault.load()?.let { backend = vault.id; return it }
            AppLog.w(TAG, "the ${vault.id} vault has no key any more; starting a new one")
        }
        val key = ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes)
        for (candidate in vaults) {
            try {
                candidate.store(key)
                dir.mkdirs()
                backendFile.writeText(candidate.id)
                backend = candidate.id
                AppLog.i(TAG, "secrets key kept in ${candidate.id}")
                return key
            } catch (e: KeyringUnavailable) {
                AppLog.i(TAG, "${candidate.id} vault not usable: ${e.message}")
            }
        }
        throw KeyringUnavailable("no vault could keep the secrets key")
    }

    companion object {
        private const val TAG = "secrets"
        const val BACKEND_FILE = "secrets.backend"

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
                listOf(SecretServiceVault(), FileVault(File(dataDir, "secrets.key")))
            }
    }
}
