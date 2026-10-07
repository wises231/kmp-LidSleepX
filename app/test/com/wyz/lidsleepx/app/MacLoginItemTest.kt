package com.wyz.lidsleepx.app

import com.wyz.lidsleepx.app.platform.MacLoginItem
import com.wyz.lidsleepx.core.APP_ID
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MacLoginItemTest {
    @Test
    fun `plist declares the app label and packaged program`() {
        val item = MacLoginItem()
        val text = item.plistTextFor("/Applications/LidSleepX.app", emptyList())
        assertTrue(text.contains("<string>com.wyz.lidsleepx</string>"))
        assertTrue(text.contains("<string>/Applications/LidSleepX.app</string>"))
        assertTrue(text.contains("<key>RunAtLoad</key>"))
        assertTrue(text.contains("<key>KeepAlive</key>"))
        assertTrue(text.contains("<key>SuccessfulExit</key>"))
        assertTrue(text.contains("<key>ProcessType</key>"))
    }

    @Test
    fun `plist keeps helper arguments in order`() {
        val item = MacLoginItem()
        val text = item.plistTextFor("/usr/bin/java", listOf("-jar", "/tmp/app.jar"))
        assertTrue(text.contains("<string>/usr/bin/java</string>"))
        assertTrue(text.contains("<string>-jar</string>"))
        assertTrue(text.contains("<string>/tmp/app.jar</string>"))
    }


    @Test
    fun `disable deletes plist before launchctl disable`() {
        val directory = Files.createTempDirectory("lidsleepx-login-item-")
        val plist = directory.resolve("$APP_ID.plist")
        val calls = mutableListOf<List<String>>()
        val existedDuringCall = mutableListOf<Boolean>()
        try {
            Files.writeString(plist, "test")
            val item = MacLoginItem(plist) { args ->
                calls += args
                existedDuringCall += Files.exists(plist)
                true
            }

            assertTrue(item.disable())
            assertEquals(listOf("disable", "gui/${MacLoginItem.currentUid()}/$APP_ID"), calls.single())
            assertEquals(listOf(false), existedDuringCall)
            assertFalse(Files.exists(plist))
        } finally {
            Files.deleteIfExists(plist)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun `enable skips bootstrap when the service is loaded`() {
        val directory = Files.createTempDirectory("lidsleepx-login-item-")
        val plist = directory.resolve("$APP_ID.plist")
        val calls = mutableListOf<List<String>>()
        try {
            val item = MacLoginItem(plist) { args ->
                calls += args
                true
            }

            assertTrue(item.enable())
            assertTrue(Files.exists(plist))
            assertEquals(listOf("enable", "gui/${MacLoginItem.currentUid()}/$APP_ID"), calls[0])
            assertEquals(listOf("print", "gui/${MacLoginItem.currentUid()}/$APP_ID"), calls[1])
            assertEquals(2, calls.size)
        } finally {
            Files.deleteIfExists(plist)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun `enable bootstraps when the service is not loaded`() {
        val directory = Files.createTempDirectory("lidsleepx-login-item-")
        val plist = directory.resolve("$APP_ID.plist")
        val calls = mutableListOf<List<String>>()
        try {
            val item = MacLoginItem(plist) { args ->
                calls += args
                args.first() != "print"
            }

            assertTrue(item.enable())
            assertEquals(listOf("enable", "gui/${MacLoginItem.currentUid()}/$APP_ID"), calls[0])
            assertEquals(listOf("print", "gui/${MacLoginItem.currentUid()}/$APP_ID"), calls[1])
            assertEquals(listOf("bootstrap", "gui/${MacLoginItem.currentUid()}", plist.toString()), calls[2])
        } finally {
            Files.deleteIfExists(plist)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun `xml escaping neutralizes markup characters`() {
        assertEquals(
            "a&amp;b&lt;c&gt;d&quot;e&apos;f",
            MacLoginItem.xmlEscape("a&b<c>d\"e'f"),
        )
    }

    @Test
    fun `shell quoting wraps values with spaces`() {
        assertEquals("'/Applications/Lid SleepX.app'", MacLoginItem.commandLine("/Applications/Lid SleepX.app", emptyList()))
    }

    @Test
    fun `development command is not empty`() {
        assertTrue(MacLoginItem().developmentCommand().isNotBlank())
    }
}
