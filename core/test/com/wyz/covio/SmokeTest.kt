package com.wyz.covio.core

import kotlin.test.Test
import kotlin.test.assertEquals

class SmokeTest {
    @Test
    fun versionIsPresent() {
        assertEquals("0.4.2", APP_VERSION)
    }
}
