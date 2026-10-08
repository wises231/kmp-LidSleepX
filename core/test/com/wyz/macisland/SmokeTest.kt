package com.wyz.macisland.core

import kotlin.test.Test
import kotlin.test.assertEquals

class SmokeTest {
    @Test
    fun versionIsPresent() {
        assertEquals("0.4.0", APP_VERSION)
    }
}
