package com.wyz.covio.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.wyz.covio.core.APP_VERSION
import com.wyz.covio.core.HelperStatus
import com.wyz.covio.core.SUPPORTED_HIBERNATE_MODES
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class SettingsSection { OVERVIEW, BATTERY, LID, IDLE, SHELF, GENERAL }

@Composable
fun SettingsWindow(runtime: AppRuntime) {
    if (!runtime.settingsVisible) return
    val windowState = rememberWindowState(width = 760.dp, height = 560.dp, position = WindowPosition(Alignment.Center))
    Window(
        onCloseRequest = runtime::hideSettings,
        state = windowState,
        title = runtime.strings.appName,
    ) {
        var section by remember { mutableStateOf(SettingsSection.OVERVIEW) }
        MaterialTheme {
            Surface(color = Color(0xFFF5F5F7)) {
                Row(Modifier.fillMaxSize()) {
                    Sidebar(runtime, section) { section = it }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.White)
                            .padding(28.dp),
                    ) {
                        when (section) {
                            SettingsSection.OVERVIEW -> OverviewPage(runtime)
                            SettingsSection.BATTERY -> BatteryPage(runtime)
                            SettingsSection.LID -> LidPage(runtime)
                            SettingsSection.IDLE -> IdlePage(runtime)
                            SettingsSection.SHELF -> ShelfPage(runtime)
                            SettingsSection.GENERAL -> GeneralPage(runtime)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Sidebar(runtime: AppRuntime, selected: SettingsSection, onSelect: (SettingsSection) -> Unit) {
    Column(
        Modifier
            .width(178.dp)
            .fillMaxHeight()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(runtime.strings.appName, fontSize = 20.sp, modifier = Modifier.padding(bottom = 18.dp))
        SidebarItem(runtime.strings.overview, selected == SettingsSection.OVERVIEW) { onSelect(SettingsSection.OVERVIEW) }
        SidebarItem(runtime.strings.battery(""), selected == SettingsSection.BATTERY) { onSelect(SettingsSection.BATTERY) }
        SidebarItem(runtime.strings.lid, selected == SettingsSection.LID) { onSelect(SettingsSection.LID) }
        SidebarItem(runtime.strings.idle, selected == SettingsSection.IDLE) { onSelect(SettingsSection.IDLE) }
        SidebarItem(runtime.strings.shelf, selected == SettingsSection.SHELF) { onSelect(SettingsSection.SHELF) }
        SidebarItem(runtime.strings.general, selected == SettingsSection.GENERAL) { onSelect(SettingsSection.GENERAL) }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = runtime::viewLog) { Text(runtime.strings.viewLog) }
        TextButton(onClick = runtime::exportLog) { Text(runtime.strings.exportLog) }
    }
}

@Composable
private fun SidebarItem(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        color = if (selected) Color.White else Color(0xFF1D1D1F),
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) Color(0xFF1D1D1F) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    )
}

@Composable
private fun OverviewPage(runtime: AppRuntime) {
    PageColumn(runtime.strings.overview) {
        val battery = runtime.state.battery?.let { AppRuntime.formatBattery(it, runtime.strings) } ?: runtime.strings.unknown
        StatusRow(runtime.strings.battery(battery))
        StatusRow(helperText(runtime))
        StatusRow("${runtime.strings.version}: $APP_VERSION")
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = runtime::sleepNow) { Text(runtime.strings.sleepNow) }
            Button(onClick = runtime::displaySleepNow) { Text(runtime.strings.displaySleepNow) }
        }
        Spacer(Modifier.height(18.dp))
        SettingSwitch(runtime.strings.enabled, runtime.config.enabled) { runtime.setEnabled(it) }
        if (runtime.helperStatus == HelperStatus.NOT_INSTALLED || runtime.helperStatus == HelperStatus.OUTDATED) {
            Button(onClick = runtime::installHelper, enabled = !runtime.busy) { Text(runtime.strings.installHelper) }
        }
        if (runtime.helperStatus == HelperStatus.INSTALLED) {
            TextButton(onClick = runtime::uninstallHelper, enabled = !runtime.busy) { Text(runtime.strings.uninstallHelper) }
        }
    }
}

