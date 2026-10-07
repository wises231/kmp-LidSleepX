package com.wyz.lidsleepx.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EngineTest {
    @Test
    fun `low battery capacity triggers sleep only while discharging`() {
        val fixture = Fixture(AppConfig(lowBatteryCapacity = 6))
        fixture.power.value = BatteryStatus(6, PowerState.DISCHARGING, null)
        fixture.engine.start()
        assertEquals(1, fixture.sleep.sleepCalls)
        fixture.power.value = BatteryStatus(5, PowerState.CHARGING, null)
        fixture.power.emit()
        assertEquals(1, fixture.sleep.sleepCalls)
    }

    @Test
    fun `low remaining time triggers sleep`() {
        val fixture = Fixture(AppConfig(lowTimeRemainingMinutes = 10))
        fixture.power.value = BatteryStatus(50, PowerState.DISCHARGING, 600)
        fixture.engine.start()
        assertEquals(1, fixture.sleep.sleepCalls)
    }

    @Test
    fun `charging rules prevent idle and lid sleep then restore on battery`() {
        val fixture = Fixture(
            AppConfig(
                disableIdleSleepInCharging = true,
                disableLidSleepInCharging = true,
                lidSleepImmediateOnClose = false,
            ),
        )
        fixture.privileged.installed = true
        fixture.power.value = BatteryStatus(80, PowerState.CHARGING, null)
        fixture.engine.start()
        assertFalse(fixture.engine.currentState.idleSleepAvailable)
        assertFalse(fixture.engine.currentState.lidSleepAvailable)

        fixture.power.value = BatteryStatus(75, PowerState.DISCHARGING, 3_600)
        fixture.power.emit()
        assertTrue(fixture.engine.currentState.idleSleepAvailable)
        assertTrue(fixture.engine.currentState.lidSleepAvailable)
    }

    @Test
    fun `lid charging policy is skipped when the helper is not installed`() {
        val fixture = Fixture(
            AppConfig(
                disableIdleSleepInCharging = true,
                disableLidSleepInCharging = true,
                lidSleepImmediateOnClose = false,
            ),
        )
        fixture.privileged.installed = false
        fixture.power.value = BatteryStatus(80, PowerState.CHARGING, null)
        fixture.engine.start()
        assertFalse(fixture.engine.currentState.idleSleepAvailable)
        assertTrue(fixture.engine.currentState.lidSleepAvailable)
        assertTrue(fixture.privileged.disableSleepValues.isEmpty())
    }

    @Test
    fun `start does not touch disablesleep when the helper is not installed`() {
        val fixture = Fixture()
        fixture.power.value = BatteryStatus(90, PowerState.DISCHARGING, null)
        fixture.privileged.installed = false
        fixture.engine.start()
        assertTrue(fixture.privileged.disableSleepValues.isEmpty())
    }

    @Test
    fun `lid close sleeps immediately when available`() {
        val fixture = Fixture(AppConfig(lidSleepImmediateOnClose = true))
        fixture.power.value = BatteryStatus(80, PowerState.DISCHARGING, null)
        fixture.engine.start()
        fixture.lid.state = LidState.CLOSED
        fixture.lid.emit(LidState.CLOSED)
        assertEquals(1, fixture.sleep.sleepCalls)
    }

    @Test
    fun `lid close only turns display off while sleep is prevented`() {
        val fixture = Fixture(AppConfig(lidSleepImmediateOnClose = false))
        fixture.power.value = BatteryStatus(80, PowerState.CHARGING, null)
        fixture.engine.start()
        fixture.engine.setLidSleepAvailable(false)
        fixture.lid.emit(LidState.CLOSED)
        assertEquals(1, fixture.sleep.displayCalls)
        assertEquals(0, fixture.sleep.sleepCalls)
    }

    @Test
    fun `idle compensation sleeps after system timeout`() {
        val fixture = Fixture(AppConfig(lidSleepImmediateOnClose = false))
        fixture.power.value = BatteryStatus(80, PowerState.CHARGING, null)
        fixture.engine.start()
        fixture.engine.setLidSleepAvailable(false)
        fixture.sleep.timeoutSeconds = 60
        fixture.idle.seconds = 61
        fixture.engine.tick()
        assertEquals(1, fixture.sleep.sleepCalls)
    }

    @Test
    fun `cancel timer restores idle availability`() {
        var now = 1_000L
        val fixture = Fixture(clock = { now })
        fixture.engine.scheduleCancelIdle(300)
        assertFalse(fixture.engine.currentState.idleSleepAvailable)
        now += 301_000L
        fixture.engine.tick(now)
        assertTrue(fixture.engine.currentState.idleSleepAvailable)
    }

    @Test
    fun `sleep now temporarily restores restrictions and wake restores them`() {
        val fixture = Fixture()
        fixture.power.value = BatteryStatus(90, PowerState.CHARGING, null)
        fixture.engine.start()
        fixture.engine.setIdleSleepAvailable(false)
        fixture.engine.setLidSleepAvailable(false)
        assertTrue(fixture.engine.currentState.idleSleepAvailable.not())

        fixture.engine.sleepNow()
        assertEquals(1, fixture.sleep.sleepCalls)
        assertTrue(fixture.engine.currentState.idleSleepAvailable)
        assertTrue(fixture.engine.currentState.lidSleepAvailable)

        fixture.watcher.wake()
        assertFalse(fixture.engine.currentState.idleSleepAvailable)
        assertFalse(fixture.engine.currentState.lidSleepAvailable)
    }

    @Test
    fun `cancel timer restores lid availability`() {
        var now = 5_000L
        val fixture = Fixture(clock = { now })
        fixture.privileged.installed = true
        fixture.engine.scheduleCancelLid(600)
        assertFalse(fixture.engine.currentState.lidSleepAvailable)
        now += 601_000L
        fixture.engine.tick(now)
        assertTrue(fixture.engine.currentState.lidSleepAvailable)
    }

    @Test
    fun `stop restores sleep availability and clears disablesleep`() {
        val fixture = Fixture(AppConfig(lidSleepImmediateOnClose = false))
        fixture.privileged.installed = true
        fixture.power.value = BatteryStatus(90, PowerState.CHARGING, null)
        fixture.engine.start()
        fixture.engine.setIdleSleepAvailable(false)
        fixture.engine.setLidSleepAvailable(false)
        assertTrue(fixture.privileged.disableSleepValues.contains(true))

        fixture.engine.stop()
        assertTrue(fixture.engine.currentState.idleSleepAvailable)
        assertTrue(fixture.engine.currentState.lidSleepAvailable)
        assertFalse(fixture.sleep.prevented)
        assertEquals(false, fixture.privileged.disableSleepValues.last())
    }

    @Test
    fun `start removes stale disablesleep flag when no charging policy needs it`() {
        val fixture = Fixture()
        fixture.power.value = BatteryStatus(90, PowerState.DISCHARGING, null)
        fixture.privileged.installed = true
        fixture.engine.start()
        assertTrue(fixture.privileged.disableSleepValues.contains(false))
    }
}

