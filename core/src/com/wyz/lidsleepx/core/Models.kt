package com.wyz.lidsleepx.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val APP_VERSION = "0.3.0"
const val APP_ID = "com.wyz.lidsleepx"
const val APP_NAME = "LidSleepX"
val SUPPORTED_HIBERNATE_MODES = setOf(0, 3, 25)

@Serializable
data class AppConfig(
    @SerialName("schemaVersion") val schemaVersion: Int = 2,
    @SerialName("language") val language: String = "",
    @SerialName("enabled") val enabled: Boolean = true,
    @SerialName("launchAtLogin") val launchAtLogin: Boolean = false,
    @SerialName("lowBatteryCapacitySleep") val lowBatteryCapacitySleep: Boolean = true,
    @SerialName("lowBatteryCapacity") val lowBatteryCapacity: Int = 6,
    @SerialName("lowTimeRemaining") val lowTimeRemainingMinutes: Int = 10,
    @SerialName("disableIdleSleepInCharging") val disableIdleSleepInCharging: Boolean = false,
    @SerialName("disableLidSleepInCharging") val disableLidSleepInCharging: Boolean = false,
    @SerialName("disableLidSleepOnBattery") val disableLidSleepOnBattery: Boolean = false,
    @SerialName("darkWakeAwarenessEnabled") val darkWakeAwarenessEnabled: Boolean = true,
    @SerialName("lidSleepImmediateOnClose") val lidSleepImmediateOnClose: Boolean = true,
    @SerialName("notificationsEnabled") val notificationsEnabled: Boolean = true,
    @SerialName("updateCheckEnabled") val updateCheckEnabled: Boolean = true,
    @SerialName("updateCheckIntervalHours") val updateCheckIntervalHours: Int = 24,
    @SerialName("lastUpdateCheckEpochSeconds") val lastUpdateCheckEpochSeconds: Long = 0L,
    @SerialName("hibernateMode") val hibernateMode: Int? = null,
    @SerialName("firstRun") val firstRun: Boolean = true,
) {
    fun normalized(): AppConfig = copy(
        schemaVersion = 2,
        lowBatteryCapacity = lowBatteryCapacity.coerceIn(0, 100),
        lowTimeRemainingMinutes = lowTimeRemainingMinutes.coerceAtLeast(0),
        updateCheckIntervalHours = updateCheckIntervalHours.coerceIn(1, 168),
        lastUpdateCheckEpochSeconds = lastUpdateCheckEpochSeconds.coerceAtLeast(0L),
        hibernateMode = hibernateMode?.takeIf { it in SUPPORTED_HIBERNATE_MODES },
    )
}

enum class PowerState {
    DISCHARGING,
    CHARGING,
    CHARGED,
    AC_POWER,
    UNKNOWN;

    val isCharging: Boolean
        get() = this == CHARGING || this == CHARGED || this == AC_POWER
}

data class BatteryStatus(
    val percent: Int,
    val state: PowerState,
    val remainingSeconds: Long?,
) {
    val isDischarging: Boolean get() = state == PowerState.DISCHARGING
}

enum class LidState { UNKNOWN, OPEN, CLOSED }

enum class HelperStatus { NOT_INSTALLED, INSTALLED, OUTDATED, ERROR }

enum class SleepEvent { WillSleep, WakeSignal }

enum class WakeKind { DARK, FULL }

data class WakeEvent(
    val kind: WakeKind,
    val atEpochSeconds: Long,
    val reason: String = "",
)

data class WakeLogSnapshot(
    val events: List<WakeEvent>,
    val darkWakeCount24h: Int,
) {
    val latest: WakeEvent? get() = events.lastOrNull()
}

data class DarkWakeStatus(
    val available: Boolean = false,
    val scanning: Boolean = false,
    val lastAtEpochSeconds: Long? = null,
    val lastReason: String? = null,
    val count24h: Int = 0,
)

data class UpdateInfo(
    val version: String,
    val title: String,
    val releaseUrl: String,
    val publishedAt: String = "",
)

data class AppState(
    val battery: BatteryStatus? = null,
    val lid: LidState = LidState.UNKNOWN,
    val enabled: Boolean = true,
    val idleSleepAvailable: Boolean = true,
    val lidSleepAvailable: Boolean = true,
    val idleCancelRemainingSeconds: Long? = null,
    val lidCancelRemainingSeconds: Long? = null,
    val darkWake: DarkWakeStatus = DarkWakeStatus(),
    val helperStatus: HelperStatus = HelperStatus.NOT_INSTALLED,
    val lastError: String? = null,
)

@Serializable
data class HelperReply(
    val version: String,
    val requestId: String,
    val ok: Boolean,
    val result: Map<String, String>? = null,
    val error: String? = null,
)

@Serializable
data class HelperRequest(
    val version: String,
    val requestId: String,
    val command: String,
    val payload: Map<String, String> = emptyMap(),
)
