package com.wyz.macisland.core

import kotlin.test.Test
import kotlin.test.assertEquals

class SmokeTest {
    @Test
    fun versionIsPresent() {
        assertEquals("0.4.1", APP_VERSION)
    }
}
