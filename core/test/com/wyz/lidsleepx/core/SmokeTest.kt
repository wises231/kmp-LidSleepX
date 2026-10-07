package com.wyz.lidsleepx.core

import kotlin.test.Test
import kotlin.test.assertEquals

class SmokeTest {
    @Test
    fun versionIsPresent() {
        assertEquals("0.1.0", APP_VERSION)
    }
}
