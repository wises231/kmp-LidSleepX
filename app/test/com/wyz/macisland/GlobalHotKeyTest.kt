package com.wyz.macisland.app

import com.wyz.macisland.app.platform.GlobalHotKey
import kotlin.test.Test
import kotlin.test.assertEquals

class GlobalHotKeyTest {
    @Test
    fun `control option T uses the Carbon key code and modifiers`() {
        assertEquals(17, GlobalHotKey.KEY_CODE_T)
        assertEquals(0x1000, GlobalHotKey.MODIFIER_CONTROL)
        assertEquals(0x0800, GlobalHotKey.MODIFIER_OPTION)
        assertEquals(0x1800, GlobalHotKey.CONTROL_OPTION_T)
    }
}
