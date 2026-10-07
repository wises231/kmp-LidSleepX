package com.wyz.lidsleepx.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuScope
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.wyz.lidsleepx.app.helper.HelperEntrypoint
import com.wyz.lidsleepx.app.ui.AppLanguage
import com.wyz.lidsleepx.app.ui.AppRuntime
import com.wyz.lidsleepx.app.ui.SettingsWindow
import com.wyz.lidsleepx.app.ui.formatDuration
import com.wyz.lidsleepx.core.APP_VERSION
import com.wyz.lidsleepx.core.HelperStatus
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.contains("--helper")) {
        exitProcess(HelperEntrypoint.run())
    }
    val runtime = AppRuntime()
    try {
        application {
            Tray(
                icon = MoonIcon,
                state = androidx.compose.ui.window.rememberTrayState(),
                tooltip = "LidSleepX",
                onAction = runtime::showSettings,
                menu = { TrayMenu(runtime, ::exitApplication) },
            )
            SettingsWindow(runtime)
            WelcomeWindow(runtime)
            AboutWindow(runtime)
        }
    } finally {
        runtime.close()
    }
}

@Composable
private fun MenuScope.TrayMenu(runtime: AppRuntime, exitApplication: () -> Unit) {
    Menu(runtime.strings.appName, enabled = false) {}
    Item(
        text = runtime.state.battery?.let { AppRuntime.formatBattery(it, runtime.strings) } ?: runtime.strings.unknown,
        enabled = false,
        onClick = {},
    )
    CheckboxItem(
        text = runtime.strings.enabled,
        checked = runtime.config.enabled,
        onCheckedChange = { runtime.setEnabled(it) },
    )
    Separator()
    Item(runtime.strings.sleepNow) { runtime.sleepNow() }
    Item(runtime.strings.displaySleepNow) { runtime.displaySleepNow() }
    CheckboxItem(
        text = runtime.strings.preventIdleSleep,
        checked = !runtime.state.idleSleepAvailable,
        onCheckedChange = { runtime.toggleIdleSleep() },
    )
    CheckboxItem(
        text = runtime.strings.preventLidSleep,
        checked = !runtime.state.lidSleepAvailable,
        onCheckedChange = { runtime.toggleLidSleep() },
    )
    Menu(runtime.strings.cancelAfter) {
        CANCELLATION_VALUES.forEach { seconds ->
            Item(formatDuration(seconds, runtime.language)) { runtime.scheduleCancelIdle(seconds) }
        }
    }
    Menu(runtime.strings.cancelAfter + " - " + runtime.strings.preventLidSleep) {
        CANCELLATION_VALUES.forEach { seconds ->
            Item(formatDuration(seconds, runtime.language)) { runtime.scheduleCancelLid(seconds) }
        }
    }
    Separator()
    Item(runtime.strings.settings) { runtime.showSettings() }
    Menu(runtime.strings.language) {
        CheckboxItem(
            text = "English",
            checked = runtime.language == AppLanguage.ENGLISH,
            onCheckedChange = { if (it) runtime.changeLanguage(AppLanguage.ENGLISH) },
        )
        CheckboxItem(
            text = "简体中文",
            checked = runtime.language == AppLanguage.SIMPLIFIED_CHINESE,
            onCheckedChange = { if (it) runtime.changeLanguage(AppLanguage.SIMPLIFIED_CHINESE) },
        )
    }
    Item(runtime.strings.checkUpdates) { runtime.checkForUpdates(manual = true) }
    Separator()
    when (runtime.helperStatus) {
        HelperStatus.INSTALLED -> Item(runtime.strings.uninstallHelper) { runtime.uninstallHelper() }
        HelperStatus.OUTDATED -> {
            Item(runtime.strings.helperOutdated, enabled = false) {}
            Item(runtime.strings.installHelper) { runtime.installHelper() }
        }
        HelperStatus.ERROR -> Item(runtime.strings.installHelper) { runtime.installHelper() }
        HelperStatus.NOT_INSTALLED -> Item(runtime.strings.installHelper) { runtime.installHelper() }
    }
    Item(runtime.strings.viewLog) { runtime.viewLog() }
    Item(runtime.strings.exportLog) { runtime.exportLog() }
    Item(runtime.strings.about) { runtime.showAbout() }
    Separator()
    Item(runtime.strings.quit) {
        runtime.close()
        exitApplication()
    }
}

@Composable
private fun WelcomeWindow(runtime: AppRuntime) {
    if (!runtime.welcomeVisible) return
    val windowState = rememberWindowState(
        width = 470.dp,
        height = 280.dp,
        position = WindowPosition(androidx.compose.ui.Alignment.Center),
    )
    Window(
        onCloseRequest = runtime::dismissWelcome,
        state = windowState,
        title = runtime.strings.welcomeTitle,
        resizable = false,
        alwaysOnTop = true,
    ) {
        MaterialTheme {
            Column(
                modifier = androidx.compose.ui.Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(runtime.strings.welcomeTitle)
                Text(runtime.strings.welcomeBody)
                Spacer(androidx.compose.ui.Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = runtime::installHelper, enabled = !runtime.busy) {
                        Text(runtime.strings.install)
                    }
                    TextButton(onClick = runtime::dismissWelcome) {
                        Text(runtime.strings.later)
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutWindow(runtime: AppRuntime) {
    if (!runtime.aboutVisible) return
    val windowState = rememberWindowState(
        width = 430.dp,
        height = 230.dp,
        position = WindowPosition(androidx.compose.ui.Alignment.Center),
    )
    Window(
        onCloseRequest = runtime::hideAbout,
        state = windowState,
        title = runtime.strings.appName,
        resizable = false,
        alwaysOnTop = true,
    ) {
        MaterialTheme {
            Column(
                modifier = androidx.compose.ui.Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("${runtime.strings.appName} ${runtime.strings.version} $APP_VERSION")
                Text(runtime.strings.aboutBody)
                Spacer(androidx.compose.ui.Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.End, modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                    TextButton(onClick = runtime::hideAbout) {
                        Text(runtime.strings.close)
                    }
                }
            }
        }
    }
}

private val CANCELLATION_VALUES = listOf(300L, 600L, 1800L, 3600L, 7200L, 10800L, 43200L, 86400L)

private object MoonIcon : Painter() {
    override val intrinsicSize: Size = Size(22f, 22f)

    override fun DrawScope.onDraw() {
        val outer = Path().apply {
            addOval(Rect(Offset(2f, 2f), Size(18f, 18f)))
        }
        val inner = Path().apply {
            addOval(Rect(Offset(8f, 1f), Size(18f, 18f)))
        }
        val crescent = Path().apply {
            op(outer, inner, PathOperation.Difference)
        }
        drawPath(crescent, Color.White)
    }
}
