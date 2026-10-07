package com.wyz.lidsleepx.app

import com.wyz.lidsleepx.app.platform.SingleInstanceLock
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SingleInstanceLockTest {
    @Test
    fun `second lock fails while the first lock is held`() {
        val directory = Files.createTempDirectory("lidsleepx-lock-")
        val path = directory.resolve("app.lock")
        val first = SingleInstanceLock(path)
        val second = SingleInstanceLock(path)
        try {
            assertTrue(first.acquire())
            assertFalse(second.acquire())
        } finally {
            second.close()
            first.close()
            Files.deleteIfExists(path)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun `lock is free after the owner closes it`() {
        val directory = Files.createTempDirectory("lidsleepx-lock-")
        val path = directory.resolve("app.lock")
        val first = SingleInstanceLock(path)
        try {
            assertTrue(first.acquire())
            first.close()
            val second = SingleInstanceLock(path)
            try {
                assertTrue(second.acquire())
            } finally {
                second.close()
            }
        } finally {
            first.close()
            Files.deleteIfExists(path)
            Files.deleteIfExists(directory)
        }
    }
}
