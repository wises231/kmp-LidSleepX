package com.wyz.macisland.app.platform

import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.wyz.macisland.core.SleepController
import java.util.concurrent.TimeUnit

class MacSleepController(
    private val clock: () -> Long = System::currentTimeMillis,
    private val timeoutReader: () -> Long? = ::readSystemIdleSleepTimeout,
) : SleepController {
    private val cf = MacNative.coreFoundation
    private val iokit = MacNative.iokitSleep
    private val lock = Any()
    private var assertion: IntByReference? = null
    private val timeoutCache = IdleSleepTimeoutCache(clock, timeoutReader)

    override fun sleep() {
        runCommand("/usr/bin/pmset", "sleepnow")
    }

    override fun displaySleep() {
        runCommand("/usr/bin/pmset", "displaysleepnow")
    }

    override fun setIdleSleepPrevented(prevented: Boolean) {
        synchronized(lock) {
            timeoutCache.invalidate()
            if (prevented && assertion == null) {
                val type = cf.CFStringCreateWithCString(null, "NoIdleSleepAssertion", UTF8_ENCODING) ?: return
                val name = cf.CFStringCreateWithCString(null, "MacIsland prevents idle sleep", UTF8_ENCODING)
                val id = IntByReference()
                try {
                    val result = iokit.IOPMAssertionCreateWithName(type, ASSERTION_LEVEL_ON, name, id)
                    if (result == 0) assertion = id else assertion = null
                } finally {
                    cf.CFRelease(type)
                    if (name != null) cf.CFRelease(name)
                }
            } else if (!prevented && assertion != null) {
                iokit.IOPMAssertionRelease(assertion!!.value)
                assertion = null
            }
        }
    }

    override fun systemIdleSleepTimeoutSeconds(): Long? {
        synchronized(lock) {
            if (assertion != null) return null
        }
        return timeoutCache.get()
    }

    private fun runCommand(vararg command: String) {
        runCatching {
            val process = ProcessBuilder(*command).redirectErrorStream(true).start()
            process.waitFor(10, TimeUnit.SECONDS)
        }
    }

    companion object {
        private const val UTF8_ENCODING = 0x08000100
        private const val ASSERTION_LEVEL_ON = 255
        private fun readSystemIdleSleepTimeout(): Long? {
            val output = runCatching {
                val process = ProcessBuilder("/usr/bin/pmset", "-g")
                    .redirectErrorStream(true)
                    .start()
                val output = process.inputStream.bufferedReader().readText()
                process.waitFor()
                output
            }.getOrNull() ?: return null
            return Regex("""(?m)^\s*sleep\s+(\d+)\s*$""")
                .findAll(output)
                .mapNotNull { it.groupValues.getOrNull(1)?.toLongOrNull() }
                .minOrNull()
                ?.times(60L)
                ?.takeIf { it > 0L }
        }
    }
}