@Composable
private fun BatteryPage(runtime: AppRuntime) {
    PageColumn(runtime.strings.battery("")) {
        SettingSwitch(runtime.strings.lowBatterySleep, runtime.config.lowBatteryCapacitySleep) {
            runtime.setLowBatterySleep(it)
        }
        NumberField(runtime.strings.lowBatteryCapacity, runtime.config.lowBatteryCapacity) {
            runtime.setLowBatteryCapacity(it, it in 0..100)
        }
        NumberField(runtime.strings.lowRemainingTime, runtime.config.lowTimeRemainingMinutes) {
            runtime.setLowTimeRemaining(it, it >= 0)
        }
        Text(runtime.config.lowTimeRemainingMinutes.toString() + " " + runtime.strings.minutes, color = Color.Gray)
    }
}

@Composable
private fun LidPage(runtime: AppRuntime) {
    val controlsEnabled = runtime.helperStatus == HelperStatus.INSTALLED
    PageColumn(runtime.strings.lid) {
        SettingSwitch(runtime.strings.immediateSleepOnLid, runtime.config.lidSleepImmediateOnClose) {
            runtime.setImmediateLidSleep(it)
        }
        SettingSwitch(runtime.strings.preventLidSleep, !runtime.state.lidSleepAvailable, controlsEnabled) {
            runtime.toggleLidSleep()
        }
        SettingSwitch(runtime.strings.disableLidOnPower, runtime.config.disableLidSleepInCharging, controlsEnabled) {
            runtime.setDisableLidWhileCharging(it)
        }
        SettingSwitch(runtime.strings.disableLidOnBattery, runtime.config.disableLidSleepOnBattery, controlsEnabled) {
            runtime.setDisableLidOnBattery(it)
        }
        Text(runtime.strings.batteryLidWarning, color = Color(0xFFB25000))
        if (!controlsEnabled) {
            Text(runtime.strings.helperOutdated, color = Color(0xFFB25000), modifier = Modifier.padding(top = 10.dp))
        }
    }
}

@Composable
private fun IdlePage(runtime: AppRuntime) {
    PageColumn(runtime.strings.idle) {
        SettingSwitch(runtime.strings.preventIdleSleep, !runtime.state.idleSleepAvailable) {
            runtime.toggleIdleSleep()
        }
        SettingSwitch(runtime.strings.disableIdleOnPower, runtime.config.disableIdleSleepInCharging) {
            runtime.setDisableIdleWhileCharging(it)
        }
        Spacer(Modifier.height(12.dp))
        Text(runtime.strings.cancelAfter)
        CancellationOptions(runtime.language, runtime::scheduleCancelIdle)
        runtime.state.idleCancelRemainingSeconds?.let {
            Text(runtime.strings.idleRemaining(formatDuration(it, runtime.language)), color = Color.Gray)
        }
    }
}

@Composable
private fun ShelfPage(runtime: AppRuntime) {
    PageColumn(runtime.strings.shelf) {
        SettingSwitch(runtime.strings.shelfEnabled, runtime.config.shelfEnabled) {
            runtime.setShelfEnabled(it)
        }
        SettingSwitch(runtime.strings.screenshotInbox, runtime.config.screenshotInboxEnabled) {
            runtime.setScreenshotInboxEnabled(it)
        }
        Text(runtime.strings.screenshotInboxHelp, color = Color(0xFF6E6E73))
        Spacer(Modifier.height(12.dp))
        SettingSwitch(runtime.strings.shelfSounds, runtime.config.shelfSoundsEnabled) {
            runtime.setShelfSoundsEnabled(it)
        }
        Spacer(Modifier.height(12.dp))
        Text(runtime.strings.shelfStatus)
        StatusRow(
            if (runtime.state.shelf.takeoverActive) runtime.strings.screenshotTakeoverOn
            else runtime.strings.screenshotTakeoverOff,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = runtime::showShelf,
                enabled = runtime.config.shelfEnabled,
            ) { Text(runtime.strings.showShelf) }
            TextButton(onClick = runtime::openScreenshotDirectory) {
                Text(runtime.strings.openScreenshotDirectory)
            }
        }
        TextButton(onClick = runtime::clearShelf) { Text(runtime.strings.clearShelf) }
        runtime.statusMessage?.let { Text(it, color = Color(0xFF0A7A32), modifier = Modifier.padding(top = 14.dp)) }
    }
}

