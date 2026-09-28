package com.privacyhub.device

import java.io.BufferedReader
import java.io.InputStreamReader

object RomInfoProvider {
    fun detect(): String {
        val candidates = listOf(
            "ro.miui.ui.version.name" to "MIUI/HyperOS",
            "ro.build.version.opporom" to "ColorOS",
            "ro.vivo.os.version" to "OriginOS/FuntouchOS",
            "ro.build.version.emui" to "EMUI",
            "ro.huawei.build.display.id" to "HarmonyOS/EMUI",
            "ro.build.display.id" to "Display"
        )
        return candidates.firstNotNullOfOrNull { (key, label) ->
            val value = getProp(key).takeIf { it.isNotBlank() }
            value?.let { "$label $it" }
        } ?: "unknown"
    }

    private fun getProp(key: String): String = runCatching {
        val process = Runtime.getRuntime().exec(arrayOf("getprop", key))
        BufferedReader(InputStreamReader(process.inputStream)).use { it.readLine().orEmpty() }
    }.getOrDefault("")
}
