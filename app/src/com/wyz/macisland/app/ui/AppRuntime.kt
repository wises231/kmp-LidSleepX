package com.wyz.macisland.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.wyz.macisland.app.platform.CoreGraphicsIdleSensor
import com.wyz.macisland.app.platform.FileAppLogger
import com.wyz.macisland.app.platform.GithubUpdateChecker
import com.wyz.macisland.app.platform.MacLidSensor
import com.wyz.macisland.app.platform.MacLoginItem
import com.wyz.macisland.app.platform.MacNotifier
import com.wyz.macisland.app.platform.MacPowerSource
import com.wyz.macisland.app.platform.MacPrivilegedOps
import com.wyz.macisland.app.platform.MacSleepController
import com.wyz.macisland.app.platform.GlobalHotKey
import com.wyz.macisland.app.platform.MacGlobalHotKey
import com.wyz.macisland.app.platform.MacIslandDragCompletion
import com.wyz.macisland.app.platform.MacShelfNative
import com.wyz.macisland.app.platform.MacSleepWatcher
import com.wyz.macisland.app.platform.MacScreenshotPreferenceStore
import com.wyz.macisland.app.platform.MarkupService
import com.wyz.macisland.app.platform.PmsetWakeLogReader
import com.wyz.macisland.app.platform.ScreenshotPreferenceStore
import com.wyz.macisland.app.platform.ScreenshotTakeoverController
import com.wyz.macisland.app.platform.ScreenshotWatcher
import com.wyz.macisland.app.platform.ShelfClipboard
import com.wyz.macisland.app.platform.ShelfFileOperations
import com.wyz.macisland.core.APP_VERSION
import com.wyz.macisland.core.AppConfig
import com.wyz.macisland.core.AppState
import com.wyz.macisland.core.ConfigStore
import com.wyz.macisland.core.Engine
import com.wyz.macisland.core.HelperStatus
import com.wyz.macisland.core.SUPPORTED_HIBERNATE_MODES
import com.wyz.macisland.core.ScreenshotInboxSnapshot
import com.wyz.macisland.core.ShelfDropAction
import com.wyz.macisland.core.ShelfDropTarget
import com.wyz.macisland.core.ShelfDragResult
import com.wyz.macisland.core.ShelfItem
import com.wyz.macisland.core.ShelfLayout
import com.wyz.macisland.core.ShelfState
import com.wyz.macisland.core.ShelfStore
import com.wyz.macisland.core.ShelfStoreState
import com.wyz.macisland.core.UpdatePolicy
import com.wyz.macisland.core.shelfDragResultFor
import java.awt.Component
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.MouseInfo
import java.awt.Rectangle
import java.awt.Toolkit
import java.nio.file.attribute.BasicFileAttributes
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JOptionPane
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