@Composable
private fun GeneralPage(runtime: AppRuntime) {
    PageColumn(runtime.strings.general) {
        SettingSwitch(runtime.strings.launchAtLogin, runtime.config.launchAtLogin) { runtime.setLaunchAtLogin(it) }
        SettingSwitch(runtime.strings.notifications, runtime.config.notificationsEnabled) { runtime.setNotifications(it) }
        SettingSwitch(runtime.strings.updateChecks, runtime.config.updateCheckEnabled) { runtime.setUpdateChecks(it) }
        Button(onClick = { runtime.checkForUpdates(manual = true) }, enabled = !runtime.busy) {
            Text(runtime.strings.checkUpdates)
        }
        Spacer(Modifier.height(12.dp))
        SettingSwitch(runtime.strings.darkWakeAwareness, runtime.config.darkWakeAwarenessEnabled) {
            runtime.setDarkWakeAwareness(it)
        }
        DarkWakeStatus(runtime)
        Spacer(Modifier.height(12.dp))
        Text(runtime.strings.sleepMode)
        SleepModeOptions(runtime)
        Spacer(Modifier.height(12.dp))
        Text(runtime.strings.language)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { runtime.changeLanguage(AppLanguage.ENGLISH) }) { Text("English") }
            TextButton(onClick = { runtime.changeLanguage(AppLanguage.SIMPLIFIED_CHINESE) }) { Text("简体中文") }
        }
        Spacer(Modifier.height(18.dp))
        TextButton(onClick = runtime::clearConfig, enabled = !runtime.busy) {
            Text(runtime.strings.clearConfig)
        }
        runtime.statusMessage?.let { Text(it, color = Color(0xFF0A7A32), modifier = Modifier.padding(top = 14.dp)) }
    }
}

@Composable
private fun DarkWakeStatus(runtime: AppRuntime) {
    val status = runtime.state.darkWake
    Text(runtime.strings.darkWakeStatus, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
    when {
        status.scanning -> StatusRow(runtime.strings.darkWakeScanning)
        !status.available -> StatusRow(runtime.strings.darkWakeUnavailable)
        else -> {
            val last = status.lastAtEpochSeconds?.let { formatWakeTime(it) } ?: runtime.strings.unknown
            StatusRow("${runtime.strings.darkWakeLast}: $last")
            StatusRow("${runtime.strings.darkWakeReason}: ${status.lastReason ?: runtime.strings.unknown}")
            StatusRow("${runtime.strings.darkWakeCount24h}: ${status.count24h}")
        }
    }
}

private fun formatWakeTime(epochSeconds: Long): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    return formatter.withZone(ZoneId.systemDefault()).format(Instant.ofEpochSecond(epochSeconds))
}

@Composable
private fun SleepModeOptions(runtime: AppRuntime) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SUPPORTED_HIBERNATE_MODES.sorted().forEach { mode ->
            val selected = runtime.config.hibernateMode == mode
            TextButton(onClick = { runtime.setSleepMode(mode) }, enabled = !runtime.busy) {
                Text((if (selected) "● " else "○ ") + sleepModeLabel(runtime.strings, mode))
            }
        }
    }
}

private fun sleepModeLabel(strings: Strings, mode: Int): String = when (mode) {
    0 -> strings.sleepMode0
    3 -> strings.sleepMode3
    else -> strings.sleepMode25
}

@Composable
private fun PageColumn(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(title, fontSize = 26.sp, modifier = Modifier.padding(bottom = 22.dp))
        content()
    }
}

@Composable
private fun StatusRow(text: String) {
    Text(text, modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
private fun SettingSwitch(
    text: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun NumberField(label: String, initial: Int, onChange: (Int) -> Unit) {
    var text by remember(initial) { mutableStateOf(initial.toString()) }
    val valid = text.toIntOrNull() != null
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            it.toIntOrNull()?.let(onChange)
        },
        label = { Text(label) },
        isError = !valid,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    )
}

@Composable
private fun CancellationOptions(language: AppLanguage, onSelect: (Long) -> Unit) {
    val options = listOf(300L, 600L, 1800L, 3600L, 7200L, 10800L, 43200L, 86400L)
    Column {
        options.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { seconds ->
                    TextButton(onClick = { onSelect(seconds) }) { Text(formatDuration(seconds, language)) }
                }
            }
        }
    }
}

private fun helperText(runtime: AppRuntime): String = when (runtime.helperStatus) {
    HelperStatus.INSTALLED -> runtime.strings.helperInstalled
    HelperStatus.OUTDATED -> runtime.strings.helperOutdated
    HelperStatus.ERROR -> runtime.strings.helperOutdated
    HelperStatus.NOT_INSTALLED -> runtime.strings.helperNotInstalled
}
