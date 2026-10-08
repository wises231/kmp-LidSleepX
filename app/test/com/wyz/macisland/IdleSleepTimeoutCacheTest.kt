package com.wyz.macisland.app

import com.wyz.macisland.app.platform.IdleSleepTimeoutCache
import kotlin.test.Test
import kotlin.test.assertEquals

class IdleSleepTimeoutCacheTest {
    @Test
    fun `cache reuses timeout inside ttl`() {
        var now = 10_000L
        var reads = 0
        val cache = IdleSleepTimeoutCache(clock = { now }, timeoutReader = {
            reads += 1
            120L
        }, ttlMillis = 60_000L)

        assertEquals(120L, cache.get())
        now += 59_999L
        assertEquals(120L, cache.get())
        assertEquals(1, reads)
    }

    @Test
    fun `cache reads timeout after ttl`() {
        var now = 10_000L
        var reads = 0
        val cache = IdleSleepTimeoutCache(clock = { now }, timeoutReader = {
            reads += 1
            if (reads == 1) 120L else 300L
        }, ttlMillis = 60_000L)

        assertEquals(120L, cache.get())
        now += 60_000L
        assertEquals(300L, cache.get())
        assertEquals(2, reads)
    }

    @Test
    fun `invalidate forces the next read`() {
        var reads = 0
        val cache = IdleSleepTimeoutCache(timeoutReader = {
            reads += 1
            reads * 60L
        })

        assertEquals(60L, cache.get())
        assertEquals(60L, cache.get())
        cache.invalidate()
        assertEquals(120L, cache.get())
        assertEquals(2, reads)
    }

    @Test
    fun `reader failure is cached as unavailable`() {
        var reads = 0
        val cache = IdleSleepTimeoutCache(timeoutReader = {
            reads += 1
            error("pmset failed")
        })

        assertEquals(null, cache.get())
        assertEquals(null, cache.get())
        assertEquals(1, reads)
    }
}
