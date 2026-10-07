package com.wyz.lidsleepx.core

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
    private val clock: () -> Long = System::currentTimeMillis,
    private val stateListener: StateListener = StateListener {},
) {
    private val lock = Any()
    private val restoreInProgress = AtomicBoolean(false)

    private var config = initialConfig.normalized()
    private var state = AppState(enabled = config.enabled)
    private var started = false
    private var previousPowerState: PowerState? = null
    private var idleCancelAtMillis: Long? = null
    private var lidCancelAtMillis: Long? = null
    private var restoreAfterSleep: RestorePoint? = null

    val currentState: AppState get() = synchronized(lock) { state }
    val currentConfig: AppConfig get() = synchronized(lock) { config }

    fun start() {
        synchronized(lock) {
            if (started) return
            started = true
        }
        power.subscribe(::onPowerChanged)
        lid.subscribe(::onLidChanged)
        sleepWatcher.subscribe(::onWillSleep, ::onDidWake)

        val initialLid = lid.read()
        synchronized(lock) { state = state.copy(lid = initialLid, helperStatus = privileged.status()) }
        onPowerChanged()
        reconcileInitialState()
        publish()
    }

    fun stop() {
        synchronized(lock) {
            if (!started) return
            started = false
            idleCancelAtMillis = null
            lidCancelAtMillis = null
        }
        runCatching { sleepController.setIdleSleepPrevented(false) }
        if (!currentState.lidSleepAvailable) {
            runCatching { privileged.setDisableSleep(false) }
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

    fun updateConfig(newConfig: AppConfig) {
        val normalized = newConfig.normalized()
        val enabledChanged: Boolean
        synchronized(lock) {
            enabledChanged = config.enabled != normalized.enabled
            config = normalized
            state = state.copy(enabled = normalized.enabled)
        }
        if (enabledChanged) setEnabled(normalized.enabled) else reconcilePolicy()
        publish()
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
            if (remaining <= 0L) setLidSleepAvailable(true) else updateCountdowns(nowMillis)
        }

        val snapshot = currentState
        if (snapshot.enabled && !snapshot.lidSleepAvailable && snapshot.idleSleepAvailable) {
            val timeout = sleepController.systemIdleSleepTimeoutSeconds()
            if (timeout != null && timeout > 0L && idle.idleSeconds() >= timeout) {
                logger.info("Idle timeout reached while lid sleep is prevented; sleeping manually.")
                sleepNow()
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        synchronized(lock) {
            if (config.enabled == enabled && state.enabled == enabled) return
            config = config.copy(enabled = enabled)
            state = state.copy(enabled = enabled)
        }
        if (!enabled) {
            synchronized(lock) {
                idleCancelAtMillis = null
                lidCancelAtMillis = null
            }
            setIdleSleepAvailable(true)
            setLidSleepAvailable(true)
            publish()
            return
        }
        reconcilePolicy()
        publish()
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
        val success = runCatching { privileged.setDisableSleep(!available) }
            .onFailure { logger.warn("Cannot update disablesleep", it) }
            .getOrDefault(false)
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

    fun scheduleCancelIdle(afterSeconds: Long) {
        require(afterSeconds > 0L) { "afterSeconds must be positive" }
        setIdleSleepAvailable(false)
        synchronized(lock) { idleCancelAtMillis = clock() + afterSeconds * 1_000L }
        updateCountdowns(clock())
        publish()
    }

    fun scheduleCancelLid(afterSeconds: Long) {
        require(afterSeconds > 0L) { "afterSeconds must be positive" }
        if (setLidSleepAvailable(false)) {
            synchronized(lock) { lidCancelAtMillis = clock() + afterSeconds * 1_000L }
            updateCountdowns(clock())
            publish()
        }
    }

    fun sleepNow() {
        val fixIdle: Boolean
        val fixLid: Boolean
        val savedIdleDeadline: Long?
        val savedLidDeadline: Long?
        synchronized(lock) {
            fixIdle = !state.idleSleepAvailable
            fixLid = !state.lidSleepAvailable
            savedIdleDeadline = idleCancelAtMillis
            savedLidDeadline = lidCancelAtMillis
            if (fixIdle || fixLid) {
                restoreAfterSleep = RestorePoint(
                    restoreIdle = fixIdle,
                    restoreLid = fixLid,
                    idleDeadline = savedIdleDeadline,
                    lidDeadline = savedLidDeadline,
                )
            }
        }
        if (fixIdle) setIdleSleepAvailable(true)
        if (fixLid) setLidSleepAvailable(true)
        runCatching { sleepController.sleep() }
            .onFailure { logger.error("Sleep request failed", it) }
        if (fixIdle || fixLid) {
            Thread {
                Thread.sleep(2_000L)
                restoreAfterSleep()
            }.apply {
                name = "LidSleepX-sleep-restore"
                isDaemon = true
                start()
            }
        }
    }

    fun displaySleepNow() {
        runCatching { sleepController.displaySleep() }
            .onFailure { logger.error("Display sleep request failed", it) }
    }

    private fun reconcileInitialState() {
        val installed = privileged.isInstalled()
        synchronized(lock) {
            state = state.copy(
                helperStatus = if (installed) privileged.status() else HelperStatus.NOT_INSTALLED,
            )
        }
        val current = currentState
        val keepLidPrevented = installed &&
            current.enabled &&
            current.battery?.state?.isCharging == true &&
            currentConfig.disableLidSleepInCharging
        if (!keepLidPrevented) {
            if (installed) runCatching { privileged.setDisableSleep(false) }
            synchronized(lock) { state = state.copy(lidSleepAvailable = true) }
        }
        reconcilePolicy()
        publish()
    }

    private fun reconcilePolicy() {
        val snapshot = currentState
        if (!snapshot.enabled) return
        val battery = snapshot.battery ?: power.read()?.also { battery ->
            synchronized(lock) { state = state.copy(battery = battery) }
        }
        if (battery == null) return
        if (battery.state.isCharging) {
            if (currentConfig.disableIdleSleepInCharging) setIdleSleepAvailable(false)
            if (currentConfig.disableLidSleepInCharging && privileged.isInstalled()) {
                val wasOpen = currentState.lid == LidState.OPEN
                if (setLidSleepAvailable(false) && wasOpen && currentState.lid == LidState.CLOSED) sleepNow()
            }
        } else if (battery.isDischarging) {
            if (currentConfig.disableIdleSleepInCharging) setIdleSleepAvailable(true)
            if (currentConfig.disableLidSleepInCharging && privileged.isInstalled()) setLidSleepAvailable(true)
        }
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

        if (previous == null || previous != battery.state) applyChargePolicy(previous, battery.state)

        val snapshot = currentConfig
        if (currentState.enabled && snapshot.lowBatteryCapacitySleep && battery.isDischarging) {
            val lowCapacity = battery.percent <= snapshot.lowBatteryCapacity
            val lowRemaining = battery.remainingSeconds?.let {
                it <= snapshot.lowTimeRemainingMinutes.toLong() * 60L
            } ?: false
            if (lowCapacity || lowRemaining) {
                logger.info("Low battery detected: ${battery.percent}% remaining=${battery.remainingSeconds}")
                sleepNow()
            }
        }
    }

    private fun applyChargePolicy(previous: PowerState?, current: PowerState) {
        val snapshot = currentConfig
        if (current == PowerState.DISCHARGING) {
            if (snapshot.disableIdleSleepInCharging) setIdleSleepAvailable(true)
            if (snapshot.disableLidSleepInCharging && privileged.isInstalled()) {
                if (setLidSleepAvailable(true) && currentState.lid == LidState.CLOSED) sleepNow()
            }
        } else if (current.isCharging && (previous == null || previous == PowerState.DISCHARGING || previous == PowerState.UNKNOWN)) {
            if (snapshot.disableIdleSleepInCharging) setIdleSleepAvailable(false)
            if (snapshot.disableLidSleepInCharging && privileged.isInstalled()) setLidSleepAvailable(false)
        }
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
                sleepNow()
            }
        }
    }

    private fun onWillSleep() {
        logger.info("System will sleep.")
    }

    private fun onDidWake() {
        logger.info("System did wake.")
        restoreAfterSleep()
    }

    private fun restoreAfterSleep() {
        val restore = synchronized(lock) { restoreAfterSleep } ?: return
        if (!restoreInProgress.compareAndSet(false, true)) return
        try {
            if (restore.restoreIdle) setIdleSleepAvailable(false)
            if (restore.restoreLid) setLidSleepAvailable(false)
            synchronized(lock) {
                restoreAfterSleep = null
                if (restore.idleDeadline != null) idleCancelAtMillis = restore.idleDeadline
                if (restore.lidDeadline != null) lidCancelAtMillis = restore.lidDeadline
            }
            updateCountdowns(clock())
            publish()
        } finally {
            restoreInProgress.set(false)
        }
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
}
