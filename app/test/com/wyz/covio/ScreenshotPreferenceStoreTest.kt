package com.wyz.covio.app

import com.wyz.covio.app.platform.MacScreenshotPreferenceStore
import com.wyz.covio.app.platform.PreferenceCommandResult
import com.wyz.covio.core.ScreenshotInboxSnapshot
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScreenshotPreferenceStoreTest {
    @Test
    fun `apply writes screenshot location and hides the thumbnail`() {
        val commands = mutableListOf<List<String>>()
        val store = MacScreenshotPreferenceStore(runner = { command ->
            commands += command
            PreferenceCommandResult(0, "")
        })

        assertTrue(store.apply(Path.of("/tmp/Covio Screenshots")))

        assertEquals(
            listOf(
                listOf("/usr/bin/defaults", "write", "com.apple.screencapture", "location", "-string", "/tmp/Covio Screenshots"),
                listOf("/usr/bin/defaults", "write", "com.apple.screencapture", "location-screenshot", "-string", "/tmp/Covio Screenshots"),
                listOf("/usr/bin/defaults", "write", "com.apple.screencapture", "show-thumbnail", "-bool", "false"),
            ),
            commands,
        )
    }

    @Test
    fun `restore writes the old values and deletes missing keys`() {
        val commands = mutableListOf<List<String>>()
        val store = MacScreenshotPreferenceStore(runner = { command ->
            commands += command
            PreferenceCommandResult(0, "")
        })

        assertTrue(
            store.restore(
                ScreenshotInboxSnapshot(
                    location = "/Users/example/Desktop",
                    locationScreenshot = null,
                    showThumbnail = true,
                ),
            ),
        )

        assertEquals(
            listOf(
                listOf("/usr/bin/defaults", "write", "com.apple.screencapture", "location", "-string", "/Users/example/Desktop"),
                listOf("/usr/bin/defaults", "delete", "com.apple.screencapture", "location-screenshot"),
                listOf("/usr/bin/defaults", "write", "com.apple.screencapture", "show-thumbnail", "-bool", "true"),
            ),
            commands,
        )
    }

    @Test
    fun `read parses strings and boolean values`() {
        val values = mapOf(
            "location" to "/tmp/screens",
            "location-screenshot" to "~/Desktop",
            "show-thumbnail" to "1",
        )
        val store = MacScreenshotPreferenceStore(runner = { command ->
            val key = command.last()
            PreferenceCommandResult(0, values[key] ?: "")
        })

        assertEquals(
            ScreenshotInboxSnapshot("/tmp/screens", "~/Desktop", true),
            store.read(),
        )
    }

    @Test
    fun `missing preferences return an empty snapshot`() {
        val store = MacScreenshotPreferenceStore(runner = { PreferenceCommandResult(1, "does not exist") })

        assertEquals(ScreenshotInboxSnapshot(), store.read())
    }

    @Test
    fun `restore handles false true and missing thumbnail values`() {
        val commands = mutableListOf<List<String>>()
        val store = MacScreenshotPreferenceStore(runner = { command ->
            commands += command
            PreferenceCommandResult(0, "")
        })

        assertTrue(store.restore(ScreenshotInboxSnapshot(showThumbnail = false)))
        assertTrue(store.restore(ScreenshotInboxSnapshot(showThumbnail = true)))
        assertTrue(store.restore(ScreenshotInboxSnapshot(showThumbnail = null)))

        assertEquals(9, commands.size)
        assertEquals(listOf("false", "true", "show-thumbnail"), commands.filter { it.contains("show-thumbnail") }.map { it.last() })
    }
}
