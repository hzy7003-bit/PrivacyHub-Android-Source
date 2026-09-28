package com.privacyhub.pro

import android.content.Context

class ProGate(context: Context) {
    private val licenseStore = ProLicenseStore(context)

    fun check(feature: ProFeature): ProGateResult {
        val state = licenseStore.currentState()
        if (!state.active) {
            return ProGateResult(
                allowed = false,
                title = "需要 Pro 授权",
                message = state.message
            )
        }
        val license = state.license
        if (license?.allows(feature) == true) {
            return ProGateResult(
                allowed = true,
                title = "Pro 功能可用",
                message = "${feature.label} 已解锁。"
            )
        }
        return ProGateResult(
            allowed = false,
            title = "当前 License 不包含此功能",
            message = "已激活 Pro，但未包含“${feature.label}”。"
        )
    }
}

data class ProGateResult(
    val allowed: Boolean,
    val title: String,
    val message: String
)
