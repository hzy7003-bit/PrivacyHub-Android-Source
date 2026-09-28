package com.privacyhub.automation

import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

/**
 * Keeps unlabelled browser inputs opt-in: a page must visibly offer the netdisk extraction action.
 */
internal object NetdiskAutomationPagePolicy {
    fun allowsSingleUnlabelledInput(
        hasExplicitAccessCodePrompt: Boolean,
        hasNetdiskExtractionAction: Boolean
    ): Boolean {
        return hasExplicitAccessCodePrompt || hasNetdiskExtractionAction
    }

    fun hasExtractionAction(node: AccessibilityNodeInfo): Boolean {
        val text = listOfNotNull(node.text, node.contentDescription)
            .joinToString(" ")
            .lowercase(Locale.ROOT)
        return text.contains("\u63d0\u53d6\u6587\u4ef6") ||
            text.contains("extract file")
    }
}
