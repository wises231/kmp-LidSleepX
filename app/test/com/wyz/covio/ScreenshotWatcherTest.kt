package com.wyz.covio.app

import com.wyz.covio.app.platform.ScreenshotWatcher
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScreenshotWatcherTest {
    @Test
    fun `candidate accepts supported image extensions`() {
        val directory = Files.createTempDirectory("covio-watcher")
        val watcher = ScreenshotWatcher(
            directory = directory,
            dedicatedDirectory = directory,
            requireScreenCaptureAttribute = false,
            onNew = {},
        )

        assertTrue(watcher.isCandidate(directory.resolve("capture.PNG")))
        assertTrue(watcher.isCandidate(directory.resolve("capture.webp")))
        assertFalse(watcher.isCandidate(directory.resolve("notes.txt")))
    }

    @Test
    fun `desktop candidates require the screen capture attribute`() {
        val directory = Files.createTempDirectory("covio-watcher")
        val dedicated = directory.resolve("dedicated")
        Files.createDirectories(dedicated)
        val watcher = ScreenshotWatcher(
            directory = directory,
            dedicatedDirectory = dedicated,
            requireScreenCaptureAttribute = true,
            onNew = {},
            attributeReader = { path -> path.fileName.toString().startsWith("tagged") },
        )

        assertTrue(watcher.isCandidate(directory.resolve("tagged.png")))
        assertFalse(watcher.isCandidate(directory.resolve("untagged.png")))
        assertTrue(watcher.isCandidate(dedicated.resolve("untagged.png")))
    }

    @Test
    fun `file events wait 200 milliseconds for writes to finish`() {
        val directory = Files.createTempDirectory("covio-watcher")
        var sleptMillis = 0L
        val watcher = ScreenshotWatcher(
            directory = directory,
            dedicatedDirectory = directory,
            requireScreenCaptureAttribute = false,
            onNew = {},
            debounceSleeper = { sleptMillis = it },
        )

        watcher.awaitDebounce()

        assertEquals(ScreenshotWatcher.DEFAULT_DEBOUNCE_MILLIS, sleptMillis)
        assertEquals(200L, ScreenshotWatcher.DEFAULT_DEBOUNCE_MILLIS)
    }
}
