package com.wyz.covio.app

import com.wyz.covio.app.ui.awaitHelperStatus
import com.wyz.covio.core.HelperStatus
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class HelperStatusAwaitTest {
    @Test
    fun `returns installed after startup completes`() = runBlocking {
        var calls = 0
        val status = awaitHelperStatus(HelperStatus.NOT_INSTALLED, 1_000L, 1L) {
            calls += 1
            if (calls >= 2) HelperStatus.INSTALLED else HelperStatus.ERROR
        }
        assertEquals(HelperStatus.INSTALLED, status)
        assertEquals(2, calls)
    }

    @Test
    fun `returns last status after timeout`() = runBlocking {
        val status = awaitHelperStatus(HelperStatus.NOT_INSTALLED, 20L, 1L) {
            HelperStatus.ERROR
        }
        assertEquals(HelperStatus.ERROR, status)
    }

    @Test
    fun `installed status returns immediately`() = runBlocking {
        val status = awaitHelperStatus(HelperStatus.INSTALLED, 1_000L, 1L) {
            error("status provider must not run")
        }
        assertEquals(HelperStatus.INSTALLED, status)
    }
}
