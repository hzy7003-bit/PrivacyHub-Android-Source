package com.privacyhub.presentation

import android.accessibilityservice.AccessibilityServiceInfo
import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.privacyhub.data.repository.LocalStore
import com.privacyhub.data.settings.AppSettingsStore
import com.privacyhub.domain.model.LinkTarget
import com.privacyhub.presentation.notification.NotificationEntryActivity
import com.privacyhub.presentation.notification.PersistentNotificationServicePolicy
import com.privacyhub.presentation.notification.SaveNotificationManager
import com.privacyhub.router.LinkParser
import com.privacyhub.router.LinkTargetDetector
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Device acceptance for the notification actions; restores the saved opt-in state afterward. */
@RunWith(AndroidJUnit4::class)
class NotificationInteractionTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val context = inst.targetContext
    private val ui get() = inst.uiAutomation

    @Test fun bothCollapsedActionsDispatchTheirOwnIntentAndSaveOnce() {
        val store = LocalStore(context)
        val settings = AppSettingsStore(context)
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val saveNotificationManager = SaveNotificationManager(context)
        val previousPersistentSetting = settings.isPersistentNotificationEnabled()
        val marker = "$TEST_MARKER_PREFIX${UUID.randomUUID()}"
        val saveText = "$marker-save"
        val openText = "$marker-open"
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val info = ui.serviceInfo
        val previousFlags = info.flags
        var previousClip: ClipData? = null
        var clipCaptured = false
        try {
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            ui.serviceInfo = info
            cleanupAcceptanceTestItems(store)

            assertTrue(
                "Could not isolate the notification prerequisite",
                settings.setPersistentNotificationEnabled(false, synchronous = true)
            )
            saveNotificationManager.cancel()
            assertTrue("Previous persistent service did not stop", awaitServiceStopped(5000))
            assertTrue(
                "Notification remained active after the service was stopped",
                notificationManager.activeNotifications.none {
                    it.packageName == context.packageName && it.id == SaveNotificationManager.NOTIFICATION_ID
                }
            )

            bringMainActivityToForeground()
            inst.runOnMainSync { previousClip = clipboard.primaryClip; clipCaptured = true }

            val notificationPermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            assertTrue("POST_NOTIFICATIONS permission is required for this acceptance test", notificationPermissionGranted)
            assertTrue("App notifications are disabled", notificationManager.areNotificationsEnabled())
            notificationManager.getNotificationChannel(SaveNotificationManager.CHANNEL_ID)?.let { channel ->
                assertTrue("Persistent notification channel is blocked", channel.importance != NotificationManager.IMPORTANCE_NONE)
            }

            assertTrue(
                "Could not enable the notification prerequisite through AppSettingsStore",
                settings.setPersistentNotificationEnabled(true, synchronous = true)
            )
            assertTrue("Persistent notification setting was not enabled", settings.isPersistentNotificationEnabled())
            val expectedPolicyDecision = PersistentNotificationServicePolicy.decide(
                action = PersistentNotificationServicePolicy.ACTION_START,
                persistentEnabled = settings.isPersistentNotificationEnabled(),
                notificationPermissionGranted = notificationPermissionGranted
            )
            assertEquals(
                PersistentNotificationServicePolicy.Decision.START_FOREGROUND,
                expectedPolicyDecision
            )

            val requestStartedAt = SystemClock.elapsedRealtime()
            saveNotificationManager.showPersistentSaveNotification()
            val requestReturnedAt = SystemClock.elapsedRealtime()
            val postedDeadline = requestReturnedAt + NOTIFICATION_POST_TIMEOUT_MS
            var postedNotification = notificationManager.activeNotifications.firstOrNull {
                it.packageName == context.packageName && it.id == SaveNotificationManager.NOTIFICATION_ID
            }
            while (postedNotification == null && SystemClock.elapsedRealtime() < postedDeadline) {
                Thread.sleep(NOTIFICATION_POLL_INTERVAL_MS)
                postedNotification = notificationManager.activeNotifications.firstOrNull {
                    it.packageName == context.packageName && it.id == SaveNotificationManager.NOTIFICATION_ID
                }
            }
            val notificationVisibleAt = SystemClock.elapsedRealtime()
            val diagnostics = notificationStartupDiagnostics(
                notificationManager = notificationManager,
                notificationPermissionGranted = notificationPermissionGranted,
                policyDecision = expectedPolicyDecision,
                requestReturnMs = requestReturnedAt - requestStartedAt,
                postElapsedMs = if (postedNotification == null) null else notificationVisibleAt - requestReturnedAt
            )
            assertNotNull("Notification must be posted before opening the shade. $diagnostics", postedNotification)
            assertEquals(context.packageName, postedNotification!!.packageName)

            Log.i(ACCEPTANCE_LOG_TAG, diagnostics)
            for ((viewId, action) in listOf(
                "notification_save" to SaveNotificationManager.ACTION_SAVE_FROM_NOTIFICATION,
                "notification_save_open" to SaveNotificationManager.ACTION_OPEN_FROM_NOTIFICATION
            )) {
                bringMainActivityToForeground()
                val text = if (viewId.endsWith("_open")) openText else saveText
                if (action == SaveNotificationManager.ACTION_OPEN_FROM_NOTIFICATION) {
                    assertNull("OPEN action fixture must not contain a URL", LinkParser.extractFirstUrl(text))
                    assertEquals("OPEN action fixture must not resolve to an app", LinkTarget.UNKNOWN, LinkTargetDetector.detect(text))
                }
                inst.runOnMainSync { clipboard.setPrimaryClip(ClipData.newPlainText("UI test", text)) }
                shell("input keyevent KEYCODE_HOME")
                shell("cmd statusbar expand-notifications")
                val button = ensureCollapsedAction("com.privacyhub:id/$viewId")
                val monitor = inst.addMonitor(NotificationEntryActivity::class.java.name, null, false)
                val routeMonitor = if (action == SaveNotificationManager.ACTION_OPEN_FROM_NOTIFICATION) {
                    inst.addMonitor(IntentFilter(Intent.ACTION_VIEW), null, false)
                } else null
                try {
                    assertTrue(button.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                    val entry = inst.waitForMonitorWithTimeout(monitor, 5000)
                    assertNotNull("Button must dispatch an Activity PendingIntent", entry)
                    assertEquals(action, entry!!.intent.action)
                    val deadline = SystemClock.elapsedRealtime() + ACTION_SAVE_TIMEOUT_MS
                    var rows = exactContentMatches(store, text)
                    while (rows.isEmpty() && SystemClock.elapsedRealtime() < deadline) {
                        Thread.sleep(NOTIFICATION_POLL_INTERVAL_MS)
                        rows = exactContentMatches(store, text)
                    }
                    assertEquals("Each action saves exactly once", 1, rows.size)
                    assertSingleSavedRowRemains(store, text)

                    if (routeMonitor != null) {
                        assertNoThirdPartyActivityForeground()
                        assertEquals("Unroutable OPEN fixture must not launch ACTION_VIEW", 0, routeMonitor.hits)
                    }
                } finally {
                    routeMonitor?.let(inst::removeMonitor)
                    inst.removeMonitor(monitor)
                }
            }
        } finally {
            try {
                cleanupAcceptanceTestItems(store)
            } finally {
                try {
                    if (clipCaptured) {
                        bringMainActivityToForeground()
                        inst.runOnMainSync {
                            previousClip?.let(clipboard::setPrimaryClip) ?: run {
                                if (android.os.Build.VERSION.SDK_INT >= 28) clipboard.clearPrimaryClip()
                                else clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                            }
                        }
                    }
                } finally {
                    try {
                        info.flags = previousFlags
                        ui.serviceInfo = info
                    } finally {
                        val restored = settings.setPersistentNotificationEnabled(
                            previousPersistentSetting,
                            synchronous = true
                        )
                        if (previousPersistentSetting) {
                            saveNotificationManager.showPersistentSaveNotification()
                        } else {
                            saveNotificationManager.cancel()
                        }
                        assertTrue("Could not restore persistent notification setting", restored)
                    }
                }
            }
        }
    }

    private fun awaitNode(id: String): AccessibilityNodeInfo {
        repeat(40) {
            ui.rootInActiveWindow?.let { root ->
                val label = if (id.endsWith("_open")) "保存并跳转" else "保存到安全箱"
                nodes(root).firstOrNull { it.viewIdResourceName == id ||
                    (it.text?.toString() == label && it.isClickable) }?.let { return it }
            }
            Thread.sleep(100)
        }
        val root = ui.rootInActiveWindow
        error("Missing visible notification action: $id; window=${root?.packageName}; " +
            "own notification title visible=${root?.let { nodes(it).any { node -> node.text?.toString() == "隐私中转站" } }}")
    }

    private fun ensureCollapsedAction(id: String): AccessibilityNodeInfo {
        var button = awaitNode(id)
        var card = notificationCard(button)
        var expander = nodes(card).firstOrNull { it.viewIdResourceName == "android:id/expand_button" }
            ?: error("Notification expansion control missing")
        val actionOwners = listOf(expander, card)
        val collapseOwner = actionOwners.firstOrNull { supportsAction(it, AccessibilityNodeInfo.ACTION_COLLAPSE) }
        val expandOwner = actionOwners.firstOrNull { supportsAction(it, AccessibilityNodeInfo.ACTION_EXPAND) }

        when {
            collapseOwner != null -> {
                assertTrue("Expanded notification must collapse", collapseOwner.performAction(AccessibilityNodeInfo.ACTION_COLLAPSE))
                inst.waitForIdleSync()
                Thread.sleep(400)
                button = awaitNode(id)
                card = notificationCard(button)
                expander = nodes(card).firstOrNull { it.viewIdResourceName == "android:id/expand_button" }
                    ?: error("Notification expansion control missing after collapse")
                assertTrue(
                    "Collapsed notification must expose ACTION_EXPAND",
                    listOf(expander, card).any { supportsAction(it, AccessibilityNodeInfo.ACTION_EXPAND) }
                )
            }
            expandOwner != null -> Unit // ACTION_EXPAND is the locale-independent collapsed-state signal.
            else -> {
                // This API 35 SystemUI exposes only ACTION_CLICK for expand_button. Toggle once,
                // compare the same card's bounds, and leave it in the shorter collapsed layout.
                val initialHeight = Rect().also(card::getBoundsInScreen).height()
                assertTrue("Notification expansion control must be clickable", expander.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                inst.waitForIdleSync()
                Thread.sleep(400)
                button = awaitNode(id)
                card = notificationCard(button)
                val toggledHeight = Rect().also(card::getBoundsInScreen).height()
                assertNotEquals("Toggling notification expansion must change card height", initialHeight, toggledHeight)

                if (toggledHeight > initialHeight) {
                    expander = nodes(card).firstOrNull { it.viewIdResourceName == "android:id/expand_button" }
                        ?: error("Notification expansion control missing after expand")
                    assertTrue(expander.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                    inst.waitForIdleSync()
                    Thread.sleep(400)
                    button = awaitNode(id)
                    card = notificationCard(button)
                    val collapsedHeight = Rect().also(card::getBoundsInScreen).height()
                    assertEquals("Notification must return to its collapsed bounds", initialHeight, collapsedHeight)
                    assertTrue("Collapsed notification must be shorter than expanded", toggledHeight > collapsedHeight)
                } else {
                    assertTrue("Collapsed notification must be shorter than expanded", initialHeight > toggledHeight)
                }
            }
        }
        return awaitNode(id)
    }

    private fun notificationCard(node: AccessibilityNodeInfo): AccessibilityNodeInfo {
        var card = node
        while (card.viewIdResourceName != "android:id/status_bar_latest_event_content") {
            card = card.parent ?: error("Notification template missing")
        }
        return card
    }

    private fun supportsAction(node: AccessibilityNodeInfo, action: Int): Boolean =
        node.actionList.any { it.id == action }

    private fun bringMainActivityToForeground(timeoutMs: Long = MAIN_ACTIVITY_FOREGROUND_TIMEOUT_MS) {
        val intent = Intent(context, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        )
        inst.runOnMainSync { context.startActivity(intent) }

        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            var mainActivityResumed = false
            inst.runOnMainSync {
                mainActivityResumed = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)
                    .any { it is MainActivity && !it.isFinishing && !it.isDestroyed }
            }
            val activeWindowPackage = ui.rootInActiveWindow?.packageName?.toString()
            if (mainActivityResumed || activeWindowPackage == context.packageName) return
            Thread.sleep(NOTIFICATION_POLL_INTERVAL_MS)
        }
        error(
            "MainActivity did not become foreground within ${timeoutMs}ms; " +
                "activeWindow=${ui.rootInActiveWindow?.packageName}"
        )
    }

    private fun assertNoThirdPartyActivityForeground(timeoutMs: Long = ACTION_RETURN_TIMEOUT_MS) {
        val homePackage = shell(
            "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
        ).decodeToString().lineSequence()
            .mapNotNull { HOME_COMPONENT.matchEntire(it.trim())?.groupValues?.get(1) }
            .firstOrNull()
            ?: error("Could not resolve the current HOME package")
        val allowedPackages = setOf(context.packageName, homePackage, "com.android.systemui")
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        var lastResumedPackage: String? = null
        while (SystemClock.elapsedRealtime() < deadline) {
            val activityDump = shell("dumpsys activity activities").decodeToString()
            val resumedLine = activityDump.lineSequence().firstOrNull {
                it.contains("topResumedActivity=")
            } ?: activityDump.lineSequence().firstOrNull { it.contains("mResumedActivity:") }
            lastResumedPackage = resumedLine?.let { RESUMED_PACKAGE.find(it)?.groupValues?.get(1) }
            if (lastResumedPackage != null && lastResumedPackage !in allowedPackages) {
                error("OPEN notification action launched a third-party Activity: $lastResumedPackage")
            }
            if (lastResumedPackage == homePackage) return
            Thread.sleep(NOTIFICATION_POLL_INTERVAL_MS)
        }
        error("OPEN action did not return to HOME; resumed=$lastResumedPackage")
    }

    private fun exactContentMatches(store: LocalStore, content: String) =
        store.searchItems(content, 100).filter { store.getItem(it.id)?.content == content }

    private fun assertSingleSavedRowRemains(store: LocalStore, content: String) {
        val deadline = SystemClock.elapsedRealtime() + DUPLICATE_SAVE_OBSERVATION_MS
        while (true) {
            assertEquals("Action must not create a duplicate row", 1, exactContentMatches(store, content).size)
            if (SystemClock.elapsedRealtime() >= deadline) return
            Thread.sleep(NOTIFICATION_POLL_INTERVAL_MS)
        }
    }

    /** Remove only clearly test-marked rows, including the old URL fixture from an interrupted run. */
    private fun cleanupAcceptanceTestItems(store: LocalStore) {
        store.searchItems(TEST_MARKER_PREFIX, 500).forEach { candidate ->
            val content = store.getItem(candidate.id)?.content ?: return@forEach
            if (content.startsWith(TEST_MARKER_PREFIX) ||
                content.startsWith(LEGACY_OPEN_URL_PREFIX + TEST_MARKER_PREFIX)
            ) {
                store.deleteItem(candidate.id)
            }
        }
    }

    private fun awaitServiceStopped(timeoutMs: Long): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            val services = shell("dumpsys activity services com.privacyhub").decodeToString()
            if (!services.contains("PersistentSaveNotificationService")) return true
            Thread.sleep(NOTIFICATION_POLL_INTERVAL_MS)
        }
        return false
    }

    private fun notificationStartupDiagnostics(
        notificationManager: NotificationManager,
        notificationPermissionGranted: Boolean,
        policyDecision: PersistentNotificationServicePolicy.Decision,
        requestReturnMs: Long,
        postElapsedMs: Long?
    ): String {
        val channelImportance = notificationManager
            .getNotificationChannel(SaveNotificationManager.CHANNEL_ID)
            ?.importance
        val active = notificationManager.activeNotifications.firstOrNull {
            it.packageName == context.packageName && it.id == SaveNotificationManager.NOTIFICATION_ID
        }
        val services = runCatching {
            shell("dumpsys activity services com.privacyhub").decodeToString()
                .lineSequence()
                .filter { line ->
                    line.contains("PersistentSaveNotificationService") ||
                        line.contains("intent={act=") ||
                        line.contains("startForegroundCount=") ||
                        line.contains("isForeground=") ||
                        line.contains("startCommandResult=")
                }
                .joinToString(" | ")
        }.getOrDefault("service snapshot unavailable")
        val systemEvents = runCatching {
            shell("logcat -d -v threadtime -t 500").decodeToString()
                .lineSequence()
                .filter { line ->
                    line.contains("Background started FGS:") ||
                        line.contains("Force stopping com.privacyhub") ||
                        line.contains("ForegroundServiceStartNotAllowedException") ||
                        line.contains("MissingForegroundServiceTypeException") ||
                        line.contains("RemoteServiceException") ||
                        line.contains("Bad notification") ||
                        line.contains("SecurityException")
                }
                .joinToString(" | ")
        }.getOrDefault("system event snapshot unavailable")
        val foregroundFlag = active?.let {
            it.notification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0
        } ?: false
        return "requestReturnMs=$requestReturnMs, postElapsedMs=$postElapsedMs, " +
            "setting=${AppSettingsStore(context).isPersistentNotificationEnabled()}, " +
            "permission=$notificationPermissionGranted, notificationsEnabled=${notificationManager.areNotificationsEnabled()}, " +
            "channelImportance=$channelImportance, expectedPolicy=$policyDecision, " +
            "activePackage=${active?.packageName}, activeId=${active?.id}, foregroundNotification=$foregroundFlag, " +
            "service=[$services], systemEvents=[$systemEvents]"
    }

    private fun nodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> = listOf(root) +
        (0 until root.childCount).mapNotNull { root.getChild(it) }.flatMap(::nodes)
    private fun shell(command: String) = ui.executeShellCommand(command).use {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
    }

    private companion object {
        const val TEST_MARKER_PREFIX = "notification-test-"
        const val LEGACY_OPEN_URL_PREFIX = "https://example.com/?"
        const val NOTIFICATION_POST_TIMEOUT_MS = 10_000L
        const val MAIN_ACTIVITY_FOREGROUND_TIMEOUT_MS = 5_000L
        const val ACTION_SAVE_TIMEOUT_MS = 5_000L
        const val ACTION_RETURN_TIMEOUT_MS = 5_000L
        const val DUPLICATE_SAVE_OBSERVATION_MS = 700L
        const val NOTIFICATION_POLL_INTERVAL_MS = 100L
        const val ACCEPTANCE_LOG_TAG = "NotificationAcceptanceTest"
        val HOME_COMPONENT = Regex("^([A-Za-z0-9_.]+)/[^/]+$")
        val RESUMED_PACKAGE = Regex("\\bu\\d+\\s+([A-Za-z0-9_.]+)/")
    }
}
