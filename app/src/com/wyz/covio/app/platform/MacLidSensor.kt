package com.wyz.covio.app.platform

import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.wyz.covio.core.LidSensor
import com.wyz.covio.core.LidState

class MacLidSensor : LidSensor {
    private val cf = MacNative.coreFoundation
    private val iokit = MacNative.iokit
    @Volatile private var listener: ((LidState) -> Unit)? = null
    @Volatile private var stopped = false
    @Volatile private var notificationPort: Pointer? = null
    @Volatile private var runLoopSource: Pointer? = null
    @Volatile private var runLoop: Pointer? = null
    @Volatile private var service = 0
    private val notification = IntByReference()
    private val cleanupLock = Any()
    private var eventThread: Thread? = null

    private val callback = IOServiceInterestCallback { _, _, _, _ ->
        runCatching { listener?.invoke(read()) }
    }

    override fun read(): LidState = runCatching {
        val rootDomain = iokit.IOServiceGetMatchingService(0, iokit.IOServiceMatching("IOPMrootDomain"))
        if (rootDomain == 0) return LidState.UNKNOWN
        try {
            val key = cf.CFStringCreateWithCString(null, "AppleClamshellState", UTF8_ENCODING)
                ?: return LidState.UNKNOWN
            try {
                val value = iokit.IORegistryEntryCreateCFProperty(rootDomain, key, null, 0)
                    ?: return LidState.UNKNOWN
                try {
                    if (cf.CFBooleanGetValue(value)) LidState.CLOSED else LidState.OPEN
                } finally {
                    cf.CFRelease(value)
                }
            } finally {
                cf.CFRelease(key)
            }
        } finally {
            iokit.IOObjectRelease(rootDomain)
        }
    }.getOrDefault(LidState.UNKNOWN)

    override fun subscribe(listener: (LidState) -> Unit) {
        this.listener = listener
        stopped = false
        eventThread = Thread {
            try {
                val port = iokit.IONotificationPortCreate(0) ?: return@Thread
                notificationPort = port
                val source = iokit.IONotificationPortGetRunLoopSource(port) ?: return@Thread
                runLoopSource = source
                service = iokit.IOServiceGetMatchingService(0, iokit.IOServiceMatching("IOPMrootDomain"))
                if (service == 0) return@Thread
                val result = iokit.IOServiceAddInterestNotification(
                    port,
                    service,
                    "IOGeneralInterest",
                    callback,
                    null,
                    notification,
                )
                if (result != 0) return@Thread
                runLoop = cf.CFRunLoopGetCurrent()
                cf.CFRunLoopAddSource(runLoop, source, MacNative.runLoopDefaultMode)
                listener(read())
                if (!stopped) cf.CFRunLoopRun()
            } finally {
                releaseNativeObjects()
            }
        }.apply {
            name = "Covio-lid-events"
            isDaemon = true
            start()
        }
    }

    override fun stop() {
        stopped = true
        listener = null
        runLoop?.let(cf::CFRunLoopStop)
        eventThread?.join(STOP_TIMEOUT_MILLIS)
        releaseNativeObjects()
    }

    private fun releaseNativeObjects() {
        synchronized(cleanupLock) {
            runLoop?.let { loop ->
                runLoopSource?.let { source ->
                    cf.CFRunLoopRemoveSource(loop, source, MacNative.runLoopDefaultMode)
                }
            }
            if (notification.value != 0) {
                iokit.IOObjectRelease(notification.value)
                notification.value = 0
            }
            if (service != 0) {
                iokit.IOObjectRelease(service)
                service = 0
            }
            notificationPort?.let(iokit::IONotificationPortDestroy)
            notificationPort = null
            runLoopSource = null
            runLoop = null
        }
    }

    companion object {
        private const val UTF8_ENCODING = 0x08000100
        private const val STOP_TIMEOUT_MILLIS = 2_000L
    }
}