private data class ShelfDragRequest(val item: ShelfItem, val path: Path)

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
    private val shelfStore = ShelfStore()
    private val screenshotPreferenceStore = MacScreenshotPreferenceStore()
    private val shelfFileOperations = ShelfFileOperations()
    private val markupService = MarkupService()
    private val globalHotKey = MacGlobalHotKey { toggleShelf() }
    private val closeLock = Any()
    private var closed = false

    private var storedShelf = shelfStore.load()
    private var shelfItems = ShelfStore.normalizeItems(storedShelf.items)
    private var shelfVisible = false
    private var shelfRevealed = storedShelf.revealed
    private val screenshotTakeover = ScreenshotTakeoverController(
        store = screenshotPreferenceStore,
        directory = screenshotDirectory(),
        loadState = { storedShelf },
        saveState = { state -> shelfStore.save(state).also { storedShelf = it } },
    )
    private var shelfAutoHideJob: Job? = null
    private var menuBarCandidateAt = 0L
    private var shelfExitCandidateAt = 0L
    private var shelfMouseInside = false
    @Volatile private var shelfDragActive = false
    private val nextShelfDragToken = AtomicInteger(1)
    private val pendingShelfDrags = ConcurrentHashMap<Int, ShelfDragRequest>()
    private val pendingShelfDragCallbacks = ConcurrentHashMap<Int, MacIslandDragCompletion>()
    private var screenshotWatchers: List<ScreenshotWatcher> = emptyList()

    @Volatile
    private var shelfWindowComponent: Component? = null

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
            state = newState.copy(helperStatus = helperStatus, shelf = currentShelfState())
        },
    )

    init {
        logger.info("MacIsland $APP_VERSION starting")
        helperStatus = privileged.status()
        syncHibernateMode()
        engine.start()
        state = engine.currentState.copy(helperStatus = helperStatus, shelf = currentShelfState())
        initializeShelf()
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
        synchronized(closeLock) {
            if (closed) return
            closed = true
        }
        runCatching { engine.stop() }
        globalHotKey.close()
        screenshotWatchers.forEach(ScreenshotWatcher::close)
        screenshotWatchers = emptyList()
        restoreScreenshotPreferencesOnExit()
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

    fun setShelfEnabled(enabled: Boolean) {
        updateConfig(config.copy(shelfEnabled = enabled))
        if (enabled) {
            startScreenshotWatchers()
            showShelfTemporarily()
        } else {
            hideShelf()
            startScreenshotWatchers()
        }
    }

    fun setScreenshotInboxEnabled(enabled: Boolean) {
        if (enabled) {
            if (!activateScreenshotTakeover()) {
                statusMessage = strings.screenshotTakeoverError
                return
            }
            updateConfig(config.copy(screenshotInboxEnabled = true))
            startScreenshotWatchers()
        } else {
            if (!deactivateScreenshotTakeover()) {
                statusMessage = strings.screenshotRestoreError
                return
            }
            updateConfig(config.copy(screenshotInboxEnabled = false))
            startScreenshotWatchers()
        }
    }

    fun setShelfSoundsEnabled(enabled: Boolean) = updateConfig(config.copy(shelfSoundsEnabled = enabled))

    fun showShelf() {
        if (!config.shelfEnabled || MacShelfNative.isFullScreenSpaceActive()) return
        shelfRevealed = true
        shelfVisible = true
        shelfExitCandidateAt = 0L
        persistShelf()
        syncShelfState()
    }

    fun hideShelf() {
        shelfAutoHideJob?.cancel()
        shelfAutoHideJob = null
        shelfVisible = false
        shelfExitCandidateAt = 0L
        persistShelf()
        syncShelfState()
    }

    fun toggleShelf() {
        if (shelfVisible) hideShelf() else showShelf()
    }

    fun showShelfTemporarily(durationMillis: Long = 2_500L) {
        showShelf()
        shelfAutoHideJob?.cancel()
        shelfAutoHideJob = scope.launch {
            delay(durationMillis.coerceAtLeast(0L))
            hideShelf()
        }
    }

    fun clearShelf() {
        shelfItems = emptyList()
        persistShelf()
        syncShelfState()
    }

    fun openScreenshotDirectory() {
        runCatching {
            Files.createDirectories(screenshotDirectory())
            if (!Desktop.isDesktopSupported()) error("Desktop API is unavailable")
            Desktop.getDesktop().open(screenshotDirectory().toFile())
        }.onFailure {
            logger.warn("Cannot open screenshot directory", it)
            statusMessage = strings.screenshotDirectoryError
        }
    }

    fun attachShelfWindow(component: Component) {
        shelfWindowComponent = component
        scope.launch(Dispatchers.Swing) {
            delay(50L)
            MacShelfNative.configureFloatingPanel(component)
            updateShelfPointerMode()
        }
    }

    fun detachShelfWindow(component: Component) {
        if (shelfWindowComponent === component) {
            MacShelfNative.setPanelMousePassthrough(component, true)
            shelfWindowComponent = null
        }
    }

    fun setShelfMouseInside(inside: Boolean) {
        shelfMouseInside = inside
        if (inside) shelfExitCandidateAt = 0L
    }

    fun copyShelfItem(item: ShelfItem) {
        if (!ShelfClipboard.copy(Path.of(item.path))) statusMessage = strings.screenshotCopyError
    }

    fun openShelfItem(item: ShelfItem) {
        runCatching {
            if (!Desktop.isDesktopSupported()) error("Desktop API is unavailable")
            Desktop.getDesktop().open(Path.of(item.path).toFile())
        }.onFailure {
            logger.warn("Cannot open screenshot", it)
            statusMessage = strings.screenshotOpenError
        }
    }

    fun markupShelfItem(item: ShelfItem) {
        if (!markupService.edit(Path.of(item.path))) statusMessage = strings.screenshotMarkupError
    }

    fun removeShelfItem(item: ShelfItem) {
        val normalized = ShelfStore.canonicalPath(item.path)
        shelfItems = shelfItems.filterNot { ShelfStore.canonicalPath(it.path) == normalized }
        persistShelf()
        syncShelfState()
    }

    fun startShelfDrag(item: ShelfItem) {
        val component = shelfWindowComponent ?: return
        val path = Path.of(item.path)
        if (!Files.exists(path)) {
            removeShelfItem(item)
            return
        }

        val token = nextShelfDragToken.getAndIncrement()
        val completion = MacIslandDragCompletion { completedToken, operationMask ->
            scope.launch(Dispatchers.Swing) {
                pendingShelfDrags.remove(completedToken) ?: return@launch
                pendingShelfDragCallbacks.remove(completedToken)
                finishShelfDrag(item, path, operationMask)
            }
        }
        pendingShelfDrags[token] = ShelfDragRequest(item, path)
        pendingShelfDragCallbacks[token] = completion
        shelfDragActive = true
        val started = runCatching {
            MacShelfNative.startFileDrag(component, path, token, completion)
        }.getOrElse {
            logger.warn("Screenshot drag failed", it)
            false
        }
        if (!started) {
            pendingShelfDrags.remove(token)
            pendingShelfDragCallbacks.remove(token)
            shelfDragActive = false
            animateShelfDragReturn(path)
            logger.warn("Screenshot drag did not start")
        }
    }

    private fun finishShelfDrag(item: ShelfItem, path: Path, operationMask: Long) {
        when (shelfDragResultFor(operationMask)) {
            ShelfDragResult.COPY -> {
                shelfDragActive = false
                playShelfSound("pop")
            }
            ShelfDragResult.MOVE -> checkMoveCompleted(item, path)
            ShelfDragResult.DELETE -> {
                shelfDragActive = false
                removeShelfItem(item)
                playShelfSound("trash")
            }
            ShelfDragResult.CANCEL -> {
                shelfDragActive = false
                animateShelfDragReturn(path)
                playShelfSound("pop")
            }
        }
    }

    private fun checkMoveCompleted(item: ShelfItem, path: Path) {
        scope.launch {
            delay(600L)
            repeat(4) {
                if (!Files.exists(path)) {
                    withContext(Dispatchers.Swing) {
                        shelfDragActive = false
                        removeShelfItem(item)
                        playShelfSound("trash")
                    }
                    return@launch
                }
                delay(300L)
            }
            withContext(Dispatchers.Swing) {
                shelfDragActive = false
                animateShelfDragReturn(path)
                playShelfSound("pop")
            }
        }
    }

    private fun animateShelfDragReturn(path: Path) {
        shelfWindowComponent?.let { component ->
            MacShelfNative.animateImage(component, path, returning = true)
        }
    }

    fun handleShelfDrop(
        item: ShelfItem,
        action: ShelfDropAction,
        destination: Path?,
        target: ShelfDropTarget,
    ) {
        val succeeded = shelfFileOperations.perform(action, Path.of(item.path), destination, target)
        if (!succeeded) {
            if (action != ShelfDropAction.CANCEL) statusMessage = strings.screenshotMoveError
            return
        }
        if (action != ShelfDropAction.COPY_AND_KEEP) removeShelfItem(item)
        playShelfSound(if (action == ShelfDropAction.TRASH_AND_REMOVE) "trash" else "pop")
    }

    fun shelfScreenBounds(): Rectangle? {
        val point = MouseInfo.getPointerInfo()?.location ?: return null
        val environment = GraphicsEnvironment.getLocalGraphicsEnvironment()
        return environment.screenDevices.firstOrNull { it.defaultConfiguration.bounds.contains(point) }?.defaultConfiguration?.bounds
    }

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
            val dialog = FileDialog(null as Frame?, "Export MacIsland log", FileDialog.SAVE).apply {
                file = "MacIsland.log"
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

    private fun initializeShelf() {
        storedShelf = storedShelf.copy(items = shelfItems)
        runCatching { Files.createDirectories(screenshotDirectory()) }
        if (config.screenshotInboxEnabled) {
            if (!screenshotTakeover.enable()) {
                logger.warn("Cannot activate screenshot takeover")
            }
        } else if (storedShelf.preferenceSnapshot != null && !screenshotTakeover.restoreOnExit()) {
            logger.warn("Cannot restore screenshot preferences")
        }
        if (!globalHotKey.register()) logger.warn("Cannot register Control+Option+T")
        startScreenshotWatchers()
        scope.launch(Dispatchers.Swing) {
            while (isActive) {
                delay(100L)
                updateShelfVisibilityFromPointer()
            }
        }
    }

    private fun activateScreenshotTakeover(): Boolean {
        if (!screenshotTakeover.enable()) return false
        syncShelfState()
        return true
    }

    private fun deactivateScreenshotTakeover(): Boolean {
        if (!screenshotTakeover.disable()) return false
        syncShelfState()
        return true
    }

    private fun restoreScreenshotPreferencesOnExit() {
        screenshotTakeover.restoreOnExit()
    }

    private fun startScreenshotWatchers() {
        if (screenshotWatchers.isNotEmpty()) {
            screenshotWatchers.forEach(ScreenshotWatcher::close)
            screenshotWatchers = emptyList()
        }
        if (!config.shelfEnabled) return
        val watchers = mutableListOf<ScreenshotWatcher>()
        watchers += ScreenshotWatcher(
            directory = screenshotDirectory(),
            dedicatedDirectory = screenshotDirectory(),
            requireScreenCaptureAttribute = false,
            onNew = ::handleNewScreenshot,
            onChange = ::refreshShelfFiles,
        )
        if (screenshotTakeover.active) {
            watchers += ScreenshotWatcher(
                directory = ScreenshotWatcher.desktop(),
                dedicatedDirectory = screenshotDirectory(),
                requireScreenCaptureAttribute = true,
                onNew = ::handleNewScreenshot,
                onChange = ::refreshShelfFiles,
            )
        }
        watchers.forEach(ScreenshotWatcher::start)
        screenshotWatchers = watchers
    }

    private fun handleNewScreenshot(path: Path) {
        scope.launch(Dispatchers.Swing) {
            addShelfItem(path)
        }
    }

    private fun addShelfItem(path: Path) {
        if (!config.shelfEnabled || !Files.exists(path)) return
        val normalized = ShelfStore.canonicalPath(path.toString())
        val item = ShelfItem(normalized, System.currentTimeMillis() / 1_000L)
        val updated = (shelfItems + item).distinctBy { ShelfStore.canonicalPath(it.path) }.takeLast(ShelfStore.MAX_ITEMS)
        if (updated.map(ShelfItem::path) == shelfItems.map(ShelfItem::path)) return
        shelfItems = updated
        persistShelf()
        showShelfTemporarily()
        scope.launch(Dispatchers.Swing) {
            delay(120L)
            shelfWindowComponent?.let { MacShelfNative.animateImage(it, path, returning = false) }
        }
        playShelfSound("pop")
    }

    private fun refreshShelfFiles() {
        scope.launch(Dispatchers.Swing) {
            val existing = shelfItems.filter { Files.exists(Path.of(it.path)) }.takeLast(ShelfStore.MAX_ITEMS)
            if (existing != shelfItems) {
                shelfItems = existing
                persistShelf()
                syncShelfState()
            }
        }
    }

    private fun persistShelf() {
        storedShelf = shelfStore.save(
            storedShelf.copy(
                items = shelfItems,
                revealed = shelfRevealed,
                preferenceSnapshot = storedShelf.preferenceSnapshot,
            ),
        )
    }

    private fun currentShelfState(): ShelfState = ShelfState(
        items = shelfItems,
        visible = shelfVisible,
        revealed = shelfRevealed,
        takeoverActive = screenshotTakeover.active,
    )

    private fun syncShelfState() {
        state = state.copy(shelf = currentShelfState())
    }

    private fun updateShelfVisibilityFromPointer() {
        if (!config.shelfEnabled || MacShelfNative.isFullScreenSpaceActive()) {
            MacShelfNative.setPanelMousePassthrough(shelfWindowComponent ?: return, true)
            if (shelfVisible) hideShelf()
            return
        }
        val pointer = MouseInfo.getPointerInfo()?.location ?: return
        val environment = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val screen = environment.screenDevices.firstOrNull { it.defaultConfiguration.bounds.contains(pointer) } ?: return
        val insets = Toolkit.getDefaultToolkit().getScreenInsets(screen.defaultConfiguration)
        val menuBarBottom = screen.defaultConfiguration.bounds.y + insets.top + 2
        val inMenuBar = pointer.y <= menuBarBottom
        val overCard = isPointerOverShelfCard(pointer)
        shelfWindowComponent?.let { MacShelfNative.setPanelMousePassthrough(it, !overCard) }
        val inShelf = overCard || shelfMouseInside || shelfDragActive
        if (inMenuBar || inShelf) {
            menuBarCandidateAt = if (menuBarCandidateAt == 0L) System.currentTimeMillis() else menuBarCandidateAt
            shelfExitCandidateAt = 0L
            if (!shelfVisible && System.currentTimeMillis() - menuBarCandidateAt >= 250L) {
                shelfAutoHideJob?.cancel()
                showShelf()
            }
        } else {
            menuBarCandidateAt = 0L
            if (shelfVisible && !shelfDragActive) {
                shelfExitCandidateAt = if (shelfExitCandidateAt == 0L) System.currentTimeMillis() else shelfExitCandidateAt
                if (System.currentTimeMillis() - shelfExitCandidateAt >= 500L) hideShelf()
            }
        }
    }

    private fun updateShelfPointerMode() {
        val component = shelfWindowComponent ?: return
        val pointer = MouseInfo.getPointerInfo()?.location ?: return
        MacShelfNative.setPanelMousePassthrough(component, !isPointerOverShelfCard(pointer))
    }

    private fun isPointerOverShelfCard(pointer: java.awt.Point): Boolean {
        val component = shelfWindowComponent ?: return false
        val bounds = runCatching { Rectangle(component.locationOnScreen, component.size) }.getOrNull() ?: return false
        if (!bounds.contains(pointer)) return false
        val count = shelfItems.size.coerceAtMost(ShelfLayout.capacity(bounds.width.toFloat()))
        if (count == 0) return false
        val x = (pointer.x - bounds.x).toFloat()
        val y = (pointer.y - bounds.y).toFloat()
        return ShelfLayout.cardIndexAt(x, y, count, bounds.width.toFloat()) != null
    }

    private fun shelfWindowBounds(): Rectangle? {
        val component = shelfWindowComponent ?: return null
        return runCatching {
            Rectangle(component.locationOnScreen, component.size)
        }.getOrNull()
    }

    private fun screenshotDirectory(): Path = ScreenshotWatcher.dedicated()

    private fun playShelfSound(name: String) {
        if (config.shelfSoundsEnabled) MacShelfNative.playSound(name)
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
        fun formatBattery(battery: com.wyz.macisland.core.BatteryStatus, strings: Strings): String {
            val stateText = when {
                battery.state == com.wyz.macisland.core.PowerState.DISCHARGING -> strings.discharging
                battery.state == com.wyz.macisland.core.PowerState.CHARGING -> strings.charging
                battery.state == com.wyz.macisland.core.PowerState.CHARGED -> strings.charged
                battery.state == com.wyz.macisland.core.PowerState.AC_POWER -> "AC"
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
