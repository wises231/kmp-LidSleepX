package com.wyz.macisland.app.platform

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.PointerByReference

interface GlobalHotKey : AutoCloseable {
    fun register(): Boolean
    override fun close()

    companion object {
        const val KEY_CODE_T = 17
        const val MODIFIER_CONTROL = 0x1000
        const val MODIFIER_OPTION = 0x0800
        val CONTROL_OPTION_T = MODIFIER_CONTROL or MODIFIER_OPTION
    }
}

internal interface CarbonLibrary : Library {
    fun GetApplicationEventTarget(): Pointer?
    fun InstallEventHandler(
        target: Pointer?,
        handler: EventHandlerProc,
        numTypes: Int,
        typeList: EventTypeSpec?,
        userData: Pointer?,
        handlerRef: PointerByReference?,
    ): Int

    fun RegisterEventHotKey(
        hotKeyCode: Int,
        modifiers: Int,
        hotKeyId: EventHotKeyId,
        target: Pointer?,
        options: Int,
        hotKeyRef: PointerByReference?,
    ): Int

    fun UnregisterEventHotKey(hotKeyRef: Pointer?)
}

internal fun interface EventHandlerProc : Callback {
    fun invoke(nextHandler: Pointer?, event: Pointer?, userData: Pointer?): Int
}

@Structure.FieldOrder("eventClass", "eventKind")
internal class EventTypeSpec : Structure() {
    @JvmField var eventClass: Int = 0
    @JvmField var eventKind: Int = 0
}

@Structure.FieldOrder("signature", "id")
internal class EventHotKeyId : Structure() {
    @JvmField var signature: Int = 0
    @JvmField var id: Int = 0
}

class MacGlobalHotKey(
    private val action: () -> Unit,
) : GlobalHotKey {
    private val library: CarbonLibrary? by lazy {
        runCatching { Native.load("Carbon", CarbonLibrary::class.java) }.getOrNull()
    }
    private val callback = EventHandlerProc { _, _, _ ->
        runCatching { action() }
        0
    }
    private var hotKeyRef: Pointer? = null

    override fun register(): Boolean {
        if (hotKeyRef != null) return true
        val carbon = library ?: return false
        return runCatching {
            val target = carbon.GetApplicationEventTarget() ?: return false
            val eventType = EventTypeSpec().apply {
                eventClass = FOUR_CHAR_KEYBOARD
                eventKind = EVENT_HOT_KEY_PRESSED
                write()
            }
            val handler = PointerByReference()
            if (carbon.InstallEventHandler(target, callback, 1, eventType, null, handler) != 0) return false
            val ref = PointerByReference()
            val id = EventHotKeyId().apply {
                signature = FOUR_CHAR_MACISLAND
                this.id = 1
                write()
            }
            if (carbon.RegisterEventHotKey(
                    GlobalHotKey.KEY_CODE_T,
                    GlobalHotKey.CONTROL_OPTION_T,
                    id,
                    target,
                    0,
                    ref,
                ) != 0
            ) {
                return false
            }
            hotKeyRef = ref.value
            true
        }.getOrDefault(false)
    }

    override fun close() {
        val ref = hotKeyRef ?: return
        hotKeyRef = null
        runCatching { library?.UnregisterEventHotKey(ref) }
    }

    private companion object {
        const val FOUR_CHAR_KEYBOARD = 0x6B657962 // 'keyb'
        const val EVENT_HOT_KEY_PRESSED = 0x686B7020 // 'hkp '
        const val FOUR_CHAR_MACISLAND = 0x4D49534C // 'MISL'
    }
}
