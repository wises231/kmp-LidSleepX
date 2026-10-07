package com.wyz.lidsleepx.app.platform

import com.wyz.lidsleepx.core.LidSensor
import com.wyz.lidsleepx.core.LidState

class MacLidSensor : LidSensor {
    private val cf = MacNative.coreFoundation
    private val iokit = MacNative.iokit
    @Volatile private var listener: ((LidState) -> Unit)? = null
    @Volatile private var stopped = false
    private var pollThread: Thread? = null

    override fun read(): LidState = runCatching {
        val service = iokit.IOServiceGetMatchingService(0, iokit.IOServiceMatching("AppleClamshellState"))
        if (service == 0) return LidState.UNKNOWN
        try {
            val key = cf.CFStringCreateWithCString(null, "AppleClamshellState", UTF8_ENCODING)
                ?: return LidState.UNKNOWN
            try {
                val value = iokit.IORegistryEntryCreateCFProperty(service, key, null, 0)
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
            iokit.IOObjectRelease(service)
        }
    }.getOrDefault(LidState.UNKNOWN)

    override fun subscribe(listener: (LidState) -> Unit) {
        this.listener = listener
        stopped = false
        pollThread = Thread {
            var previous = read()
            listener(previous)
            while (!stopped) {
                Thread.sleep(1_000L)
                val current = read()
                if (current != previous) {
                    previous = current
                    listener(current)
                }
            }
        }.apply {
            name = "LidSleepX-lid-events"
            isDaemon = true
            start()
        }
    }

    override fun stop() {
        stopped = true
        listener = null
    }

    companion object {
        private const val UTF8_ENCODING = 0x08000100
    }
}
