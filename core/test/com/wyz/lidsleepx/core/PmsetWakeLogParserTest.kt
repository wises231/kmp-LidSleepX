package com.wyz.lidsleepx.core

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PmsetWakeLogParserTest {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z", Locale.US)

    @Test
    fun `classifies dark wake and full wake events with reasons`() {
        val snapshot = PmsetWakeLogParser.parse(
            """
            2026-10-08 11:18:14 +0800 DarkWake DarkWake from Deep Idle [CDNP] : due to NUB.SPMISw3IRQ Using BATT (Charge:80%) 2 secs
            2026-10-08 12:20:00 +0800 Wake Wake from Deep Idle [CDNVA] : due to USB-C_plug Using AC (Charge:80%)
            2026-10-08 13:34:53 +0800 Wake DarkWake to FullWake from Deep Idle [CDNVA] : due to UserActivity Assertion Using AC (Charge:80%) 141 secs
            """.trimIndent(),
            epoch("2026-10-08 14:00:00 +0800"),
        )

        assertEquals(3, snapshot.events.size)
        assertEquals(WakeKind.DARK, snapshot.events[0].kind)
        assertEquals("NUB.SPMISw3IRQ", snapshot.events[0].reason)
        assertEquals(WakeKind.FULL, snapshot.events[1].kind)
        assertEquals("USB-C_plug", snapshot.events[1].reason)
        assertEquals(WakeKind.FULL, snapshot.events[2].kind)
        assertEquals("UserActivity Assertion", snapshot.events[2].reason)
        assertEquals(1, snapshot.darkWakeCount24h)
    }

    @Test
    fun `parses real pmset log lines`() {
        val snapshot = PmsetWakeLogParser.parse(
            """2026-10-08 11:01:01 +0800 DarkWake            	DarkWake from Deep Idle [CDNP] : due to NUB.SPMISw3IRQ nub-spmi0.0x02 rtc/Maintenance Using BATT (Charge:80%) 6 secs
2026-10-08 13:34:53 +0800 Wake                	Wake from Deep Idle [CDNVA] : due to smc.sysState.Wake(0x70070000) USB-C_plug SMC.OutboxNotEmpty/Notification Using AC (Charge:80%)""",
            epoch("2026-10-08 14:00:00 +0800"),
        )

        assertEquals(2, snapshot.events.size)
        assertEquals(WakeKind.DARK, snapshot.events[0].kind)
        assertEquals("NUB.SPMISw3IRQ nub-spmi0.0x02 rtc/Maintenance", snapshot.events[0].reason)
        assertEquals(WakeKind.FULL, snapshot.events[1].kind)
        assertEquals(
            "smc.sysState.Wake(0x70070000) USB-C_plug SMC.OutboxNotEmpty/Notification",
            snapshot.events[1].reason,
        )
        assertEquals(1, snapshot.darkWakeCount24h)
    }

    @Test
    fun `keeps events inside twenty four hour boundary`() {
        val now = epoch("2026-10-09 00:00:00 +0800")
        val snapshot = PmsetWakeLogParser.parse(
            """
            2026-10-07 23:59:59 +0800 DarkWake DarkWake from Deep Idle : due to Old
            2026-10-08 00:00:00 +0800 DarkWake DarkWake from Deep Idle : due to Boundary
            """.trimIndent(),
            now,
        )

        assertEquals(1, snapshot.events.size)
        assertEquals("Boundary", snapshot.events.single().reason)
        assertEquals(1, snapshot.darkWakeCount24h)
    }

    @Test
    fun `ignores malformed and unknown lines`() {
        val snapshot = PmsetWakeLogParser.parse(
            """
            not a timestamp
            2026-10-08 13:00:00 +0800 Sleep Sleep from Idle : due to Idle
            2026-10-08 13:01:00 +0800 Wake Wake from Deep Idle : due to User
            """.trimIndent(),
            epoch("2026-10-08 14:00:00 +0800"),
        )

        assertEquals(1, snapshot.events.size)
        assertEquals(WakeKind.FULL, snapshot.events.single().kind)
    }

    @Test
    fun `returns an empty snapshot for garbled input`() {
        val snapshot = PmsetWakeLogParser.parse("�\nnoise\n", epoch("2026-10-08 14:00:00 +0800"))
        assertTrue(snapshot.events.isEmpty())
        assertEquals(0, snapshot.darkWakeCount24h)
    }

    private fun epoch(value: String): Long = OffsetDateTime.parse(value, formatter).toEpochSecond()
}
