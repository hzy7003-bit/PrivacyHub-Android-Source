package com.privacyhub.presentation

import android.app.Activity
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.privacyhub.data.repository.LocalStore
import com.privacyhub.data.settings.AppSettingsStore
import com.privacyhub.domain.model.EntryPoint
import com.privacyhub.domain.model.LinkTarget
import com.privacyhub.domain.model.TestReport
import com.privacyhub.presentation.share.ShareReceiverActivity
import com.privacyhub.presentation.share.ProcessTextReceiverActivity
import com.privacyhub.presentation.transfer.TransferActivity
import com.privacyhub.presentation.vault.VaultDetailActivity
import com.privacyhub.security.lock.AppLockSession
import androidx.lifecycle.Lifecycle
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Real encrypted storage + Android lifecycle. Only records created by this test are removed. */
@RunWith(AndroidJUnit4::class)
class UiFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val store by lazy { LocalStore(context) }
    private val settings by lazy { AppSettingsStore(context) }
    private val marker = "UI-review-" + UUID.randomUUID().toString()
    private val created = mutableListOf<Long>()
    private var lockBefore = false
    private var noticeBefore: String? = null
    private var homePreviewBefore = false

    @Before fun setup() {
        instrumentation.sendStatus(2, android.os.Bundle().apply { putString("stream", "UI setup\n") })
        lockBefore = settings.isSecurityLockEnabled()
        noticeBefore = settings.lastSeenTrialNoticeVersion()
        homePreviewBefore = settings.isHomePreviewEnabled()
        settings.setLastSeenTrialNoticeVersion(com.privacyhub.presentation.guide.GuideContent.noticeKey(com.privacyhub.BuildConfig.VERSION_NAME))
        settings.setSecurityLockEnabled(false)
        settings.setHomePreviewEnabled(false)
        // Vivo may block background Activity launches even under instrumentation.
        // Enter through the exported launcher first; no system security settings are changed.
        instrumentation.uiAutomation.executeShellCommand("am start -W -n com.privacyhub/.presentation.MainActivity").use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
        }
        instrumentation.waitForIdleSync()
    }

    @After fun cleanup() {
        created.forEach(store::deleteItem)
        settings.setSecurityLockEnabled(lockBefore)
        settings.setLastSeenTrialNoticeVersion(noticeBefore)
        settings.setHomePreviewEnabled(homePreviewBefore)
    }

    @Test fun settingsReportUpdatesPreserveSectionsAndReplaceOnlyReports() {
        ActivityScenario.launch<SettingsActivity>(Intent(context, SettingsActivity::class.java)).use { scenario ->
            scenario.onActivity(::assertSettingsSectionsPresent)
            awaitSettingsReportsLoaded(scenario)

            val firstReport = "settings-report-first-$marker"
            val latestReport = "settings-report-latest-$marker"
            scenario.onActivity { activity ->
                val host = settingsReportHost(activity)
                activity.renderRecentReports(host, Result.success(listOf(report(firstReport), report("second-$marker"))))
                activity.renderRecentReports(host, Result.success(listOf(report(latestReport))))

                val reportTexts = visibleTexts(host)
                assertTrue(reportTexts.any { it.contains(latestReport) })
                assertFalse(reportTexts.any { it.contains(firstReport) })
                assertEquals("A repeated report callback should replace, not stack, report cards", 1, host.childCount)
                assertSettingsSectionsPresent(activity)
            }
        }
    }

    @Test fun settingsToggleAndPauseResumeKeepControlsAlongsideReportCallback() {
        ActivityScenario.launch<SettingsActivity>(Intent(context, SettingsActivity::class.java)).use { scenario ->
            awaitSettingsReportsLoaded(scenario)
            scenario.onActivity { activity ->
                assertSettingsSectionsPresent(activity)
                nodes(activity).filterIsInstance<Button>().first { it.text.toString() == "显示首页预览" }.performClick()
                assertTrue(settings.isHomePreviewEnabled())
                val host = settingsReportHost(activity)
                activity.renderRecentReports(host, Result.success(listOf(report("toggle-report-$marker"))))
                assertTrue(visibleTexts(host).any { it.contains("toggle-report-$marker") })
                assertSettingsSectionsPresent(activity)
            }
            awaitText(scenario, "隐藏首页预览")
            awaitSettingsReportsLoaded(scenario)

            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity(::assertSettingsSectionsPresent)
        }
    }

    @Test fun destroyedSettingsActivityDoesNotUpdateItsOldReportViews() {
        var oldActivity: SettingsActivity? = null
        var oldHost: LinearLayout? = null
        val beforeDestroy = "before-destroy-$marker"
        ActivityScenario.launch<SettingsActivity>(Intent(context, SettingsActivity::class.java)).use { scenario ->
            awaitSettingsReportsLoaded(scenario)
            scenario.onActivity { activity ->
                oldActivity = activity
                oldHost = settingsReportHost(activity)
                activity.renderRecentReports(oldHost!!, Result.success(listOf(report(beforeDestroy))))
            }
        }

        assertTrue(oldActivity?.isDestroyed == true)
        instrumentation.runOnMainSync {
            oldActivity!!.renderRecentReports(oldHost!!, Result.success(listOf(report("after-destroy-$marker"))))
        }
        assertTrue(visibleTexts(oldHost!!).any { it.contains(beforeDestroy) })
        assertFalse(visibleTexts(oldHost!!).any { it.contains("after-destroy-$marker") })
    }

    @Test fun savedTransferSurvivesRecreationWithoutInsertingAgain() {
        val id = fixture()
        ActivityScenario.launch<TransferActivity>(Intent(context, TransferActivity::class.java)
            .putExtra(TransferActivity.EXTRA_ITEM_ID, id)).use { scenario ->
            awaitText(scenario, marker)
            scenario.recreate()
            awaitText(scenario, marker)
            assertEquals(1, store.searchItems(marker, 10).size)
            assertEquals(marker, store.getItem(id)?.content)
        }
    }

    @Test fun editingSurvivesRecreationAndSaveUpdatesTheSameRow() {
        val id = fixture()
        ActivityScenario.launch<VaultDetailActivity>(Intent(context, VaultDetailActivity::class.java)
            .putExtra(VaultDetailActivity.EXTRA_ITEM_ID, id)).use { scenario ->
            scenario.onActivity { activity ->
                nodes(activity).filterIsInstance<Button>().first { it.text.startsWith("编辑") }.performClick()
                nodes(activity).filterIsInstance<EditText>().first { it.hint == "保存的内容" }.setText("$marker edited")
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertEquals("$marker edited", nodes(activity).filterIsInstance<EditText>()
                    .first { it.hint == "保存的内容" }.text.toString())
                nodes(activity).filterIsInstance<Button>().first { it.text == "保存修改" }.performClick()
            }
            awaitStoredContent(id, "$marker edited")
            assertEquals("$marker edited", store.getItem(id)?.content)
            assertEquals(1, store.searchItems(marker, 10).size)
        }
    }

    @Test fun externalShareSavesOnceAndOpensSavedTransfer() {
        val monitor = instrumentation.addMonitor(TransferActivity::class.java.name, null, false)
        try {
            context.startActivity(Intent(context, ShareReceiverActivity::class.java)
                .setAction(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, marker).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val activity = instrumentation.waitForMonitorWithTimeout(monitor, 8000)
            assertNotNull("Share must reach saved transfer", activity)
            val records = store.searchItems(marker, 10)
            created.addAll(records.map { it.id })
            assertEquals(1, records.size)
            assertEquals(marker, records.single().originalText)
            instrumentation.runOnMainSync { activity?.finish() }
        } finally {
            instrumentation.removeMonitor(monitor)
            // Also clean our fixture if the assertion failed after persistence.
            store.searchItems(marker, 10).map { it.id }.filterNot(created::contains).forEach(created::add)
        }
    }

    @Test fun processTextShareSavesOnceAndOpensSavedTransfer() {
        val processText = "process-text-$marker"
        val monitor = instrumentation.addMonitor(TransferActivity::class.java.name, null, false)
        try {
            context.startActivity(Intent(context, ProcessTextReceiverActivity::class.java)
                .setAction(Intent.ACTION_PROCESS_TEXT)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_PROCESS_TEXT, processText)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val activity = instrumentation.waitForMonitorWithTimeout(monitor, 8000)
            assertNotNull("ACTION_PROCESS_TEXT must reach saved transfer", activity)
            val records = store.searchItems(processText, 10)
            created.addAll(records.map { it.id })
            assertEquals(1, records.size)
            assertEquals(processText, records.single().originalText)
            instrumentation.runOnMainSync { activity?.finish() }
        } finally {
            instrumentation.removeMonitor(monitor)
            store.searchItems(processText, 10).map { it.id }.filterNot(created::contains).forEach(created::add)
        }
    }

    @Test fun unsupportedActionDoesNotSaveOrAlterExistingVaultData() {
        val existingId = fixture()
        val rejectedText = "invalid-action-$marker"

        repeat(3) {
            val intent = Intent(context, ShareReceiverActivity::class.java)
                .setAction("com.example.UNEXPECTED")
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, rejectedText)
            ActivityScenario.launch<ShareReceiverActivity>(intent).use { scenario ->
                awaitText(scenario, "不支持的输入方式")
                assertTrue(store.searchItems(rejectedText, 10).isEmpty())
                assertEquals(marker, store.getItem(existingId)?.content)
            }
        }
    }

    @Test fun malformedShareExtraDoesNotCrashOrSave() {
        val existingId = fixture()
        val intent = Intent(context, ShareReceiverActivity::class.java)
            .setAction(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, 42)

        ActivityScenario.launch<ShareReceiverActivity>(intent).use { scenario ->
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val status = nodes(activity).filterIsInstance<TextView>().map { it.text.toString() }
                assertTrue(status.any { it.contains("没有收到文本") || it.contains("无法读取分享内容") })
            }
            assertEquals(1, store.searchItems(marker, 10).size)
            assertEquals(marker, store.getItem(existingId)?.content)
        }
    }

    @Test fun oversizedShareIsRejectedWithoutChangingExistingVaultData() {
        val existingId = fixture()
        val uniquePrefix = "oversized-$marker"
        val oversizedText = uniquePrefix + "x".repeat(
            com.privacyhub.presentation.share.ShareTextInputPolicy.MAX_TEXT_LENGTH + 1
        )
        val intent = Intent(context, ShareReceiverActivity::class.java)
            .setAction(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, oversizedText)

        ActivityScenario.launch<ShareReceiverActivity>(intent).use { scenario ->
            awaitText(scenario, "分享内容过长")
            assertTrue(store.searchItems(uniquePrefix, 10).isEmpty())
            assertEquals(marker, store.getItem(existingId)?.content)
        }
    }

    @Test fun deletedTransferShowsMissingInsteadOfSavingAgain() {
        val id = fixture()
        store.deleteItem(id)
        ActivityScenario.launch<TransferActivity>(Intent(context, TransferActivity::class.java)
            .putExtra(TransferActivity.EXTRA_ITEM_ID, id)).use { scenario ->
            awaitText(scenario, "内容已不存在")
            assertTrue(store.searchItems(marker, 10).isEmpty())
        }
    }

    @Test fun lockedEditorBackCannotExposeCachedVaultContent() {
        val id = fixture()
        ActivityScenario.launch<VaultDetailActivity>(Intent(context, VaultDetailActivity::class.java)
            .putExtra(VaultDetailActivity.EXTRA_ITEM_ID, id)).use { scenario ->
            scenario.onActivity { activity ->
                nodes(activity).filterIsInstance<Button>().first { it.text.startsWith("编辑") }.performClick()
            }
            scenario.moveToState(Lifecycle.State.CREATED)
            settings.setSecurityLockEnabled(true)
            AppLockSession.lockNow()
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                assertFalse(nodes(activity).filterIsInstance<TextView>().any { it.text.toString().contains(marker) })
                activity.onBackPressedDispatcher.onBackPressed()
                assertTrue("Locked editor must finish instead of rendering cached details", activity.isFinishing)
            }
        }
    }

    @Test fun navigationTouchTargetsAreFullyVisible() {
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            scenario.onActivity { activity ->
                nodes(activity).filterIsInstance<Button>().filter { it.text.toString() in listOf("中转", "安全箱", "设置") }
                    .forEach { button ->
                        val rect = android.graphics.Rect()
                        assertTrue(button.getGlobalVisibleRect(rect))
                        val minimum = (48 * activity.resources.displayMetrics.density).toInt()
                        assertTrue("Navigation ${button.text}: visible=${rect.height()}, height=${button.height}, minimum=$minimum",
                            rect.height() >= minimum)
                    }
            }
        }
    }

    @Test fun manualInputSurvivesRotationWithoutPersistingDraft() {
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            scenario.onActivity { activity ->
                nodes(activity).filterIsInstance<Button>().first { it.contentDescription == "添加一段内容" }.performClick()
            }
            instrumentation.waitForIdleSync()
            val manual = instrumentation.uiAutomation.rootInActiveWindow
                .findAccessibilityNodeInfosByText("手动输入").first()
            assertTrue(manual.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
            instrumentation.waitForIdleSync()
            fun editable(node: android.view.accessibility.AccessibilityNodeInfo): android.view.accessibility.AccessibilityNodeInfo? {
                if (node.isEditable) return node
                for (i in 0 until node.childCount) node.getChild(i)?.let { editable(it)?.let { found -> return found } }
                return null
            }
            val field = editable(instrumentation.uiAutomation.rootInActiveWindow)!!
            assertTrue(field.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT,
                android.os.Bundle().apply {
                    putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, marker)
                }))
            scenario.recreate()
            instrumentation.waitForIdleSync()
            assertEquals(marker, editable(instrumentation.uiAutomation.rootInActiveWindow)?.text?.toString())
            assertTrue("Typing must not save until submission", store.searchItems(marker, 10).isEmpty())
        }
    }

    private fun fixture(): Long {
        instrumentation.sendStatus(2, android.os.Bundle().apply { putString("stream", "Creating isolated UI fixture\n") })
        return store.saveManualItem(marker, marker, "其他", "").also {
            created.add(it)
            instrumentation.sendStatus(2, android.os.Bundle().apply { putString("stream", "UI fixture saved\n") })
        }
    }

    private fun nodes(activity: Activity): List<View> {
        fun descend(view: View): List<View> = listOf(view) +
            if (view is ViewGroup) (0 until view.childCount).flatMap { descend(view.getChildAt(it)) } else emptyList()
        return descend(activity.window.decorView)
    }

    private fun assertSettingsSectionsPresent(activity: SettingsActivity) {
        val buttonLabels = nodes(activity).filterIsInstance<Button>().map { it.text.toString() }.toSet()
        assertTrue("Category controls should remain rendered", "管理分类" in buttonLabels)
        assertTrue("Home tag controls should remain rendered", "管理首页标签" in buttonLabels)
        assertTrue("Privacy settings should remain rendered", "显示首页预览" in buttonLabels || "隐藏首页预览" in buttonLabels)
        assertTrue("Report section should remain rendered", nodes(activity).filterIsInstance<TextView>()
            .any { it.text.toString() == "最近测试报告" })
    }

    private fun settingsReportHost(activity: SettingsActivity): LinearLayout {
        val title = nodes(activity).filterIsInstance<TextView>()
            .first { it.text.toString() == "最近测试报告" }
        val parent = title.parent as ViewGroup
        val nextIndex = parent.indexOfChild(title) + 1
        return parent.getChildAt(nextIndex) as LinearLayout
    }

    private fun report(note: String) = TestReport(
        id = -1,
        createdAtMillis = System.currentTimeMillis(),
        entryPoint = EntryPoint.MANUAL,
        androidVersion = "15",
        sdkInt = 35,
        brand = "test-brand",
        manufacturer = "test-manufacturer",
        model = "test-model",
        rom = "instrumentation",
        success = true,
        failureReason = null,
        target = LinkTarget.WEB,
        note = note
    )

    private fun visibleTexts(root: View): List<String> {
        fun descend(view: View): List<View> = listOf(view) +
            if (view is ViewGroup) (0 until view.childCount).flatMap { descend(view.getChildAt(it)) } else emptyList()
        return descend(root).filterIsInstance<TextView>().map { it.text?.toString().orEmpty() }
    }

    private fun awaitSettingsReportsLoaded(scenario: ActivityScenario<SettingsActivity>) {
        val deadline = System.currentTimeMillis() + 8_000
        var loaded = false
        while (!loaded && System.currentTimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val host = settingsReportHost(activity)
                loaded = host.childCount > 0 && visibleTexts(host).none { it.contains("正在读取测试报告") }
            }
            if (!loaded) Thread.sleep(50)
        }
        assertTrue("Recent reports should finish their asynchronous render", loaded)
    }

    private fun <T : Activity> awaitText(scenario: ActivityScenario<T>, text: String) {
        val deadline = System.currentTimeMillis() + 8000
        var found = false
        while (!found && System.currentTimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity -> found = nodes(activity).filterIsInstance<TextView>().any { it.text.toString().contains(text) } }
            if (!found) Thread.sleep(50)
        }
        assertTrue("Expected visible state: $text", found)
    }

    private fun awaitStoredContent(id: Long, expected: String) {
        val deadline = System.currentTimeMillis() + 8_000
        var actual = store.getItem(id)?.content
        while (actual != expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
            actual = store.getItem(id)?.content
        }
        assertEquals(expected, actual)
    }
}
