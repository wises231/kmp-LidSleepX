package com.wyz.macisland.app.platform

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import java.awt.Component
import java.nio.file.Files
import java.nio.file.Path
import java.lang.reflect.Field
import java.util.concurrent.TimeUnit

internal fun interface MacIslandDragCompletion : Callback {
    fun invoke(token: Int, operation: Long)
}

private interface MacIslandDragLibrary : Library {
    fun MIStartFileDrag(
        windowPointer: Pointer,
        filePath: String,
        token: Int,
        completion: MacIslandDragCompletion,
    ): Int

    fun MIAnimateImageToWindow(filePath: String, windowPointer: Pointer, returning: Int): Int

    fun MIOpenMarkup(filePath: String): Int
}

private interface CoreGraphicsShelfLibrary : Library {
    fun CGSMainConnectionID(): Int
    fun CGSCopyManagedDisplaySpaces(connection: Int): Pointer?
}

object MacShelfNative {
    private val coreGraphics: CoreGraphicsShelfLibrary? by lazy {
        runCatching { Native.load("CoreGraphics", CoreGraphicsShelfLibrary::class.java) }.getOrNull()
    }
    private val dragLibrary: MacIslandDragLibrary? by lazy {
        val path = findNativeLibrary() ?: return@lazy null
        runCatching { Native.load(path.toString(), MacIslandDragLibrary::class.java) }.getOrNull()
    }
    private val objcMsgSend by lazy {
        runCatching { NativeLibrary.getInstance("objc").getFunction("objc_msgSend") }.getOrNull()
    }
    private val componentPeerField: Field? by lazy {
        runCatching {
            Class.forName("java.awt.Component").getDeclaredField("peer").apply { isAccessible = true }
        }.getOrNull()
    }

    fun isFullScreenSpaceActive(): Boolean {
        val library = coreGraphics ?: return false
        return runCatching {
            val displays = library.CGSCopyManagedDisplaySpaces(library.CGSMainConnectionID()) ?: return false
            try {
                val count = MacNative.coreFoundation.CFArrayGetCount(displays)
                for (index in 0 until count) {
                    val display = MacNative.coreFoundation.CFArrayGetValueAtIndex(displays, index) ?: continue
                    val current = dictionaryValue(display, "Current Space") ?: continue
                    val type = numberValue(current, "type") ?: continue
                    if (type == FULL_SCREEN_SPACE_TYPE) return true
                }
                false
            } finally {
                MacNative.coreFoundation.CFRelease(displays)
            }
        }.getOrDefault(false)
    }

    fun configureFloatingPanel(component: Component): Boolean = runCatching {
        val window = nativeWindow(component) ?: return false
        sendVoid(window, selector("setLevel:"), FLOATING_WINDOW_LEVEL)
        sendVoid(window, selector("setCollectionBehavior:"), PANEL_COLLECTION_BEHAVIOR)
        sendVoid(window, selector("setHidesOnDeactivate:"), falseByte())
        sendVoid(window, selector("setMovable:"), falseByte())
        sendVoid(window, selector("setHasShadow:"), falseByte())
        sendVoid(window, selector("setIgnoresMouseEvents:"), trueByte())
        sendVoid(window, selector("orderFrontRegardless"))
        true
    }.getOrDefault(false)

    fun nativeWindowPointer(component: Component): Pointer? = runCatching { nativeWindow(component) }.getOrNull()

    fun openMarkup(path: Path): Boolean {
        val library = dragLibrary ?: return false
        return runCatching {
            library.MIOpenMarkup(path.toAbsolutePath().normalize().toString()) == 1
        }.getOrDefault(false)
    }

    internal fun startFileDrag(
        component: Component,
        path: Path,
        token: Int,
        completion: MacIslandDragCompletion,
    ): Boolean {
        val library = dragLibrary ?: return false
        val window = nativeWindowPointer(component) ?: return false
        return runCatching {
            library.MIStartFileDrag(window, path.toAbsolutePath().normalize().toString(), token, completion) == 1
        }.getOrDefault(false)
    }

    internal fun animateImage(component: Component, path: Path, returning: Boolean): Boolean {
        val library = dragLibrary ?: return false
        val window = nativeWindowPointer(component) ?: return false
        return runCatching {
            library.MIAnimateImageToWindow(
                path.toAbsolutePath().normalize().toString(),
                window,
                if (returning) 1 else 0,
            ) == 1
        }.getOrDefault(false)
    }

