package com.wyz.macisland.app.platform

import com.wyz.macisland.core.ScreenshotInboxSnapshot
import com.wyz.macisland.core.ShelfStoreState
import java.nio.file.Files
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.TimeUnit

interface ScreenshotPreferenceStore {
    fun read(): ScreenshotInboxSnapshot
    fun apply(directory: Path): Boolean
    fun restore(snapshot: ScreenshotInboxSnapshot): Boolean
}

class ScreenshotTakeoverController(
    private val store: ScreenshotPreferenceStore,
    private val directory: Path,
    private val loadState: () -> ShelfStoreState,
    private val saveState: (ShelfStoreState) -> ShelfStoreState,
) {
    var active: Boolean = false
        private set

    fun enable(): Boolean {
        runCatching { Files.createDirectories(directory) }
        val state = loadState()
        if (state.preferenceSnapshot == null) {
            saveState(state.copy(preferenceSnapshot = store.read()))
        }
        if (!store.apply(directory)) return false
        active = true
        return true
    }

    fun disable(): Boolean = restore()

    fun restoreOnExit(): Boolean = restore()

    private fun restore(): Boolean {
        val snapshot = loadState().preferenceSnapshot
        if (snapshot == null) {
            active = false
            return true
        }
        if (!store.restore(snapshot)) return false
        saveState(loadState().copy(preferenceSnapshot = null))
        active = false
        return true
    }
}

data class PreferenceCommandResult(val exitCode: Int, val output: String)

class MacScreenshotPreferenceStore(
    private val runner: (List<String>) -> PreferenceCommandResult = ::runDefaultsCommand,
    private val domain: String = "com.apple.screencapture",
) : ScreenshotPreferenceStore {
    override fun read(): ScreenshotInboxSnapshot = ScreenshotInboxSnapshot(
        location = readString("location"),
        locationScreenshot = readString("location-screenshot"),
        showThumbnail = readBoolean("show-thumbnail"),
    )

    override fun apply(directory: Path): Boolean {
        val path = directory.toAbsolutePath().normalize().toString()
        return writeString("location", path) &&
            writeString("location-screenshot", path) &&
            writeBoolean("show-thumbnail", false)
    }

    override fun restore(snapshot: ScreenshotInboxSnapshot): Boolean {
        var ok = true
        ok = restoreValue("location", snapshot.location, stringValue = true) && ok
        ok = restoreValue("location-screenshot", snapshot.locationScreenshot, stringValue = true) && ok
        ok = restoreValue("show-thumbnail", snapshot.showThumbnail?.let { if (it) "1" else "0" }, stringValue = false) && ok
        return ok
    }

    internal fun readString(key: String): String? {
        val result = runner(listOf("/usr/bin/defaults", "read", domain, key))
        if (result.exitCode != 0) return null
        return result.output.trim().removeSurrounding("\"").takeIf { it.isNotEmpty() }
    }

    internal fun readBoolean(key: String): Boolean? {
        val result = runner(listOf("/usr/bin/defaults", "read", domain, key))
        if (result.exitCode != 0) return null
        return when (result.output.trim().lowercase()) {
            "1", "true", "yes" -> true
            "0", "false", "no" -> false
            else -> null
        }
    }

    private fun writeString(key: String, value: String): Boolean =
        runner(listOf("/usr/bin/defaults", "write", domain, key, "-string", value)).exitCode == 0

    private fun writeBoolean(key: String, value: Boolean): Boolean =
        runner(listOf("/usr/bin/defaults", "write", domain, key, "-bool", if (value) "true" else "false")).exitCode == 0

    private fun restoreValue(key: String, value: String?, stringValue: Boolean): Boolean {
        if (value != null) {
            return runner(
                listOf(
                    "/usr/bin/defaults",
                    "write",
                    domain,
                    key,
                    if (stringValue) "-string" else "-bool",
                    if (!stringValue && value == "1") "true" else if (!stringValue) "false" else value,
                ),
            ).exitCode == 0
        }
        val result = runner(listOf("/usr/bin/defaults", "delete", domain, key))
        return result.exitCode == 0 || result.output.contains("does not exist", ignoreCase = true)
    }
}

private fun runDefaultsCommand(command: List<String>): PreferenceCommandResult = runCatching {
    val process = ProcessBuilder(command).redirectErrorStream(true).start()
    if (!process.waitFor(5L, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        return@runCatching PreferenceCommandResult(-1, "")
    }
    val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).readText()
    PreferenceCommandResult(process.exitValue(), output)
}.getOrDefault(PreferenceCommandResult(-1, ""))
