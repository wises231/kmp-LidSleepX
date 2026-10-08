package com.wyz.macisland.core

import java.util.concurrent.atomic.AtomicBoolean

class Engine(
    initialConfig: AppConfig,
    private val power: PowerSource,
    private val lid: LidSensor,
    private val idle: IdleSensor,
    private val sleepController: SleepController,
    private val sleepWatcher: SleepWatcher,
    private val privileged: PrivilegedOps,
    private val logger: AppLogger,
    private val wakeLogReader: WakeLogReader = WakeLogReader { null },
    private val clock: () -> Long = System::currentTimeMillis,
    private val stateListener: StateListener = StateListener {},
    private val wakeScanExecutor: ((Runnable) -> Unit) = defaultWakeScanExecutor(),
    private val wakeDelay: (Long) -> Unit = { millis -> Thread.sleep(millis) },
    private val wakeRetryDelayMillis: Long = 1_000L,
    private val wakeRetryCount: Int = 3,
    private val wakeCacheNowMillis: () -> Long = System::currentTimeMillis,
) {
    private val lock = Any()
    private val restoreInProgress = AtomicBoolean(false)
    private val reconcileInProgress = AtomicBoolean(false)

    private var config = initialConfig.normalized()
    private var state = AppState(enabled = config.enabled)
    private var started = false
    private var previousPowerState: PowerState? = null
    private var idleCancelAtMillis: Long? = null
    private var lidCancelAtMillis: Long? = null
    private var restoreAfterSleep: RestorePoint? = null
    private var manualLidHold = false
    private var lowBatterySleepLatched = false
    private var lastWakeEvent: WakeEvent? = null
    private var wakeScanScheduled = false
    private var cachedWakeLog: WakeLogCache? = null

    val currentState: AppState get() = synchronized(lock) { state }
    val currentConfig: AppConfig get() = synchronized(lock) { config }

    fun start() {
        synchronized(lock) {
            if (started) return
            started = true
        }
        power.subscribe(::onPowerChanged)
        lid.subscribe(::onLidChanged)
        sleepWatcher.subscribe(::onSleepEvent)

        val initialLid = runCatching { lid.read() }.getOrDefault(LidState.UNKNOWN)
        val initialBattery = runCatching { power.read() }.getOrNull()
        val helperStatus = runCatching { privileged.status() }.getOrDefault(HelperStatus.ERROR)
        synchronized(lock) {
            state = state.copy(
                lid = initialLid,
                battery = initialBattery,
                helperStatus = helperStatus,
            )
        }
        startInitialWakeScan()
        reconcilePolicy(clearStaleLidFlag = true)
        publish()
    }

    fun stop() {
        synchronized(lock) {
            if (!started) return
            started = false
            idleCancelAtMillis = null
            lidCancelAtMillis = null
            restoreAfterSleep = null
            manualLidHold = false
            lowBatterySleepLatched = false
            wakeScanScheduled = false
            state = state.copy(darkWake = state.darkWake.copy(scanning = false))
        }
        runCatching { sleepController.setIdleSleepPrevented(false) }
        if (!currentState.lidSleepAvailable || runCatching { privileged.isInstalled() }.getOrDefault(false)) {
            runCatching { privileged.setDisableSleep(false) }
                .onFailure { logger.warn("Cannot clear disablesleep on stop", it) }
        }
        runCatching { power.stop() }
        runCatching { lid.stop() }
        runCatching { sleepWatcher.stop() }
        synchronized(lock) {
            state = state.copy(
                idleSleepAvailable = true,
                lidSleepAvailable = true,
                idleCancelRemainingSeconds = null,
                lidCancelRemainingSeconds = null,
            )
        }
        publish()
    }

    fun updateConfig(newConfig: AppConfig): Boolean {
        val normalized = newConfig.normalized()
        val previous: AppConfig
        val enabledChanged: Boolean
        val darkWakeChanged: Boolean
        synchronized(lock) {
            previous = config
            enabledChanged = config.enabled != normalized.enabled
            darkWakeChanged = config.darkWakeAwarenessEnabled != normalized.darkWakeAwarenessEnabled
            config = normalized
            state = state.copy(enabled = normalized.enabled)
        }
        val success = if (enabledChanged) {
            setEnabled(normalized.enabled)
        } else {
            if (darkWakeChanged) {
                if (normalized.darkWakeAwarenessEnabled) {
                    startInitialWakeScan()
                } else {
                    synchronized(lock) { state = state.copy(darkWake = state.darkWake.copy(scanning = false)) }
                    publish()
                }
            }
            reconcilePolicy()
        }
        if (!success) {
            synchronized(lock) {
                config = previous
                state = state.copy(enabled = previous.enabled)
            }
        }
        publish()
        return success
    }

    fun tick(nowMillis: Long = clock()) {
        val idleDeadline: Long?
        val lidDeadline: Long?
        synchronized(lock) {
            idleDeadline = idleCancelAtMillis
            lidDeadline = lidCancelAtMillis
        }
        if (idleDeadline != null) {
            val remaining = idleDeadline - nowMillis
            if (remaining <= 0L) setIdleSleepAvailable(true) else updateCountdowns(nowMillis)
        }
        if (lidDeadline != null) {
            val remaining = lidDeadline - nowMillis
            if (remaining <= 0L) {
                synchronized(lock) { lidCancelAtMillis = null }
                reconcilePolicy()
            } else {
                updateCountdowns(nowMillis)
            }
        }

        val snapshot = currentState
        if (snapshot.enabled && !snapshot.lidSleepAvailable && snapshot.idleSleepAvailable) {
            val timeout = sleepController.systemIdleSleepTimeoutSeconds()
            if (timeout != null && timeout > 0L && idle.idleSeconds() >= timeout) {
                logger.info("Idle timeout reached while lid sleep is prevented; sleeping manually.")
                requestSleep(restoreAfterWake = true)
            }
        }
    }

    fun setEnabled(enabled: Boolean): Boolean {
        synchronized(lock) {
            if (config.enabled == enabled && state.enabled == enabled) return true
            config = config.copy(enabled = enabled)
            state = state.copy(enabled = enabled)
            if (!enabled) {
                idleCancelAtMillis = null
                lidCancelAtMillis = null
                manualLidHold = false
                lowBatterySleepLatched = false
            }
        }
        val success = if (!enabled) {
            setIdleSleepAvailable(true)
            applyDesiredLidHold(forceClear = true).success
        } else {
            reconcilePolicy()
        }
        publish()
        return success
    }

    fun setIdleSleepAvailable(available: Boolean) {
        val changed: Boolean
        synchronized(lock) {
            changed = state.idleSleepAvailable != available
            state = state.copy(
                idleSleepAvailable = available,
                idleCancelRemainingSeconds = if (available) null else state.idleCancelRemainingSeconds,
            )
            if (available) idleCancelAtMillis = null
        }
        if (changed) {
            runCatching { sleepController.setIdleSleepPrevented(!available) }
                .onFailure { logger.warn("Cannot update idle sleep assertion", it) }
        }
        publish()
    }

    fun setLidSleepAvailable(available: Boolean): Boolean {
        logger.info("Set lid sleep available=$available")
        val helperInstalled = runCatching { privileged.isInstalled() }.getOrDefault(false)
        val success = if (available && !helperInstalled) {
            true
        } else {
            runCatching { privileged.setDisableSleep(!available) }
                .onFailure { logger.warn("Cannot update disablesleep", it) }
                .getOrDefault(false)
        }
        synchronized(lock) {
            if (success) {
                state = state.copy(
                    lidSleepAvailable = available,
                    lidCancelRemainingSeconds = if (available) null else state.lidCancelRemainingSeconds,
                )
                if (available) lidCancelAtMillis = null
            }
        }
        publish()
        return success
    }

    fun setManualLidSleepAvailable(available: Boolean) {
        synchronized(lock) { manualLidHold = !available }
        val released = applyDesiredLidHold().released
        if (released && currentState.lid == LidState.CLOSED && currentConfig.lidSleepImmediateOnClose) {
            requestSleep(restoreAfterWake = true)
        }
        publish()
    }

    fun scheduleCancelIdle(afterSeconds: Long) {
        require(afterSeconds > 0L) { "afterSeconds must be positive" }
        setIdleSleepAvailable(false)
        synchronized(lock) { idleCancelAtMillis = clock() + afterSeconds * 1_000L }
        updateCountdowns(clock())
        publish()
    }

    fun scheduleCancelLid(afterSeconds: Long) {
        require(afterSeconds > 0L) { "afterSeconds must be positive" }
        synchronized(lock) { lidCancelAtMillis = clock() + afterSeconds * 1_000L }
        val released = applyDesiredLidHold().released
        if (released && currentState.lid == LidState.CLOSED && currentConfig.lidSleepImmediateOnClose) {
            requestSleep(restoreAfterWake = true)
        }
        updateCountdowns(clock())
        publish()
    }

    fun sleepNow() {
        requestSleep(restoreAfterWake = true)
    }

    fun displaySleepNow() {
        runCatching { sleepController.displaySleep() }
            .onFailure { logger.error("Display sleep request failed", it) }
    }

    private fun requestSleep(restoreAfterWake: Boolean) {
        val fixIdle: Boolean
        val fixLid: Boolean
        val shouldRestore: Boolean
        val savedIdleDeadline: Long?
        val savedLidDeadline: Long?
        synchronized(lock) {
            fixIdle = !state.idleSleepAvailable
            fixLid = !state.lidSleepAvailable
            shouldRestore = restoreAfterWake && (fixIdle || fixLid)
            savedIdleDeadline = idleCancelAtMillis
            savedLidDeadline = lidCancelAtMillis
            restoreAfterSleep = if (shouldRestore) {
                RestorePoint(
                    restoreIdle = fixIdle,
                    restoreLid = fixLid,
                    idleDeadline = savedIdleDeadline,
                    lidDeadline = savedLidDeadline,
                )
            } else {
                null
            }
        }
        if (fixIdle) setIdleSleepAvailable(true)
        if (fixLid) setLidSleepAvailable(true)
        runCatching { sleepController.sleep() }
            .onFailure { logger.error("Sleep request failed", it) }
        if (shouldRestore) {
            Thread {
                Thread.sleep(2_000L)
                restoreAfterSleep()
            }.apply {
                name = "MacIsland-sleep-restore"
                isDaemon = true
                start()
            }
        }
    }

    private fun reconcilePolicy(clearStaleLidFlag: Boolean = false): Boolean {
        if (!reconcileInProgress.compareAndSet(false, true)) {
            logger.info("Policy reconciliation already in progress.")
            return false
        }
        try {
            val snapshot = currentState
            if (!snapshot.enabled) {
                setIdleSleepAvailable(true)
                return applyDesiredLidHold(forceClear = clearStaleLidFlag).success
            }
            val battery = snapshot.battery ?: readBattery() ?: run {
                return applyDesiredLidHold(forceClear = clearStaleLidFlag).success
            }
            if (config.disableIdleSleepInCharging && battery.state.isCharging) {
                setIdleSleepAvailable(false)
            } else if (config.disableIdleSleepInCharging) {
                setIdleSleepAvailable(true)
            }

            val lowBattery = lowBatterySleepRequired(battery)
            if (lowBattery) {
                val result = applyDesiredLidHold(forceClear = clearStaleLidFlag)
                if (!lowBatterySleepLatched) {
                    lowBatterySleepLatched = true
                    logger.info("Low battery detected: ${battery.percent}% remaining=${battery.remainingSeconds}")
                    requestSleep(restoreAfterWake = false)
                }
                return result.success
            }
            lowBatterySleepLatched = false

            val result = applyDesiredLidHold(forceClear = clearStaleLidFlag)
            if (result.released && currentState.lid == LidState.CLOSED && currentConfig.lidSleepImmediateOnClose) {
                requestSleep(restoreAfterWake = true)
            }
            return result.success
        } finally {
            reconcileInProgress.set(false)
        }
    }

    private fun applyDesiredLidHold(forceClear: Boolean = false): LidHoldResult {
        val snapshot = currentState
        val desired = desiredLidHold(snapshot)
        val wasHeld = !snapshot.lidSleepAvailable
        if (desired) {
            if (wasHeld) return LidHoldResult(released = false, success = true)
            val success = setLidSleepAvailable(false)
            return LidHoldResult(released = false, success = success)
        }

        val helperInstalled = runCatching { privileged.isInstalled() }.getOrDefault(false)
        if (!wasHeld && !(forceClear && helperInstalled)) return LidHoldResult(released = false, success = true)
        if (!helperInstalled) return LidHoldResult(released = false, success = false)
        val success = setLidSleepAvailable(true)
        return LidHoldResult(released = success && wasHeld, success = success)
    }

    private fun desiredLidHold(snapshot: AppState): Boolean {
        if (!snapshot.enabled) return false
        if (restoreAfterSleep != null) return false
        if (!helperReady()) return false
        val battery = snapshot.battery
        if (battery != null && lowBatterySleepRequired(battery)) return false
        val powerPolicy = when {
            battery?.state?.isCharging == true -> config.disableLidSleepInCharging
            battery?.isDischarging == true -> config.disableLidSleepOnBattery
            else -> false
        }
        return manualLidHold || lidCancelAtMillis != null || powerPolicy
    }

    private fun helperReady(): Boolean = runCatching {
        privileged.isInstalled() && privileged.status() == HelperStatus.INSTALLED
    }.getOrDefault(false)

    private fun readBattery(): BatteryStatus? = runCatching { power.read() }
        .getOrNull()
        ?.also { battery -> synchronized(lock) { state = state.copy(battery = battery) } }

    private fun lowBatterySleepRequired(battery: BatteryStatus): Boolean {
        val snapshot = currentConfig
        if (!snapshot.lowBatteryCapacitySleep || !battery.isDischarging) return false
        val lowCapacity = battery.percent <= snapshot.lowBatteryCapacity
        val lowRemaining = battery.remainingSeconds?.let {
            it <= snapshot.lowTimeRemainingMinutes.toLong() * 60L
        } ?: false
        return lowCapacity || lowRemaining
    }

    private fun onPowerChanged() {
        val battery = runCatching { power.read() }.getOrNull() ?: return
        val previous: PowerState?
        synchronized(lock) {
            previous = previousPowerState
            previousPowerState = battery.state
            state = state.copy(battery = battery)
        }
        publish()
        if (previous == null || previous != battery.state || currentState.enabled) reconcilePolicy()
    }

    private fun onLidChanged(lidState: LidState) {
        val previous: LidState
        synchronized(lock) {
            previous = state.lid
            state = state.copy(lid = lidState)
        }
        publish()
        if (lidState == LidState.UNKNOWN || previous == lidState || !currentState.enabled) return
        if (lidState == LidState.CLOSED) {
            if (!currentState.lidSleepAvailable) {
                logger.info("Lid closed while lid sleep is prevented; turning display off.")
                displaySleepNow()
            } else if (currentConfig.lidSleepImmediateOnClose) {
                logger.info("Lid closed; starting immediate sleep.")
                requestSleep(restoreAfterWake = true)
            }
        }
    }

    private fun onSleepEvent(event: SleepEvent) {
        when (event) {
            SleepEvent.WillSleep -> logger.info("System will sleep.")
            SleepEvent.WakeSignal -> {
                logger.info("System wake signal received.")
                if (currentConfig.darkWakeAwarenessEnabled) {
                    scheduleWakeScan(initial = false)
                } else {
                    handleFullWake("DarkWake awareness is disabled")
                }
            }
        }
    }

    private fun startInitialWakeScan() {
        if (!currentConfig.darkWakeAwarenessEnabled) return
        scheduleWakeScan(initial = true)
    }

    private fun scheduleWakeScan(initial: Boolean) {
        val shouldSchedule: Boolean
        synchronized(lock) {
            if (!started || !config.darkWakeAwarenessEnabled || wakeScanScheduled) return
            wakeScanScheduled = true
            state = state.copy(darkWake = state.darkWake.copy(scanning = true))
            shouldSchedule = true
        }
        if (!shouldSchedule) return
        publish()
        wakeScanExecutor {
            runWakeScan(initial)
        }
    }

    private fun runWakeScan(initial: Boolean) {
        try {
            val baseline = synchronized(lock) { lastWakeEvent }
            if (!initial) wakeDelay(wakeRetryDelayMillis)
            var snapshot = if (initial) readWakeLog() else readWakeLogFresh()
            var retries = 0
            if (!initial) {
                while (snapshot?.latest == baseline && retries < wakeRetryCount.coerceAtLeast(0)) {
                    retries += 1
                    wakeDelay(wakeRetryDelayMillis)
                    snapshot = readWakeLogFresh() ?: snapshot
                }
            }
            applyWakeSnapshot(snapshot, initial)
        } finally {
            synchronized(lock) { wakeScanScheduled = false }
            finishWakeScan()
        }
    }

    /*
     * The wake log command is expensive. The cache serves repeated refreshes for
     * up to 60 seconds. A wake signal bypasses the cache with readWakeLogFresh().
     * A new timestamp or event kind therefore always bypasses a cached result.
     */
    private fun readWakeLog(): WakeLogSnapshot? {
        val cached = synchronized(lock) { cachedWakeLog }
        if (cached != null && wakeCacheNowMillis() - cached.readAtMillis in 0 until WAKE_LOG_CACHE_MILLIS) {
            return cached.snapshot
        }
        return readWakeLogFresh()
    }

    private fun readWakeLogFresh(): WakeLogSnapshot? {
        val snapshot = runCatching { wakeLogReader.read() }
            .onFailure { logger.warn("Cannot read pmset wake log", it) }
            .getOrNull()
        if (snapshot != null) {
            synchronized(lock) { cachedWakeLog = WakeLogCache(snapshot, wakeCacheNowMillis()) }
        }
        return snapshot
    }

    private fun applyWakeSnapshot(snapshot: WakeLogSnapshot?, initial: Boolean) {
        if (!currentConfig.darkWakeAwarenessEnabled) return
        val previous = synchronized(lock) { state.darkWake }
        if (snapshot == null) {
            logger.warn("Wake log parsing failed. Using FULL wake behavior.")
            synchronized(lock) {
                state = state.copy(
                    darkWake = previous.copy(available = false, scanning = false),
                )
                if (!initial) lastWakeEvent = null
            }
            publish()
            if (!initial) handleFullWake("Wake log is unavailable")
            return
        }

        val latest = snapshot.latest
        synchronized(lock) {
            state = state.copy(
                darkWake = DarkWakeStatus(
                    available = true,
                    scanning = false,
                    lastAtEpochSeconds = latest?.atEpochSeconds,
                    lastReason = latest?.reason?.takeIf { it.isNotBlank() },
                    count24h = snapshot.darkWakeCount24h,
                ),
            )
        }
        publish()

        if (initial) {
            synchronized(lock) { lastWakeEvent = latest }
            return
        }

        val baseline = synchronized(lock) { lastWakeEvent }
        if (latest == null || latest == baseline) {
            logger.info("Wake log did not change after retries.")
            return
        }
        synchronized(lock) { lastWakeEvent = latest }
        when (latest.kind) {
            WakeKind.DARK -> handleDarkWake(latest.reason)
            WakeKind.FULL -> handleFullWake(latest.reason)
        }
    }

    private fun finishWakeScan() {
        synchronized(lock) {
            if (!started) return
            state = state.copy(darkWake = state.darkWake.copy(scanning = false))
        }
        publish()
    }

    private fun handleDarkWake(reason: String) {
        logger.info("DarkWake detected${reason.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""}.")
        if (restoreAfterSleep != null) return
        reconcilePolicy()
    }

    private fun handleFullWake(reason: String) {
        logger.info("Full wake detected${reason.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""}.")
        if (!restoreAfterSleep()) reconcilePolicy()
    }

    private fun restoreAfterSleep(): Boolean {
        val restore = synchronized(lock) { restoreAfterSleep } ?: return false
        if (!restoreInProgress.compareAndSet(false, true)) return true
        try {
            if (restore.restoreIdle) setIdleSleepAvailable(false)
            synchronized(lock) {
                restoreAfterSleep = null
                if (restore.idleDeadline != null) idleCancelAtMillis = restore.idleDeadline
                if (restore.lidDeadline != null) lidCancelAtMillis = restore.lidDeadline
            }
            reconcilePolicy()
            val battery = currentState.battery
            if (restore.restoreLid && battery != null && !lowBatterySleepRequired(battery)) {
                setLidSleepAvailable(false)
            }
            updateCountdowns(clock())
            publish()
        } finally {
            restoreInProgress.set(false)
        }
        return true
    }

    private fun updateCountdowns(nowMillis: Long) {
        synchronized(lock) {
            state = state.copy(
                idleCancelRemainingSeconds = idleCancelAtMillis?.let { ((it - nowMillis) / 1_000L).coerceAtLeast(0L) },
                lidCancelRemainingSeconds = lidCancelAtMillis?.let { ((it - nowMillis) / 1_000L).coerceAtLeast(0L) },
            )
        }
        publish()
    }

    private fun publish() {
        stateListener.onState(currentState)
    }

    private data class RestorePoint(
        val restoreIdle: Boolean,
        val restoreLid: Boolean,
        val idleDeadline: Long?,
        val lidDeadline: Long?,
    )

    private data class LidHoldResult(
        val released: Boolean,
        val success: Boolean,
    )

    private data class WakeLogCache(
        val snapshot: WakeLogSnapshot,
        val readAtMillis: Long,
    )

    companion object {
        private const val WAKE_LOG_CACHE_MILLIS = 60_000L

        private fun defaultWakeScanExecutor(): (Runnable) -> Unit = { runnable ->
            Thread(runnable).apply {
                name = "MacIsland-wake-log"
                isDaemon = true
                start()
            }
        }
    }
}