    fun setPanelMousePassthrough(component: Component, passThrough: Boolean): Boolean = runCatching {
        val window = nativeWindow(component) ?: return false
        sendVoid(window, selector("setIgnoresMouseEvents:"), passThrough.toNativeBool())
        true
    }.getOrDefault(false)

    fun playSound(name: String, volume: Float = 0.35f) {
        runCatching {
            val sound = when (name.lowercase()) {
                "pop" -> "/System/Library/Sounds/Pop.aiff"
                "trash" -> "/System/Library/Sounds/Submarine.aiff"
                else -> "/System/Library/Sounds/Tink.aiff"
            }
            val process = ProcessBuilder("/usr/bin/afplay", "-v", volume.coerceIn(0f, 1f).toString(), sound)
                .redirectErrorStream(true)
                .start()
            if (!process.waitFor(4L, TimeUnit.SECONDS)) process.destroyForcibly()
        }
    }

    private fun findNativeLibrary(): Path? {
        val configured = System.getProperty("macisland.native.library")
        if (!configured.isNullOrBlank()) {
            val path = runCatching { Path.of(configured).toAbsolutePath().normalize() }.getOrNull()
            if (path != null && Files.isRegularFile(path)) return path
        }

        val candidates = LinkedHashSet<Path>()
        runCatching {
            val location = MacShelfNative::class.java.protectionDomain.codeSource.location.toURI()
            var current = Path.of(location).toAbsolutePath().normalize()
            if (Files.isRegularFile(current)) current = current.parent ?: current
            repeat(6) {
                candidates.add(current.resolve("libMacIslandDrag.dylib"))
                candidates.add(current.resolve("native").resolve("libMacIslandDrag.dylib"))
                current = current.parent ?: return@repeat
            }
        }
        candidates.add(
            Path.of(System.getProperty("user.dir"), "native", "libMacIslandDrag.dylib")
                .toAbsolutePath()
                .normalize(),
        )
        return candidates.firstOrNull(Files::isRegularFile)
    }

    private fun nativeWindow(component: Component): Pointer? {
        val peer = componentPeerField?.get(component) ?: return null
        val platformWindow = peer.javaClass.methods
            .firstOrNull { it.name == "getPlatformWindow" && it.parameterCount == 0 }
            ?.invoke(peer) ?: return null
        val contentView = platformWindow.javaClass.methods
            .firstOrNull { it.name == "getContentView" && it.parameterCount == 0 }
            ?.invoke(platformWindow) ?: return null
        val viewPointer = contentView.javaClass.methods
            .firstOrNull { it.name == "getAWTView" && it.parameterCount == 0 }
            ?.invoke(contentView) as? Long ?: return null
        if (viewPointer == 0L) return null
        return sendPointer(Pointer(viewPointer), selector("window"))
    }

    private fun sendPointer(receiver: Pointer?, selector: Pointer?, vararg args: Any?): Pointer? {
        val function = objcMsgSend ?: return null
        return function.invoke(Pointer::class.java, arrayOf(receiver, selector, *args)) as Pointer?
    }

    private fun sendVoid(receiver: Pointer?, selector: Pointer?, vararg args: Any?) {
        val function = objcMsgSend ?: return
        function.invoke(Void.TYPE, arrayOf(receiver, selector, *args))
    }

    private fun selector(name: String): Pointer? = MacNative.objc.sel_registerName(name)

    private fun Boolean.toNativeBool(): Byte = if (this) 1 else 0
    private fun trueByte(): Byte = 1
    private fun falseByte(): Byte = 0

    private fun dictionaryValue(dictionary: Pointer, key: String): Pointer? {
        val cf = MacNative.coreFoundation
        val cfKey = cf.CFStringCreateWithCString(null, key, UTF8_ENCODING) ?: return null
        return try {
            cf.CFDictionaryGetValue(dictionary, cfKey)
        } finally {
            cf.CFRelease(cfKey)
        }
    }

    private fun numberValue(dictionary: Pointer, key: String): Int? {
        val value = dictionaryValue(dictionary, key) ?: return null
        val result = IntByReference()
        return if (MacNative.coreFoundation.CFNumberGetValue(value, K_CF_NUMBER_SINT32_TYPE, result.pointer)) {
            result.value
        } else {
            null
        }
    }

    private const val UTF8_ENCODING = 0x08000100
    private const val K_CF_NUMBER_SINT32_TYPE = 3
    private const val FULL_SCREEN_SPACE_TYPE = 4
    private const val FLOATING_WINDOW_LEVEL = 3L
    private const val PANEL_COLLECTION_BEHAVIOR = 0x51L
}
