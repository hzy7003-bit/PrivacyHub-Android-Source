package com.privacyhub.automation

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.view.accessibility.AccessibilityManager

object NetdiskAccessibilityStatus {
    fun isServiceEnabled(context: Context): Boolean {
        val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
        val component = ComponentName(context, NetdiskAccessCodeAccessibilityService::class.java)
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { isThisServiceId(it.id, component.flattenToString(), component.flattenToShortString()) }
    }

    internal fun isThisServiceId(id: String, fullId: String, shortId: String): Boolean {
        return id == fullId || id == shortId
    }
}
