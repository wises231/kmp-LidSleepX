package com.wyz.lidsleepx.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.wyz.lidsleepx.app.platform.CoreGraphicsIdleSensor
import com.wyz.lidsleepx.app.platform.FileAppLogger
import com.wyz.lidsleepx.app.platform.GithubUpdateChecker
import com.wyz.lidsleepx.app.platform.MacLidSensor
import com.wyz.lidsleepx.app.platform.MacLoginItem
import com.wyz.lidsleepx.app.platform.MacNotifier
import com.wyz.lidsleepx.app.platform.MacPowerSource
import com.wyz.lidsleepx.app.platform.MacPrivilegedOps
import com.wyz.lidsleepx.app.platform.MacSleepController
import com.wyz.lidsleepx.app.platform.MacSleepWatcher
import com.wyz.lidsleepx.app.platform.PmsetWakeLogReader
import com.wyz.lidsleepx.core.APP_VERSION
import com.wyz.lidsleepx.core.AppConfig
import com.wyz.lidsleepx.core.AppState
import com.wyz.lidsleepx.core.ConfigStore
import com.wyz.lidsleepx.core.Engine
import com.wyz.lidsleepx.core.HelperStatus
import com.wyz.lidsleepx.core.SUPPORTED_HIBERNATE_MODES
import com.wyz.lidsleepx.core.UpdatePolicy
import java.awt.FileDialog
import java.awt.Frame
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import javax.swing.JOptionPane
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AppRuntime {
    val logger = FileAppLogger()
    private val configStore = ConfigStore()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    var config by mutableStateOf(configStore.load().let { config ->
        if (config.language.isBlank()) config.copy(language = AppLanguage.fromCode(null).code).also(configStore::save) else config
    })
        private set

    var state by mutableStateOf(AppState(enabled = config.enabled))
        private set

    var settingsVisible by mutableStateOf(false)
        private set

    var welcomeVisible by mutableStateOf(config.firstRun)
        private set

    var aboutVisible by mutableStateOf(false)
        private set

    var statusMessage by mutableStateOf<String?>(null)
        private set

    var busy by mutableStateOf(false)
        private set

    var helperStatus by mutableStateOf(HelperStatus.NOT_INSTALLED)
        private set

    val language: AppLanguage get() = AppLanguage.fromCode(config.language)
    val strings: Strings get() = stringsFor(language)

    private val power = MacPowerSource()
    private val lid = MacLidSensor()
    private val idle = CoreGraphicsIdleSensor()
    private val sleepController = MacSleepController()
    private val sleepWatcher = MacSleepWatcher()
    private val wakeLogReader = PmsetWakeLogReader()
    private val privileged = MacPrivilegedOps(logger)
    private val loginItem = MacLoginItem()
    private val notifier = MacNotifier(logger) { config.notificationsEnabled }
    private val updateChecker = GithubUpdateChecker()

    private val engine = Engine(
        initialConfig = config,
        power = power,
        lid = lid,
        idle = idle,
        sleepController = sleepController,
        sleepWatcher = sleepWatcher,
        privileged = privileged,
        logger = logger,
        wakeLogReader = wakeLogReader,
        stateListener = { newState ->
            state = newState.copy(helperStatus = helperStatus)
        },
    )

    init {
        logger.info("LidSleepX $APP_VERSION starting")
        helperStatus = privileged.status()
        syncHibernateMode()
        engine.start()
        state = engine.currentState.copy(helperStatus = helperStatus)
        loginItemSync()
        scope.launch {
            var ticks = 0L
            while (isActive) {
                delay(1_000L)
                runCatching { engine.tick() }
                ticks += 1
                if (ticks % 5L == 0L) refreshHelperStatus()
            }
        }
        scope.launch {
            delay(30_000L)
            while (isActive) {
                checkForUpdates(manual = false)
                delay(config.updateCheckIntervalHours.coerceAtLeast(1) * 3_600_000L)
            }
        }
    }

    fun close() {
        runCatching { engine.stop() }
        scope.cancel()
    }

    fun showSettings() {
        settingsVisible = true
    }

    fun hideSettings() {
        settingsVisible = false
    }

    fun showAbout() {
        aboutVisible = true
    }

    fun hideAbout() {
        aboutVisible = false
    }

    fun dismissWelcome() {
        welcomeVisible = false
        updateConfig(config.copy(firstRun = false))
    }

    fun changeLanguage(language: AppLanguage) {
        updateConfig(config.copy(language = language.code))
    }

    fun setEnabled(enabled: Boolean) {
        if (!updateConfig(config.copy(enabled = enabled))) statusMessage = strings.lidPolicyError
    }

    fun setLowBatterySleep(enabled: Boolean) = updateConfig(config.copy(lowBatteryCapacitySleep = enabled))
    fun setLowBatteryCapacity(value: Int, valid: Boolean) {
        if (valid) updateConfig(config.copy(lowBatteryCapacity = value.coerceIn(0, 100)))
    }
    fun setLowTimeRemaining(value: Int, valid: Boolean) {
        if (valid) updateConfig(config.copy(lowTimeRemainingMinutes = value.coerceAtLeast(0)))
    }
    fun setDisableIdleWhileCharging(enabled: Boolean) = updateConfig(config.copy(disableIdleSleepInCharging = enabled))
    fun setDisableLidWhileCharging(enabled: Boolean) {
        if (enabled && !lidPolicyAvailable()) {
            statusMessage = strings.helperOutdated
            return
        }
        if (!updateConfig(config.copy(disableLidSleepInCharging = enabled))) {
            statusMessage = strings.lidPolicyError
        }
    }
    fun setDisableLidOnBattery(enabled: Boolean) {
        if (enabled && !lidPolicyAvailable()) {
            statusMessage = strings.helperOutdated
            return
        }
        if (!updateConfig(config.copy(disableLidSleepOnBattery = enabled))) {
            statusMessage = strings.lidPolicyError
        }
    }
    fun setDarkWakeAwareness(enabled: Boolean) = updateConfig(config.copy(darkWakeAwarenessEnabled = enabled))
    fun setImmediateLidSleep(enabled: Boolean) = updateConfig(config.copy(lidSleepImmediateOnClose = enabled))
    fun setNotifications(enabled: Boolean) = updateConfig(config.copy(notificationsEnabled = enabled))
    fun setUpdateChecks(enabled: Boolean) = updateConfig(config.copy(updateCheckEnabled = enabled))

    fun setSleepMode(mode: Int) {
        if (mode !in SUPPORTED_HIBERNATE_MODES || busy) return
        busy = true
        scope.launch {
            val success = privileged.setHibernateMode(mode)
            if (success) {
                val actual = privileged.hibernateMode() ?: mode
                updateConfig(config.copy(hibernateMode = actual))
                statusMessage = null
            } else {
                statusMessage = strings.sleepModeError
            }
            busy = false
        }
    }

    fun clearConfig() {
        if (busy) return
        val choice = JOptionPane.showConfirmDialog(
            null,
            strings.clearConfigConfirm,
            strings.clearConfig,
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE,
        )
        if (choice != JOptionPane.YES_OPTION) return
        if (!configStore.clear()) {
            statusMessage = strings.updateError
            return
        }
        val defaultLanguage = AppLanguage.fromCode(null)
        val defaults = AppConfig(
            language = defaultLanguage.code,
            launchAtLogin = loginItem.isEnabled(),
            hibernateMode = privileged.hibernateMode(),
        ).normalized()
        config = defaults
        engine.updateConfig(defaults)
        state = engine.currentState.copy(helperStatus = helperStatus)
        welcomeVisible = defaults.firstRun
        statusMessage = stringsFor(defaultLanguage).clearConfigDone
    }

    fun setLaunchAtLogin(enabled: Boolean) {
        val success = if (enabled) loginItem.enable() else loginItem.disable()
        if (success) updateConfig(config.copy(launchAtLogin = enabled))
        else statusMessage = strings.launchAtLoginError
    }

    fun toggleIdleSleep() {
        engine.setIdleSleepAvailable(!state.idleSleepAvailable)
    }

    fun toggleLidSleep() {
        val makeAvailable = !state.lidSleepAvailable
        if (!makeAvailable && !lidPolicyAvailable()) {
            statusMessage = strings.helperOutdated
            return
        }
        engine.setManualLidSleepAvailable(makeAvailable)
        state = engine.currentState.copy(helperStatus = helperStatus)
    }

    fun scheduleCancelIdle(seconds: Long) = engine.scheduleCancelIdle(seconds)
    fun scheduleCancelLid(seconds: Long) = engine.scheduleCancelLid(seconds)
    fun sleepNow() = engine.sleepNow()
    fun displaySleepNow() = engine.displaySleepNow()

    fun installHelper() {
        if (busy) return
        busy = true
        scope.launch {
            val installed = privileged.install()
            refreshHelperStatus(waitForInstalled = installed)
            busy = false
            val success = helperStatus == HelperStatus.INSTALLED
            statusMessage = if (success) strings.helperInstalled else strings.helperInstallError
            if (success) dismissWelcome()
        }
    }

    fun uninstallHelper() {
        if (busy) return
        busy = true
        scope.launch {
            val removed = privileged.uninstall()
            helperStatus = HelperStatus.NOT_INSTALLED
            engine.setLidSleepAvailable(true)
            state = engine.currentState.copy(helperStatus = helperStatus)
            busy = false
            statusMessage = if (removed) strings.helperNotInstalled else strings.helperUninstallError
        }
    }

    fun checkForUpdates(manual: Boolean) {
        val now = System.currentTimeMillis() / 1_000L
        if (!manual && !UpdatePolicy.shouldCheck(config, now)) return
        scope.launch {
            val release = runCatching { updateChecker.latestRelease() }.getOrNull()
            val update = UpdatePolicy.selectUpdate(release, APP_VERSION)
            updateConfig(config.copy(lastUpdateCheckEpochSeconds = now))
            if (update != null) {
                notifier.notify(
                    title = strings.updateAvailable,
                    message = "${update.version} · ${update.title}",
                    releaseUrl = update.releaseUrl,
                )
                if (manual) runCatching { ProcessBuilder("/usr/bin/open", update.releaseUrl).start() }
                statusMessage = "${strings.updateAvailable}: ${update.version}"
            } else if (manual) {
                statusMessage = if (release == null) strings.updateError else strings.upToDate
            }
        }
    }

    fun viewLog() {
        runCatching { ProcessBuilder("/usr/bin/open", logger.logFile()).start() }
            .onFailure { logger.warn("Cannot open log file", it) }
    }

    fun exportLog() {
        runCatching {
            val dialog = FileDialog(null as Frame?, "Export LidSleepX log", FileDialog.SAVE).apply {
                file = "LidSleepX.log"
                isVisible = true
            }
            val selected = dialog.file ?: return@runCatching
            val target = Path.of(dialog.directory, selected)
            val source = Path.of(logger.logFile())
            if (!Files.exists(source)) Files.writeString(source, "")
            val home = System.getProperty("user.home")
            val content = Files.readString(source, StandardCharsets.UTF_8).replace(home, "~")
            Files.writeString(target, content, StandardCharsets.UTF_8)
            statusMessage = strings.logExported
        }.onFailure {
            logger.warn("Cannot export log", it)
            statusMessage = strings.logExportError
        }
    }

    fun clearStatus() {
        statusMessage = null
    }

    private fun updateConfig(newConfig: AppConfig): Boolean {
        val previous = config
        val normalized = newConfig.copy(
            launchAtLogin = loginItem.isEnabled(),
        ).normalized()
        config = normalized
        configStore.save(normalized)
        val success = engine.updateConfig(normalized)
        if (!success) {
            config = previous
            configStore.save(previous)
            engine.updateConfig(previous)
        }
        state = engine.currentState.copy(helperStatus = helperStatus)
        return success
    }

    private suspend fun refreshHelperStatus(waitForInstalled: Boolean = false) {
        val current = privileged.status()
        val status = if (waitForInstalled) awaitHelperStatus(current) { privileged.status() } else current
        if (helperStatus == status) return
        helperStatus = status
        state = engine.currentState.copy(helperStatus = status)
        if (status == HelperStatus.INSTALLED) engine.updateConfig(config)
    }

    private fun loginItemSync() {
        val actual = loginItem.isEnabled()
        if (actual != config.launchAtLogin) {
            config = config.copy(launchAtLogin = actual)
            configStore.save(config)
        }
    }

    private fun syncHibernateMode() {
        val actual = privileged.hibernateMode() ?: return
        if (actual == config.hibernateMode) return
        config = config.copy(hibernateMode = actual)
        configStore.save(config)
    }

    private fun lidPolicyAvailable(): Boolean = helperStatus == HelperStatus.INSTALLED

    companion object {
        fun formatBattery(battery: com.wyz.lidsleepx.core.BatteryStatus, strings: Strings): String {
            val stateText = when {
                battery.state == com.wyz.lidsleepx.core.PowerState.DISCHARGING -> strings.discharging
                battery.state == com.wyz.lidsleepx.core.PowerState.CHARGING -> strings.charging
                battery.state == com.wyz.lidsleepx.core.PowerState.CHARGED -> strings.charged
                battery.state == com.wyz.lidsleepx.core.PowerState.AC_POWER -> "AC"
                else -> strings.unknown
            }
            return "${battery.percent}% · $stateText"
        }
    }
}

internal suspend fun awaitHelperStatus(
    initialStatus: HelperStatus,
    timeoutMillis: Long = 5_000L,
    pollIntervalMillis: Long = 200L,
    statusProvider: () -> HelperStatus,
): HelperStatus {
    if (initialStatus == HelperStatus.INSTALLED) return initialStatus
    val deadline = System.nanoTime() + timeoutMillis.coerceAtLeast(0L) * 1_000_000L
    var status = initialStatus
    while (status != HelperStatus.INSTALLED && System.nanoTime() < deadline) {
        delay(pollIntervalMillis.coerceAtLeast(1L))
        status = statusProvider()
    }
    return status
}
