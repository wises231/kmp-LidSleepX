package com.wyz.lidsleepx.app.platform

import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer

internal object MacNative {
    val coreFoundation: CoreFoundationLibrary = Native.load("CoreFoundation", CoreFoundationLibrary::class.java)
    val iops: IOPSLibrary = Native.load("IOKit", IOPSLibrary::class.java)
    val iokit: IOKitLibrary = Native.load("IOKit", IOKitLibrary::class.java)
    val iokitPower: IOKitPowerLibrary = Native.load("IOKit", IOKitPowerLibrary::class.java)
    val iokitSleep: IOKitSleepLibrary = Native.load("IOKit", IOKitSleepLibrary::class.java)
    val objc: ObjCLibrary = Native.load("objc", ObjCLibrary::class.java)

    val runLoopDefaultMode: Pointer
        get() = NativeLibrary.getInstance("CoreFoundation")
            .getGlobalVariableAddress("kCFRunLoopDefaultMode")
            .getPointer(0)
}
