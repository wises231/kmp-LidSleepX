package com.wyz.covio.app.platform

import com.wyz.covio.core.APP_ID
import com.wyz.covio.core.AppLogger
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant

class FileAppLogger(
    val directory: Path = Path.of(System.getProperty("user.home"), "Library", "Application Support", APP_ID, "logs"),
) : AppLogger {
    private val lock = Any()
    private val file = directory.resolve("Covio.log")

    init {
        Files.createDirectories(directory)
    }

    override fun info(message: String) = write("INFO", message, null)
    override fun warn(message: String, error: Throwable?) = write("WARN", message, error)
    override fun error(message: String, error: Throwable?) = write("ERROR", message, error)
    override fun logFile(): String = file.toString()

    private fun write(level: String, message: String, error: Throwable?) {
        synchronized(lock) {
            rotateIfNeeded()
            val details = buildString {
                append(Instant.now()).append(' ')
                append(level).append(' ')
                append(message)
                if (error != null) append(": ").append(error.stackTraceToString())
                appendLine()
            }
            Files.writeString(
                file,
                details,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
            )
        }
    }

    private fun rotateIfNeeded() {
        if (!Files.exists(file) || Files.size(file) < MAX_BYTES) return
        val oldest = file.resolveSibling(file.fileName.toString() + ".3")
        Files.deleteIfExists(oldest)
        for (index in 2 downTo 1) {
            val source = file.resolveSibling(file.fileName.toString() + ".$index")
            val target = file.resolveSibling(file.fileName.toString() + ".${index + 1}")
            if (Files.exists(source)) Files.move(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
        Files.move(file, file.resolveSibling(file.fileName.toString() + ".1"), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }

    companion object {
        private const val MAX_BYTES = 5L * 1024L * 1024L
    }
}
