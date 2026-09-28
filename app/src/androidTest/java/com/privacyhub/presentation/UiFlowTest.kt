package com.privacyhub.presentation

import android.app.Activity
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.privacyhub.data.repository.LocalStore
import com.privacyhub.data.settings.AppSettingsStore
import com.privacyhub.presentation.share.ShareReceiverActivity
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

    @Before fun setup() {
        instrumentation.sendStatus(2, android.os.Bundle().apply { putString("stream", "UI setup\n") })
        lockBefore = settings.isSecurityLockEnabled()
        noticeBefore = settings.lastSeenTrialNoticeVersion()
        settings.setLastSeenTrialNoticeVersion(com.privacyhub.presentation.guide.GuideContent.noticeKey(com.privacyhub.BuildConfig.VERSION_NAME))
        settings.setSecurityLockEnabled(false)
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
}