private class Fixture(
    config: AppConfig = AppConfig(),
    clock: () -> Long = System::currentTimeMillis,
) {
    val power = FakePower()
    val lid = FakeLid()
    val idle = FakeIdle()
    val sleep = FakeSleepController()
    val watcher = FakeSleepWatcher()
    val privileged = FakePrivilegedOps()
    val logger = MemoryLogger()
    val engine = Engine(
        initialConfig = config,
        power = power,
        lid = lid,
        idle = idle,
        sleepController = sleep,
        sleepWatcher = watcher,
        privileged = privileged,
        logger = logger,
        clock = clock,
    )
}

private class FakePower(var value: BatteryStatus? = null) : PowerSource {
    private var listener: (() -> Unit)? = null
    override fun read() = value
    override fun subscribe(listener: () -> Unit) { this.listener = listener }
    override fun stop() { listener = null }
    fun emit() { listener?.invoke() }
}

private class FakeLid(var state: LidState = LidState.OPEN) : LidSensor {
    private var listener: ((LidState) -> Unit)? = null
    override fun read() = state
    override fun subscribe(listener: (LidState) -> Unit) { this.listener = listener }
    override fun stop() { listener = null }
    fun emit(value: LidState) { state = value; listener?.invoke(value) }
}

private class FakeIdle(var seconds: Long = 0L) : IdleSensor {
    override fun idleSeconds() = seconds
}

private class FakeSleepController : SleepController {
    var sleepCalls = 0
    var displayCalls = 0
    var prevented = false
    var timeoutSeconds: Long? = 600
    override fun sleep() { sleepCalls++ }
    override fun displaySleep() { displayCalls++ }
    override fun setIdleSleepPrevented(prevented: Boolean) { this.prevented = prevented }
    override fun systemIdleSleepTimeoutSeconds() = timeoutSeconds
}

private class FakeSleepWatcher : SleepWatcher {
    private var wake: (() -> Unit)? = null
    override fun subscribe(onWillSleep: () -> Unit, onDidWake: () -> Unit) { wake = onDidWake }
    override fun stop() { wake = null }
    fun wake() { wake?.invoke() }
}

private class FakePrivilegedOps : PrivilegedOps {
    var installed = false
    val disableSleepValues = mutableListOf<Boolean>()
    override fun isInstalled() = installed
    override fun status() = if (installed) HelperStatus.INSTALLED else HelperStatus.NOT_INSTALLED
    override fun helperVersion() = APP_VERSION
    override fun install() = true
    override fun uninstall() = true
    override fun setDisableSleep(disabled: Boolean): Boolean {
        disableSleepValues += disabled
        return true
    }
}

private class MemoryLogger : AppLogger {
    val messages = mutableListOf<String>()
    override fun info(message: String) { messages += message }
    override fun warn(message: String, error: Throwable?) { messages += message }
    override fun error(message: String, error: Throwable?) { messages += message }
    override fun logFile() = ""
}
