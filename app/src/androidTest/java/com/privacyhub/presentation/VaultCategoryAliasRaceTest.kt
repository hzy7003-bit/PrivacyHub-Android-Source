package com.privacyhub.presentation

import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.privacyhub.backup.PortableBackupSettings
import com.privacyhub.data.repository.LocalStore
import com.privacyhub.data.settings.AppSettingsStore
import com.privacyhub.presentation.vault.VaultDetailActivity
import com.privacyhub.util.AppExecutors
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real Activity watchers, lifecycle, save worker, settings and encrypted database; no injected UI callbacks. */
@RunWith(AndroidJUnit4::class)
class VaultCategoryAliasRaceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val settings = AppSettingsStore(context)
    private val store = LocalStore(context)
    private val marker = "category-race-${UUID.randomUUID()}"
    private val alias = "别名-${UUID.randomUUID().toString().take(8)}"
    private val manual = "手动分类"
    private lateinit var settingsBefore: PortableBackupSettings
    private var lockBefore = false
    private var blockedDisk: CountDownLatch? = null

    @Before fun setup() {
        drainDiskAndUi()
        lockBefore = settings.isSecurityLockEnabled()
        settingsBefore = settings.portableBackupSettings(emptyList())
        settings.setSecurityLockEnabled(false)
        settings.replacePortableBackupSettings(PortableBackupSettings(emptyList(), emptyMap(), emptyList()))
        assertTrue(settings.addCategory(alias))
        assertTrue(settings.setCategoryAlias("文本", alias, emptyList()))
    }

    @After fun cleanup() {
        blockedDisk?.countDown()
        drainDiskAndUi()
        store.searchItems(marker, 100).forEach { store.deleteItem(it.id) }
        settings.replacePortableBackupSettings(settingsBefore)
        settings.setSecurityLockEnabled(lockBefore)
    }

    @Test fun immediateSaveWhileAliasLookupIsPendingPersistsAlias() {
        blockDisk()
        create().use { scenario ->
            scenario.onActivity { activity ->
                content(activity).setText(marker)
                assertEquals("文本", category(activity).text.toString())
                save(activity)
            }
            releaseDisk()
            assertSavedCategory(alias)
        }
    }

    @Test fun loadedAliasIsDisplayedAndPersisted() {
        create().use { scenario ->
            drainDiskAndUi()
            scenario.onActivity { activity ->
                content(activity).setText(marker)
                assertEquals(alias, category(activity).text.toString())
                save(activity)
            }
            assertSavedCategory(alias)
        }
    }

    @Test fun userEditDuringAliasLookupWinsAfterCallbackAndSave() {
        blockDisk()
        create().use { scenario ->
            scenario.onActivity { activity ->
                content(activity).setText(marker)
                category(activity).setText(manual)
            }
            releaseDisk()
            scenario.onActivity { activity ->
                assertEquals(manual, category(activity).text.toString())
                save(activity)
            }
            assertSavedCategory(manual)
        }
    }

    @Test fun destroyedActivityDoesNotReceiveAliasCallback() {
        blockDisk()
        lateinit var oldCategory: EditText
        lateinit var oldActivity: VaultDetailActivity
        create().use { scenario ->
            scenario.onActivity { activity ->
                oldActivity = activity
                content(activity).setText(marker)
                oldCategory = category(activity)
                assertEquals("文本", oldCategory.text.toString())
            }
        }
        assertTrue(oldActivity.isDestroyed)
        releaseDisk()
        instrumentation.runOnMainSync {
            assertEquals("文本", oldCategory.text.toString())
        }
    }

    @Test fun pendingAutomaticCategoryRecreationStillResolvesAliasOnImmediateSave() {
        blockDisk()
        create().use { scenario ->
            scenario.onActivity { content(it).setText(marker) }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertEquals(marker, content(activity).text.toString())
                assertEquals("文本", category(activity).text.toString())
                save(activity)
            }
            releaseDisk()
            assertSavedCategory(alias)
        }
    }

    @Test fun pendingAutomaticCategoryRecreationUpdatesDisplayWhenAliasLoads() {
        blockDisk()
        create().use { scenario ->
            scenario.onActivity { content(it).setText(marker) }
            scenario.recreate()
            releaseDisk()
            scenario.onActivity { activity ->
                assertEquals(alias, category(activity).text.toString())
                save(activity)
            }
            assertSavedCategory(alias)
        }
    }

    @Test fun manualCategoryRecreationPreservesUserChoiceDuringLookup() {
        blockDisk()
        create().use { scenario ->
            scenario.onActivity { activity ->
                content(activity).setText(marker)
                category(activity).setText(manual)
            }
            scenario.recreate()
            releaseDisk()
            scenario.onActivity { activity ->
                assertEquals(manual, category(activity).text.toString())
                save(activity)
            }
            assertSavedCategory(manual)
        }
    }

    @Test fun existingItemEditDraftRecreationKeepsCategoryAndUpdatesSameRow() {
        val id = store.saveManualItem(marker, marker, manual, "")
        ActivityScenario.launch<VaultDetailActivity>(Intent(context, VaultDetailActivity::class.java)
            .putExtra(VaultDetailActivity.EXTRA_ITEM_ID, id)).use { scenario ->
            drainDiskAndUi()
            scenario.onActivity { activity ->
                nodes(activity.window.decorView).filterIsInstance<Button>().first { it.text == "编辑" }.performClick()
                category(activity).setText("编辑分类")
                content(activity).setText("$marker edited")
            }
            scenario.recreate()
            drainDiskAndUi()
            scenario.onActivity { activity ->
                assertEquals("编辑分类", category(activity).text.toString())
                save(activity)
            }
            drainDiskAndUi()
            assertEquals("编辑分类", store.getItem(id)?.category)
            assertEquals("$marker edited", store.getItem(id)?.content)
            assertEquals(1, store.searchItems(marker, 10).size)
        }
    }

    @Test fun defaultCategoryWithoutAliasIsPersisted() {
        settings.replacePortableBackupSettings(PortableBackupSettings(emptyList(), emptyMap(), emptyList()))
        blockDisk()
        create().use { scenario ->
            scenario.onActivity { activity -> content(activity).setText(marker); save(activity) }
            releaseDisk()
            assertSavedCategory("文本")
        }
    }

    @Test fun unreadableAliasSettingsFallBackToSystemCategory() {
        // An incomplete real encrypted settings pair exercises the store's read failure fallback.
        val preferences = context.getSharedPreferences("privacy_hub_app_settings", Context.MODE_PRIVATE)
        assertTrue(preferences.edit().remove("encrypted_category_settings_snapshot_v1_iv").commit())
        create().use { scenario ->
            drainDiskAndUi()
            assertNotNull(settings.categorySettingsReadError())
            scenario.onActivity { activity ->
                content(activity).setText(marker)
                assertEquals("文本", category(activity).text.toString())
                save(activity)
            }
            assertSavedCategory("文本")
        }
    }

    private fun create() = ActivityScenario.launch<VaultDetailActivity>(
        Intent(context, VaultDetailActivity::class.java).putExtra(VaultDetailActivity.EXTRA_CREATE_MODE, true))

    private fun blockDisk() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        blockedDisk = release
        AppExecutors.executeDisk {
            started.countDown()
            check(release.await(30, TimeUnit.SECONDS)) { "Test disk barrier timed out" }
        }
        assertTrue(started.await(10, TimeUnit.SECONDS))
    }

    private fun releaseDisk() {
        blockedDisk!!.countDown()
        blockedDisk = null
        drainDiskAndUi()
    }

    private fun drainDiskAndUi() {
        // Save completion can enqueue a second disk task to load the saved detail.
        repeat(2) {
            val delivered = CountDownLatch(1)
            AppExecutors.executeDiskResult({ Unit }) { delivered.countDown() }
            assertTrue("Disk work and main callbacks must complete", delivered.await(20, TimeUnit.SECONDS))
            instrumentation.waitForIdleSync()
        }
    }

    private fun assertSavedCategory(expected: String) {
        drainDiskAndUi()
        val saved = store.searchItems(marker, 10)
        assertEquals("Save must insert exactly once", 1, saved.size)
        assertEquals(expected, store.getItem(saved.single().id)?.category)
    }

    private fun content(activity: VaultDetailActivity) = field(activity, "保存的内容")
    private fun category(activity: VaultDetailActivity) = field(activity, "分类，比如：购物、证件、口令")
    private fun field(activity: VaultDetailActivity, hint: String) =
        nodes(activity.window.decorView).filterIsInstance<EditText>().first { it.hint == hint }
    private fun save(activity: VaultDetailActivity) {
        nodes(activity.window.decorView).filterIsInstance<Button>()
            .first { it.text == "保存到安全箱" || it.text == "保存修改" }.performClick()
    }
    private fun nodes(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { nodes(view.getChildAt(it)) } else emptyList()
}
