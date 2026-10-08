package com.wyz.macisland.app.platform

import com.wyz.macisland.core.PmsetWakeLogParser
import com.wyz.macisland.core.WakeLogReader
import com.wyz.macisland.core.WakeLogSnapshot
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Instant
import java.util.concurrent.TimeUnit

class PmsetWakeLogReader(
    private val command: List<String> = listOf("/usr/bin/pmset", "-g", "log"),
    private val timeoutMillis: Long = 20_000L,
    private val nowEpochSeconds: () -> Long = { Instant.now().epochSecond },
    private val commandRunner: (List<String>, Long) -> String? = ::runPmsetCommand,
) : WakeLogReader {
    override fun read(): WakeLogSnapshot? {
        val text = commandRunner(command, timeoutMillis) ?: return null
        return runCatching {
            PmsetWakeLogParser.parse(text, nowEpochSeconds())
        }.getOrNull()
    }
}

internal fun runPmsetCommand(command: List<String>, timeoutMillis: Long): String? {
    if (command.isEmpty() || timeoutMillis <= 0L) return null
    val outputPath = runCatching { Files.createTempFile("macisland-pmset", ".log") }.getOrNull() ?: return null
    return try {
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .redirectOutput(outputPath.toFile())
            .start()
        val finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroyForcibly()
            process.waitFor(2L, TimeUnit.SECONDS)
            null
        } else if (process.exitValue() != 0) {
            null
        } else {
            Files.readString(outputPath, StandardCharsets.UTF_8)
        }
    } catch (_: Exception) {
        null
    } finally {
        runCatching { Files.deleteIfExists(outputPath) }
    }
}
