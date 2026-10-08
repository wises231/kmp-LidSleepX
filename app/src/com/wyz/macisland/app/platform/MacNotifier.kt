package com.wyz.macisland.app.platform

import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.wyz.macisland.core.AppLogger
import com.wyz.macisland.core.Notifier
import java.util.concurrent.TimeUnit

class MacNotifier(
    private val logger: AppLogger,
    private val notificationsEnabled: () -> Boolean,
) : Notifier {
    private val objc = MacNative.objc
    private val objcLibrary = NativeLibrary.getInstance("objc")
    private val msgSend = objcLibrary.getFunction("objc_msgSend")
    private val classAddMethod = objcLibrary.getFunction("class_addMethod")
    @Volatile private var pendingReleaseUrl: String? = null
    private val activationCallback = DidActivateNotificationCallback { _, _, _, _ ->
        pendingReleaseUrl?.let { url ->
            runCatching { ProcessBuilder("/usr/bin/open", url).start() }
        }
        pendingReleaseUrl = null
    }
    private val delegate: Pointer? by lazy { createDelegate() }

    override fun notify(title: String, subtitle: String, message: String, releaseUrl: String?) {
        if (!notificationsEnabled()) return
        pendingReleaseUrl = releaseUrl
        val delivered = runCatching { deliverNative(title, subtitle, message) }.getOrDefault(false)
        if (!delivered) {
            logger.warn("Native notification failed; using osascript fallback")
            fallback(title, subtitle, message)
        }
    }

    private fun deliverNative(title: String, subtitle: String, message: String): Boolean {
        val delegateValue = delegate ?: return false
        val notificationClass = objc.objc_getClass("NSUserNotification") ?: return false
        val centerClass = objc.objc_getClass("NSUserNotificationCenter") ?: return false
        val allocated = sendPointer(notificationClass, selector("alloc")) ?: return false
        val notification = sendPointer(allocated, selector("init")) ?: return false
        sendVoid(notification, selector("setTitle:"), title)
        if (subtitle.isNotBlank()) sendVoid(notification, selector("setSubtitle:"), subtitle)
        if (message.isNotBlank()) sendVoid(notification, selector("setInformativeText:"), message)
        val center = sendPointer(centerClass, selector("defaultUserNotificationCenter")) ?: return false
        sendVoid(center, selector("setDelegate:"), delegateValue)
        sendVoid(center, selector("deliverNotification:"), notification)
        return true
    }

    private fun createDelegate(): Pointer? = runCatching {
        val objectClass = objc.objc_getClass("NSObject") ?: return@runCatching null
        val className = "MacIslandNotificationDelegate"
        val cls = objc.objc_allocateClassPair(objectClass, className, 0L) ?: return@runCatching null
        val added = classAddMethod.invoke(
            Boolean::class.javaPrimitiveType,
            arrayOf(
                cls,
                selector("userNotificationCenter:didActivateNotification:"),
                activationCallback,
                "v@:@@",
            ),
        ) as Boolean
        if (!added) return@runCatching null
        objc.objc_registerClassPair(cls)
        sendPointer(cls, selector("alloc"))?.let { sendPointer(it, selector("init")) }
    }.getOrNull()

    private fun fallback(title: String, subtitle: String, message: String) {
        val text = listOf(title, subtitle, message).filter { it.isNotBlank() }.joinToString(" - ")
        runCatching {
            ProcessBuilder(
                "/usr/bin/osascript",
                "-e",
                "display notification \"${appleScriptEscape(text)}\" with title \"MacIsland\"",
            ).redirectErrorStream(true).start().waitFor(5, TimeUnit.SECONDS)
        }
    }

    private fun selector(name: String): Pointer? = objc.sel_registerName(name)

    private fun sendPointer(receiver: Pointer?, selector: Pointer?, vararg args: Any?): Pointer? =
        msgSend.invoke(Pointer::class.java, arrayOf(receiver, selector, *args)) as Pointer?

    private fun sendVoid(receiver: Pointer?, selector: Pointer?, vararg args: Any?) {
        msgSend.invoke(Void.TYPE, arrayOf(receiver, selector, *args))
    }

    private fun appleScriptEscape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", " ")
}
