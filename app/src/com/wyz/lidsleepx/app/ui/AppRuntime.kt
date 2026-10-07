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
import com.wyz.lidsleepx.core.APP_VERSION
import com.wyz.lidsleepx.core.AppConfig
import com.wyz.lidsleepx.core.AppState
import com.wyz.lidsleepx.core.ConfigStore
import com.wyz.lidsleepx.core.Engine
import com.wyz.lidsleepx.core.HelperStatus
import com.wyz.lidsleepx.core.UpdatePolicy
import java.awt.FileDialog
import java.awt.Frame
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
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
        stateListener = { newState ->
            state = newState
        },
    )

    init {
        logger.info("LidSleepX $APP_VERSION starting")
        helperStatus = privileged.status()
        engine.start()
        state = engine.currentState.copy(helperStatus = helperStatus)
        loginItemSync()
        scope.launch {
            while (isActive) {
                delay(1_000L)
                runCatching { engine.tick() }
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
        updateConfig(config.copy(enabled = enabled))
    }

    fun setLowBatterySleep(enabled: Boolean) = updateConfig(config.copy(lowBatteryCapacitySleep = enabled))
    fun setLowBatteryCapacity(value: Int, valid: Boolean) {
        if (valid) updateConfig(config.copy(lowBatteryCapacity = value.coerceIn(0, 100)))
    }
    fun setLowTimeRemaining(value: Int, valid: Boolean) {
        if (valid) updateConfig(config.copy(lowTimeRemainingMinutes = value.coerceAtLeast(0)))
    }
    fun setDisableIdleWhileCharging(enabled: Boolean) = updateConfig(config.copy(disableIdleSleepInCharging = enabled))
    fun setDisableLidWhileCharging(enabled: Boolean) = updateConfig(config.copy(disableLidSleepInCharging = enabled))
    fun setImmediateLidSleep(enabled: Boolean) = updateConfig(config.copy(lidSleepImmediateOnClose = enabled))
    fun setNotifications(enabled: Boolean) = updateConfig(config.copy(notificationsEnabled = enabled))
    fun setUpdateChecks(enabled: Boolean) = updateConfig(config.copy(updateCheckEnabled = enabled))

    fun setLaunchAtLogin(enabled: Boolean) {
        val success = if (enabled) loginItem.enable() else loginItem.disable()
        if (success) updateConfig(config.copy(launchAtLogin = enabled))
        else statusMessage = strings.updateError
    }

    fun toggleIdleSleep() {
        engine.setIdleSleepAvailable(!state.idleSleepAvailable)
    }

    fun toggleLidSleep() {
        val makeAvailable = !state.lidSleepAvailable
        if (!makeAvailable) updateConfig(config.copy(lidSleepImmediateOnClose = false))
        engine.setLidSleepAvailable(makeAvailable)
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
            helperStatus = privileged.status()
            state = engine.currentState.copy(helperStatus = helperStatus)
            busy = false
            statusMessage = if (installed) strings.helperInstalled else strings.helperInstallError
            if (installed) dismissWelcome()
        }
    }

    fun uninstallHelper() {
        if (busy) return
        busy = true
        scope.launch {
            val removed = privileged.uninstall()
            helperStatus = HelperStatus.NOT_INSTALLED
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

    private fun updateConfig(newConfig: AppConfig) {
        val normalized = newConfig.copy(
            launchAtLogin = loginItem.isEnabled(),
        ).normalized()
        config = normalized
        configStore.save(normalized)
        engine.updateConfig(normalized)
        state = engine.currentState
    }

    private fun loginItemSync() {
        val actual = loginItem.isEnabled()
        if (actual != config.launchAtLogin) {
            config = config.copy(launchAtLogin = actual)
            configStore.save(config)
        }
    }

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
