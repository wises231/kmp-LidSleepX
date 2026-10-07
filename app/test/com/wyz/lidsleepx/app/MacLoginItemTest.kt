package com.wyz.lidsleepx.app

import com.wyz.lidsleepx.app.platform.MacLoginItem
import kotlin.test.Test
import kotlin.test.assertEquals
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
