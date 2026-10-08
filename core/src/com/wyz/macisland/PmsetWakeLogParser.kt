package com.wyz.macisland.core

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * The wake-log parsing approach follows BatteryHog.
 * Source: https://github.com/luke-fairbanks/BatteryHog
 * Copyright (c) 2026 Luke Fairbanks
 * Licensed under the MIT License. See THIRD_PARTY_NOTICES.md.
 */
object PmsetWakeLogParser {
    private const val RETENTION_SECONDS = 24L * 60L * 60L
    private const val FUTURE_TOLERANCE_SECONDS = 300L
    private val timestampPattern = Regex("""^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2} [+-]\d{4})\s+(.+)$""")
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z", Locale.US)
    private val reasonPattern = Regex("""\bdue to\s+(.+?)(?:\s+Using\s|$)""", RegexOption.IGNORE_CASE)
    private val darkWakeToFullWakeToken = Regex("""\bDarkWake\s+to\s+FullWake\b""", RegexOption.IGNORE_CASE)
    private val fullWakeToken = Regex("""(?:^|\s)Wake(?:\s|\[|$)""")
    private val darkWakeToken = Regex("""(?:^|\s)DarkWake(?:\s|\[|$)""")

    fun parse(text: String, nowEpochSeconds: Long): WakeLogSnapshot {
        val cutoff = nowEpochSeconds - RETENTION_SECONDS
        val futureLimit = nowEpochSeconds + FUTURE_TOLERANCE_SECONDS
        val events = mutableListOf<WakeEvent>()

        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            val match = timestampPattern.matchEntire(line) ?: continue
            val atEpochSeconds = runCatching {
                OffsetDateTime.parse(match.groupValues[1], formatter).toEpochSecond()
            }.getOrNull() ?: continue
            if (atEpochSeconds < cutoff || atEpochSeconds > futureLimit) continue

            val remainder = match.groupValues[2]
            val kind = classify(remainder) ?: continue
            val reason = reasonPattern.find(remainder)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            events += WakeEvent(kind = kind, atEpochSeconds = atEpochSeconds, reason = reason)
        }

        events.sortWith(compareBy<WakeEvent> { it.atEpochSeconds })
        return WakeLogSnapshot(
            events = events,
            darkWakeCount24h = events.count { it.kind == WakeKind.DARK },
        )
    }

    private fun classify(remainder: String): WakeKind? {
        val eventText = remainder.substringBefore(" from ", remainder).trim()
        return when {
            darkWakeToFullWakeToken.containsMatchIn(eventText) -> WakeKind.FULL
            fullWakeToken.containsMatchIn(eventText) -> WakeKind.FULL
            darkWakeToken.containsMatchIn(eventText) -> WakeKind.DARK
            else -> null
        }
    }
}
