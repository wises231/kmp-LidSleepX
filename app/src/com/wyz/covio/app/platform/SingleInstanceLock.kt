package com.wyz.covio.app.platform

import com.wyz.covio.core.APP_ID
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Keeps one Covio instance per user.
 *
 * The app starts a second instance when the LaunchAgent bootstraps while an
 * instance already runs. The second instance must stop immediately.
 * Therefore the second instance cannot take the file lock.
 *
 * The operating system releases the lock when the process exits.
 * A crash also releases the lock.
 */
class SingleInstanceLock(
    private val lockPath: Path = defaultLockPath(),
) : AutoCloseable {
    private var channel: FileChannel? = null
    private var lock: FileLock? = null

    /** Returns true when this process owns the lock. */
    fun acquire(): Boolean {
        if (lock?.isValid == true) return true
        return runCatching {
            Files.createDirectories(lockPath.parent)
            val opened = FileChannel.open(
                lockPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
            )
            val acquired = opened.tryLock()
            if (acquired == null) {
                opened.close()
                false
            } else {
                channel = opened
                lock = acquired
                true
            }
        }.getOrDefault(false)
    }

    override fun close() {
        runCatching { lock?.release() }
        runCatching { channel?.close() }
        lock = null
        channel = null
    }

    companion object {
        fun defaultLockPath(): Path {
            val home = System.getProperty("user.home")
            return Path.of(home, "Library", "Application Support", APP_ID, "app.lock")
        }
    }
}
