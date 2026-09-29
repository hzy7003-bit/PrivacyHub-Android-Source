package com.privacyhub.presentation

import android.app.Activity
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.privacyhub.data.repository.LocalStore
import com.privacyhub.data.settings.AppSettingsStore
import com.privacyhub.presentation.backup.BackupActivity
import com.privacyhub.presentation.category.CategoryDetailActivity
import com.privacyhub.presentation.category.CategoryManagementActivity
import com.privacyhub.presentation.category.MoreCategoriesActivity
import com.privacyhub.presentation.search.SearchResultsActivity
import com.privacyhub.presentation.transfer.TransferActivity
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Exercises the Activity result paths backed by real SQLCipher storage on API 35. */
@RunWith(AndroidJUnit4::class)
class AsyncDataScreenTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val store by lazy { LocalStore(context) }
    private val settings by lazy { AppSettingsStore(context) }
    private val suffix = UUID.randomUUID().toString().take(8)
    private val marker = "async-screen-$suffix"
    private val category = "异步标签-$suffix"
    private var savedItemId: Long? = null
    private var lockBefore = false
    private var noticeBefore: String? = null

    @Before
    fun setup() {
        lockBefore = settings.isSecurityLockEnabled()
        noticeBefore = settings.lastSeenTrialNoticeVersion()
        settings.setSecurityLockEnabled(false)
        settings.setLastSeenTrialNoticeVersion(
            com.privacyhub.presentation.guide.GuideContent.noticeKey(com.privacyhub.BuildConfig.VERSION_NAME)
        )
        settings.addCategory(category)
        savedItemId = store.saveManualItem(marker, marker, category, "")
        instrumentation.uiAutomation.executeShellCommand(
            "am start -W -n com.privacyhub/.presentation.MainActivity"
        ).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        instrumentation.waitForIdleSync()
    }

    @After
    fun cleanup() {
        savedItemId?.let(store::deleteItem)
        settings.removeCustomCategory(category)
        settings.setSecurityLockEnabled(lockBefore)
        settings.setLastSeenTrialNoticeVersion(noticeBefore)
    }

    @Test
    fun searchAndCategoryDetailRenderAsyncDatabaseResults() {
        ActivityScenario.launch<SearchResultsActivity>(
            Intent(context, SearchResultsActivity::class.java)
                .putExtra(SearchResultsActivity.EXTRA_QUERY, marker)
        ).use { scenario ->
            awaitText(scenario, marker)
        }

        ActivityScenario.launch<CategoryDetailActivity>(
            Intent(context, CategoryDetailActivity::class.java)
                .putExtra(CategoryDetailActivity.EXTRA_MODE, CategoryDetailActivity.MODE_CATEGORY)
                .putExtra(CategoryDetailActivity.EXTRA_CATEGORY, category)
        ).use { scenario ->
            awaitText(scenario, marker)
        }
    }

    @Test
    fun mainVaultAndTransferRenderAsyncDatabaseResults() {
        ActivityScenario.launch<MainActivity>(
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_TAB, MainActivity.VAULT)
        ).use { scenario ->
            awaitText(scenario, marker)
        }

        ActivityScenario.launch<TransferActivity>(
            Intent(context, TransferActivity::class.java)
                .putExtra(TransferActivity.EXTRA_ITEM_ID, savedItemId!!)
        ).use { scenario ->
            awaitText(scenario, marker)
        }
    }

    @Test
    fun categoryManagementAndMoreCategoriesRenderAsyncTagResults() {
        ActivityScenario.launch<CategoryManagementActivity>(Intent(context, CategoryManagementActivity::class.java))
            .use { scenario -> awaitText(scenario, category) }

        ActivityScenario.launch<MoreCategoriesActivity>(Intent(context, MoreCategoriesActivity::class.java))
            .use { scenario -> awaitText(scenario, category) }
    }

    @Test
    fun backupEntryRendersWithoutOpeningDatabaseOnTheActivityThread() {
        ActivityScenario.launch<BackupActivity>(Intent(context, BackupActivity::class.java)).use { scenario ->
            awaitText(scenario, "选择备份文件")
            awaitText(scenario, "创建备份")
        }
    }

    private fun <T : Activity> awaitText(scenario: ActivityScenario<T>, expected: String) {
        val deadline = System.currentTimeMillis() + 10_000
        var found = false
        while (!found && System.currentTimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                found = visibleText(activity).any { it.contains(expected) }
            }
            if (!found) Thread.sleep(40)
        }
        assertTrue("Expected visible UI text: $expected", found)
    }

    private fun visibleText(activity: Activity): List<String> {
        fun descend(view: View): List<View> = listOf(view) +
            if (view is ViewGroup) (0 until view.childCount).flatMap { descend(view.getChildAt(it)) } else emptyList()
        return descend(activity.window.decorView)
            .filterIsInstance<TextView>()
            .map { it.text?.toString().orEmpty() }
    }
}
