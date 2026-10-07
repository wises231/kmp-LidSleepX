package com.wyz.lidsleepx.app

import com.wyz.lidsleepx.app.ui.AppLanguage
import com.wyz.lidsleepx.app.ui.Strings
import com.wyz.lidsleepx.app.ui.formatDuration
import com.wyz.lidsleepx.app.ui.stringsFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class StringsTest {
    @Test
    fun `every string field has text in both languages`() {
        val fields = Strings::class.java.declaredFields.filter { it.type == String::class.java }
        assertTrue(fields.isNotEmpty())
        for (field in fields) {
            field.isAccessible = true
            for (language in AppLanguage.entries) {
                val value = field.get(stringsFor(language)) as String
                assertTrue(value.isNotBlank(), "${field.name} is blank for $language")
            }
        }
    }

    @Test
    fun `languages provide distinct labels`() {
        assertNotEquals(stringsFor(AppLanguage.ENGLISH).sleepNow, stringsFor(AppLanguage.SIMPLIFIED_CHINESE).sleepNow)
        assertEquals("LidSleepX", stringsFor(AppLanguage.SIMPLIFIED_CHINESE).appName)
    }

    @Test
    fun `duration formatting respects language`() {
        assertEquals("1h 30m", formatDuration(5_400, AppLanguage.ENGLISH))
        assertEquals("5m", formatDuration(300, AppLanguage.ENGLISH))
        assertEquals("1小时30分", formatDuration(5_400, AppLanguage.SIMPLIFIED_CHINESE))
        assertEquals("5分", formatDuration(300, AppLanguage.SIMPLIFIED_CHINESE))
    }

    @Test
    fun `language codes fall back to a default`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromCode("en"))
        assertEquals(AppLanguage.SIMPLIFIED_CHINESE, AppLanguage.fromCode("zh-CN"))
    }
}
