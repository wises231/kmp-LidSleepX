package com.wyz.macisland.app

import com.wyz.macisland.app.platform.PmsetWakeLogReader
import com.wyz.macisland.app.platform.runPmsetCommand
import com.wyz.macisland.core.WakeKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PmsetWakeLogReaderTest {
    @Test
    fun `reader parses command output`() {
        val reader = PmsetWakeLogReader(
            commandRunner = { _, _ ->
                "2026-10-08 11:18:14 +0800 DarkWake DarkWake from Deep Idle : due to RTC Using BATT (Charge:80%)"
            },
            nowEpochSeconds = { 1_791_432_000L },
        )

        val snapshot = assertNotNull(reader.read())
        assertEquals(WakeKind.DARK, snapshot.latest?.kind)
        assertEquals("RTC", snapshot.latest?.reason)
    }

    @Test
    fun `reader returns null when the command fails`() {
        val reader = PmsetWakeLogReader(commandRunner = { _, _ -> null })
        assertNull(reader.read())
    }

    @Test
    fun `command times out and returns null`() {
        assertNull(runPmsetCommand(listOf("/bin/sh", "-c", "sleep 5"), 100L))
    }
}
