package com.privacyhub.automation

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.privacyhub.data.settings.AppSettingsStore
import com.privacyhub.pro.ProFeature
import com.privacyhub.pro.ProGate
import java.util.ArrayDeque
import java.util.Locale

class NetdiskAccessCodeAccessibilityService : AccessibilityService() {
    private val settingsStore by lazy { AppSettingsStore(this) }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!settingsStore.isNetdiskAutomationEnabled() ||
            !ProGate(this).check(ProFeature.AccessibilityAutomation).allowed
        ) {
            return
        }
        val packageName = event.packageName?.toString().orEmpty()
        val root = rootInActiveWindow ?: event.source ?: return
        val currentHost = currentBrowserHost(packageName, root) ?: return
        val task = NetdiskAccessCodeTaskController.matching(packageName, currentHost) ?: return
        val input = findAccessCodeInput(root) ?: return
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, task.accessCode)
        }
        val filled = input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        if (filled) {
            NetdiskAccessCodeTaskController.complete(applicationContext, task)
        }
    }

    override fun onInterrupt() = Unit

    private fun currentBrowserHost(packageName: String, root: AccessibilityNodeInfo): String? {
        val candidates = mutableListOf<NetdiskAddressBarCandidate>()
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        pending.add(root)
        var inspected = 0
        while (pending.isNotEmpty() && inspected < MAX_INSPECTED_NODES) {
            val node = pending.removeFirst()
            inspected += 1
            candidates += NetdiskAddressBarCandidate(
                viewIdResourceName = node.viewIdResourceName,
                text = node.text?.toString(),
                hintText = node.hintText?.toString(),
                contentDescription = node.contentDescription?.toString()
            )
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(pending::addLast)
            }
        }
        return NetdiskBrowserAddressBarPolicy.extractHost(packageName, candidates)
    }

    private fun findAccessCodeInput(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        root ?: return null
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        val editableCandidates = mutableListOf<AccessibilityNodeInfo>()
        pending.add(root)
        var inspected = 0
        var pageExplicitlyRequestsAccessCode = false
        var pageProvidesNetdiskExtractionContext = false
        while (pending.isNotEmpty() && inspected < MAX_INSPECTED_NODES) {
            val node = pending.removeFirst()
            inspected += 1
            pageExplicitlyRequestsAccessCode = pageExplicitlyRequestsAccessCode || hasAccessCodePagePrompt(node)
            pageProvidesNetdiskExtractionContext = pageProvidesNetdiskExtractionContext ||
                NetdiskAutomationPagePolicy.hasExtractionAction(node)
            if (node.isEnabled && node.isEditable && supportsSetText(node)) {
                if (hasAccessCodeLabel(node)) return node
                editableCandidates.add(node)
            }
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(pending::addLast)
            }
        }
        if (!NetdiskAutomationPagePolicy.allowsSingleUnlabelledInput(
                hasExplicitAccessCodePrompt = pageExplicitlyRequestsAccessCode,
                hasNetdiskExtractionAction = pageProvidesNetdiskExtractionContext
            )
        ) {
            return null
        }
        return editableCandidates.singleOrNull()
            ?: editableCandidates.filter(AccessibilityNodeInfo::isFocused).singleOrNull()
    }

    private fun supportsSetText(node: AccessibilityNodeInfo): Boolean {
        return node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT }
    }

    private fun hasAccessCodeLabel(node: AccessibilityNodeInfo): Boolean {
        val label = listOfNotNull(
            node.hintText?.toString(),
            node.contentDescription?.toString(),
            node.viewIdResourceName
        ).joinToString(" ").lowercase(Locale.ROOT)
        return label.contains("提取码") ||
            label.contains("访问码") ||
            label.contains("提取密码") ||
            label.contains("sharecode") ||
            label.contains("accesscode") ||
            label.contains("passcode") ||
            label.contains("pwd")
    }

    private fun hasAccessCodePagePrompt(node: AccessibilityNodeInfo): Boolean {
        val pageText = listOfNotNull(
            node.text?.toString(),
            node.hintText?.toString(),
            node.contentDescription?.toString()
        ).joinToString(" ").lowercase(Locale.ROOT)
        return pageText.contains("请输入提取码") ||
            pageText.contains("输入提取码") ||
            pageText.contains("请输入访问码") ||
            pageText.contains("输入访问码")
    }

    private companion object {
        const val MAX_INSPECTED_NODES = 180
    }
}
