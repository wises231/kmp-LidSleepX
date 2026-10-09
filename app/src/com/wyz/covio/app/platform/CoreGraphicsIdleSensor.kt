package com.wyz.covio.app.platform

import com.sun.jna.Library
import com.sun.jna.Native
import com.wyz.covio.core.IdleSensor

internal interface CoreGraphicsLibrary : Library {
    fun CGEventSourceSecondsSinceLastEventType(stateId: Int, eventType: Int): Double
}

class CoreGraphicsIdleSensor : IdleSensor {
    private val library = Native.load("CoreGraphics", CoreGraphicsLibrary::class.java)

    override fun idleSeconds(): Long =
        library.CGEventSourceSecondsSinceLastEventType(COMBINED_SESSION_STATE, ANY_INPUT_EVENT).toLong()

    companion object {
        private const val COMBINED_SESSION_STATE = 0
        private const val ANY_INPUT_EVENT = -1
    }
}
