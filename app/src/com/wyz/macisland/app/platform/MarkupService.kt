package com.wyz.macisland.app.platform

import com.sun.jna.Function
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class MarkupService {
    private data class NativeHandles(
        val objc: ObjCLibrary,
        val msgSend: Function,
        val nsUrlClass: Pointer,
        val nsArrayClass: Pointer,
        val serviceClass: Pointer,
        val nsStringClass: Pointer,
    )

    private val handles: NativeHandles? by lazy {
        runCatching {
            val library = NativeLibrary.getInstance("AppKit")
            NativeHandles(
                objc = MacNative.objc,
                msgSend = library.getFunction("objc_msgSend"),
                nsUrlClass = requireNotNull(MacNative.objc.objc_getClass("NSURL")),
                nsArrayClass = requireNotNull(MacNative.objc.objc_getClass("NSArray")),
                serviceClass = requireNotNull(MacNative.objc.objc_getClass("NSSharingService")),
                nsStringClass = requireNotNull(MacNative.objc.objc_getClass("NSString")),
            )
        }.getOrNull()
    }

    fun edit(path: Path): Boolean {
        val native = runCatching { nativeEdit(path) }.getOrDefault(false)
        return if (native) true else fallback(path)
    }

    private fun nativeEdit(path: Path): Boolean {
        val handles = handles ?: return false
        val objc = handles.objc
        val string = sendPointer(
            handles.msgSend,
            handles.nsStringClass,
            selector(objc, "stringWithUTF8String:"),
            path.toAbsolutePath().toString(),
        ) ?: return false
        val serviceName = sendPointer(
            handles.msgSend,
            handles.nsStringClass,
            selector(objc, "stringWithUTF8String:"),
            "com.apple.MarkupUI.Markup",
        ) ?: return false
        val service = sendPointer(
            handles.msgSend,
            handles.serviceClass,
            selector(objc, "sharingServiceNamed:"),
            serviceName,
        ) ?: return false
        val url = sendPointer(handles.msgSend, handles.nsUrlClass, selector(objc, "fileURLWithPath:"), string)
            ?: return false
        val array = sendPointer(handles.msgSend, handles.nsArrayClass, selector(objc, "arrayWithObject:"), url)
            ?: return false
        val canPerform = handles.msgSend.invoke(
            Boolean::class.javaPrimitiveType,
            arrayOf(service, selector(objc, "canPerformWithItems:"), array),
        ) as Boolean
        if (!canPerform) return false
        handles.msgSend.invoke(Void.TYPE, arrayOf(service, selector(objc, "performWithItems:"), array))
        return true
    }

    private fun fallback(path: Path): Boolean = runCatching {
        val process = ProcessBuilder("/usr/bin/open", "-a", "Preview", path.toAbsolutePath().toString())
            .redirectErrorStream(true)
            .start()
        process.waitFor(5L, TimeUnit.SECONDS)
        process.exitValue() == 0
    }.getOrDefault(false)

    private fun selector(objc: ObjCLibrary, name: String): Pointer? = objc.sel_registerName(name)

    private fun sendPointer(msgSend: Function, receiver: Pointer?, selector: Pointer?, vararg args: Any?): Pointer? =
        msgSend.invoke(Pointer::class.java, arrayOf(receiver, selector, *args)) as Pointer?
}
