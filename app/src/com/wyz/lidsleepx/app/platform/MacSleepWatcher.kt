package com.wyz.lidsleepx.app.platform

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.wyz.lidsleepx.core.SleepWatcher

internal interface IOKitSleepLibrary : com.sun.jna.Library {
    fun IOPMAssertionCreateWithName(
        assertionType: Pointer?,
        assertionLevel: Int,
        assertionName: Pointer?,
        assertionId: IntByReference,
    ): Int

    fun IOPMAssertionRelease(assertionId: Int): Int

    fun IORegisterForSystemPower(
        refCon: Pointer?,
        notificationPort: PointerByReference,
        callback: IOPowerCallback,
        notifier: IntByReference,
    ): Int

    fun IONotificationPortGetRunLoopSource(port: Pointer?): Pointer?
    fun IONotificationPortDestroy(port: Pointer?)
    fun IOAllowPowerChange(connect: Int, notificationId: Long)
    fun IODeregisterForSystemPower(notifier: IntByReference): Int
    fun IOServiceClose(connect: Int): Int
}

class MacSleepWatcher : SleepWatcher {
    private val cf = MacNative.coreFoundation
    private val library: IOKitSleepLibrary = Native.load("IOKit", IOKitSleepLibrary::class.java)
    @Volatile private var onWillSleep: (() -> Unit)? = null
    @Volatile private var onDidWake: (() -> Unit)? = null
    @Volatile private var rootPort = 0
    @Volatile private var runLoop: Pointer? = null
    @Volatile private var stopped = false
    private val notifier = IntByReference()
    private var notificationPort: Pointer? = null
    private var thread: Thread? = null

    private val callback = IOPowerCallback { refCon: Pointer?, service: Int, messageType: Int, argument: Pointer? ->
        when (messageType) {
            MESSAGE_SYSTEM_WILL_SLEEP -> {
                onWillSleep?.invoke()
                if (rootPort != 0 && argument != null) {
                    library.IOAllowPowerChange(rootPort, Pointer.nativeValue(argument))
                }
            }
            MESSAGE_SYSTEM_WILL_POWER_ON, MESSAGE_SYSTEM_HAS_POWERED_ON -> onDidWake?.invoke()
        }
    }

    override fun subscribe(onWillSleep: () -> Unit, onDidWake: () -> Unit) {
        this.onWillSleep = onWillSleep
        this.onDidWake = onDidWake
        stopped = false
        thread = Thread {
            val port = PointerByReference()
            rootPort = library.IORegisterForSystemPower(null, port, callback, notifier)
            if (rootPort == 0 || port.value == null) return@Thread
            notificationPort = port.value
            val source = library.IONotificationPortGetRunLoopSource(port.value)
                ?: return@Thread
            runLoop = cf.CFRunLoopGetCurrent()
            cf.CFRunLoopAddSource(runLoop, source, MacNative.runLoopDefaultMode)
            if (!stopped) cf.CFRunLoopRun()
        }.apply {
            name = "LidSleepX-sleep-watcher"
            isDaemon = true
            start()
        }
    }

    override fun stop() {
        stopped = true
        runLoop?.let { cf.CFRunLoopStop(it) }
        if (rootPort != 0) {
            library.IODeregisterForSystemPower(notifier)
            notificationPort?.let(library::IONotificationPortDestroy)
            library.IOServiceClose(rootPort)
            rootPort = 0
            notificationPort = null
        }
        onWillSleep = null
        onDidWake = null
    }

    companion object {
        private const val MESSAGE_SYSTEM_WILL_SLEEP = 0xe0000280.toInt()
        private const val MESSAGE_SYSTEM_WILL_POWER_ON = 0xe0000320.toInt()
        private const val MESSAGE_SYSTEM_HAS_POWERED_ON = 0xe0000300.toInt()
    }
}
