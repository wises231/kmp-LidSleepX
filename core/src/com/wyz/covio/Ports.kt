package com.wyz.covio.core

fun interface StateListener {
    fun onState(state: AppState)
}

interface PowerSource {
    fun read(): BatteryStatus?
    fun subscribe(listener: () -> Unit)
    fun stop()
}

interface LidSensor {
    fun read(): LidState
    fun subscribe(listener: (LidState) -> Unit)
    fun stop()
}

interface IdleSensor {
    fun idleSeconds(): Long
}

interface SleepController {
    fun sleep()
    fun displaySleep()
    fun setIdleSleepPrevented(prevented: Boolean)
    fun systemIdleSleepTimeoutSeconds(): Long?
}

interface SleepWatcher {
    fun subscribe(listener: (SleepEvent) -> Unit)
    fun stop()
}

fun interface WakeLogReader {
    fun read(): WakeLogSnapshot?
}

interface PrivilegedOps {
    fun isInstalled(): Boolean
    fun status(): HelperStatus
    fun helperVersion(): String?
    fun install(): Boolean
    fun uninstall(): Boolean
    fun setDisableSleep(disabled: Boolean): Boolean
    fun hibernateMode(): Int?
    fun setHibernateMode(mode: Int): Boolean
}

interface LoginItem {
    fun isEnabled(): Boolean
    fun enable(): Boolean
    fun disable(): Boolean
}

interface UpdateChecker {
    fun latestRelease(): UpdateInfo?
}

interface Notifier {
    fun notify(title: String, subtitle: String = "", message: String = "", releaseUrl: String? = null)
}

interface AppLogger {
    fun info(message: String)
    fun warn(message: String, error: Throwable? = null)
    fun error(message: String, error: Throwable? = null)
    fun logFile(): String
}

fun interface ConfigChanged {
    fun onChanged(config: AppConfig)
}
