package com.privacyhub.device

import android.os.Build

data class DeviceInfo(
    val androidVersion: String,
    val sdkInt: Int,
    val brand: String,
    val manufacturer: String,
    val model: String,
    val rom: String
)

object DeviceInfoProvider {
    fun current(): DeviceInfo = DeviceInfo(
        androidVersion = Build.VERSION.RELEASE ?: "unknown",
        sdkInt = Build.VERSION.SDK_INT,
        brand = Build.BRAND ?: "unknown",
        manufacturer = Build.MANUFACTURER ?: "unknown",
        model = Build.MODEL ?: "unknown",
        rom = RomInfoProvider.detect()
    )
}
