// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.desktop

import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock

/**
 * One running app per data directory: the HTTP cache and the database are not meant to be shared by two processes.
 * The lock is a file lock on `<data>/.lock`, released by the system if the process dies.
 */
class InstanceLock private constructor(private val file: RandomAccessFile, private val lock: FileLock) {
    fun release() {
        runCatching { lock.release() }
        runCatching { file.close() }
    }

    companion object {
        /** null when another process holds the lock (or the lock file cannot be opened). */
        fun acquire(dataDir: File): InstanceLock? {
            val raf = runCatching { RandomAccessFile(File(dataDir, ".lock"), "rw") }.getOrNull() ?: return null
            val lock = runCatching { raf.channel.tryLock() }.getOrNull()
            if (lock == null) {
                runCatching { raf.close() }
                return null
            }
            return InstanceLock(raf, lock)
        }
    }
}
