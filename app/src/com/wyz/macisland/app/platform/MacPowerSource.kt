package com.wyz.macisland.app.platform

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.wyz.macisland.core.BatteryStatus
import com.wyz.macisland.core.PowerSource
import com.wyz.macisland.core.PowerState

internal interface CoreFoundationLibrary : Library {
    fun CFArrayGetCount(array: Pointer?): Long
    fun CFArrayGetValueAtIndex(array: Pointer?, index: Long): Pointer?
    fun CFBooleanGetValue(value: Pointer?): Boolean
    fun CFDictionaryGetValue(dictionary: Pointer?, key: Pointer?): Pointer?
    fun CFNumberGetValue(number: Pointer?, type: Int, value: Pointer?): Boolean
    fun CFRelease(value: Pointer?)
    fun CFStringCreateWithCString(allocator: Pointer?, value: String, encoding: Int): Pointer?
    fun CFStringGetCString(value: Pointer?, buffer: Pointer?, size: Long, encoding: Int): Boolean
    fun CFRunLoopAddSource(runLoop: Pointer?, source: Pointer?, mode: Pointer?)
    fun CFRunLoopGetCurrent(): Pointer?
    fun CFRunLoopRemoveSource(runLoop: Pointer?, source: Pointer?, mode: Pointer?)
    fun CFRunLoopRun()
    fun CFRunLoopStop(runLoop: Pointer?)
}

internal interface CoreFoundationFoundationLibrary : Library {
    fun CFRunLoopRun()
}

internal fun interface PowerNotificationCallback : Callback {
    fun invoke(context: Pointer?)
}

internal interface IOKitPowerLibrary : Library {
    fun IOPSNotificationCreateRunLoopSource(callback: PowerNotificationCallback, context: Pointer?): Pointer?
}

class MacPowerSource : PowerSource {
    private val cf = MacNative.coreFoundation
    private val iokit = MacNative.iokitPower
    @Volatile private var listener: (() -> Unit)? = null
    @Volatile private var source: Pointer? = null
    @Volatile private var runLoop: Pointer? = null
    @Volatile private var stopped = false
    private val callback = PowerNotificationCallback { refresh() }
    private var eventThread: Thread? = null
    private var pollThread: Thread? = null

    override fun read(): BatteryStatus? = readBattery()

    override fun subscribe(listener: () -> Unit) {
        this.listener = listener
        stopped = false
        registerIOPS()
        pollThread = Thread {
            while (!stopped) {
                runCatching { refresh() }
                Thread.sleep(5_000L)
            }
        }.apply {
            name = "MacIsland-power-poll"
            isDaemon = true
            start()
        }
    }

    override fun stop() {
        stopped = true
        source?.let { runLoop?.let { loop -> cf.CFRunLoopRemoveSource(loop, it, MacNative.runLoopDefaultMode) } }
        runLoop?.let { cf.CFRunLoopStop(it) }
        source?.let { cf.CFRelease(it) }
        source = null
        runLoop = null
        listener = null
    }

    private fun registerIOPS() {
        runCatching {
            val created = iokit.IOPSNotificationCreateRunLoopSource(callback, null) ?: return
            source = created
            eventThread = Thread {
                runLoop = cf.CFRunLoopGetCurrent()
                cf.CFRunLoopAddSource(runLoop, created, MacNative.runLoopDefaultMode)
                if (!stopped) cf.CFRunLoopRun()
            }.apply {
                name = "MacIsland-power-events"
                isDaemon = true
                start()
            }
        }.onFailure {
            eventThread = null
        }
    }

    private fun refresh() {
        listener?.invoke()
    }

    private fun readBattery(): BatteryStatus? = runCatching {
        val info = MacNative.iops.IOPSCopyPowerSourcesInfo() ?: return null
        try {
            val sources = MacNative.iops.IOPSCopyPowerSourcesList(info) ?: return null
            try {
                val count = cf.CFArrayGetCount(sources)
                for (index in 0 until count) {
                    val source = cf.CFArrayGetValueAtIndex(sources, index) ?: continue
                    val description = MacNative.iops.IOPSGetPowerSourceDescription(info, source) ?: continue
                    val type = dictionaryString(description, "Type")
                    if (!type.equals("InternalBattery", ignoreCase = true)) continue
                    val current = dictionaryLong(description, "Current Capacity")
                    val maximum = dictionaryLong(description, "Max Capacity")
                    val percent = when {
                        current != null && maximum != null && maximum > 0 -> ((current * 100) / maximum).toInt()
                        else -> dictionaryLong(description, "Current Capacity")?.toInt() ?: 0
                    }
                    val isCharging = dictionaryBoolean(description, "Is Charging") == true
                    val isCharged = dictionaryBoolean(description, "Is Charged") == true
                    val powerState = dictionaryString(description, "Power Source State")
                    val state = when {
                        isCharged -> PowerState.CHARGED
                        isCharging -> PowerState.CHARGING
                        powerState.equals("Battery Power", ignoreCase = true) -> PowerState.DISCHARGING
                        powerState.equals("AC Power", ignoreCase = true) -> PowerState.AC_POWER
                        else -> PowerState.UNKNOWN
                    }
                    val minutes = dictionaryLong(description, if (state == PowerState.DISCHARGING) "Time to Empty" else "Time to Full Charge")
                    return BatteryStatus(percent.coerceIn(0, 100), state, minutes?.takeIf { it > 0 }?.times(60L))
                }
                null
            } finally {
                cf.CFRelease(sources)
            }
        } finally {
            cf.CFRelease(info)
        }
    }.getOrNull()

    private fun dictionaryString(dictionary: Pointer, key: String): String? = withCFString(key) { keyPointer ->
        val value = cf.CFDictionaryGetValue(dictionary, keyPointer) ?: return@withCFString null
        val buffer = Memory(256)
        if (!cf.CFStringGetCString(value, buffer, 256, UTF8_ENCODING)) return@withCFString null
        buffer.getString(0)
    }

    private fun dictionaryLong(dictionary: Pointer, key: String): Long? = withCFString(key) { keyPointer ->
        val value = cf.CFDictionaryGetValue(dictionary, keyPointer) ?: return@withCFString null
        val memory = Memory(8)
        if (!cf.CFNumberGetValue(value, CF_NUMBER_SINT64, memory)) return@withCFString null
        memory.getLong(0)
    }

    private fun dictionaryBoolean(dictionary: Pointer, key: String): Boolean? = withCFString(key) { keyPointer ->
        cf.CFDictionaryGetValue(dictionary, keyPointer)?.let { cf.CFBooleanGetValue(it) }
    }

    private inline fun <T> withCFString(value: String, block: (Pointer) -> T): T? {
        val pointer = cf.CFStringCreateWithCString(null, value, UTF8_ENCODING) ?: return null
        return try { block(pointer) } finally { cf.CFRelease(pointer) }
    }

    companion object {
        private const val UTF8_ENCODING = 0x08000100
        private const val CF_NUMBER_SINT64 = 4
    }
}
