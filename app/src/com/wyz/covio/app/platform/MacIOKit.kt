package com.wyz.covio.app.platform

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference

internal interface IOPSLibrary : Library {
    fun IOPSCopyPowerSourcesInfo(): Pointer?
    fun IOPSCopyPowerSourcesList(blob: Pointer?): Pointer?
    fun IOPSGetPowerSourceDescription(blob: Pointer?, source: Pointer?): Pointer?
}

internal interface IOKitLibrary : Library {
    fun IOServiceMatching(name: String): Pointer?
    fun IOServiceGetMatchingService(masterPort: Int, matching: Pointer?): Int
    fun IORegistryEntryCreateCFProperty(
        entry: Int,
        key: Pointer?,
        allocator: Pointer?,
        options: Int,
    ): Pointer?
    fun IONotificationPortCreate(mainPort: Int): Pointer?
    fun IONotificationPortGetRunLoopSource(notificationPort: Pointer?): Pointer?
    fun IONotificationPortDestroy(notificationPort: Pointer?)
    fun IOServiceAddInterestNotification(
        notificationPort: Pointer?,
        service: Int,
        interestType: String,
        callback: IOServiceInterestCallback,
        refCon: Pointer?,
        notification: IntByReference,
    ): Int
    fun IOObjectRelease(objectId: Int): Int
}

internal fun interface IOPowerCallback : Callback {
    fun invoke(refCon: Pointer?, service: Int, messageType: Int, messageArgument: Pointer?)
}

internal fun interface IOServiceInterestCallback : Callback {
    fun invoke(refCon: Pointer?, service: Int, messageType: Int, messageArgument: Pointer?)
}

internal interface ObjCLibrary : Library {
    fun objc_getClass(name: String): Pointer?
    fun sel_registerName(name: String): Pointer?
    fun objc_msgSend(receiver: Pointer?, selector: Pointer?, vararg arguments: Any?): Pointer?
    fun objc_allocateClassPair(superclass: Pointer?, name: String, extraBytes: Long): Pointer?
    fun objc_registerClassPair(cls: Pointer?)
    fun class_addMethod(cls: Pointer?, selector: Pointer?, implementation: Callback, types: String): Boolean
}

internal fun interface DidActivateNotificationCallback : Callback {
    fun invoke(
        delegate: Pointer?,
        selector: Pointer?,
        center: Pointer?,
        notification: Pointer?,
    )
}
