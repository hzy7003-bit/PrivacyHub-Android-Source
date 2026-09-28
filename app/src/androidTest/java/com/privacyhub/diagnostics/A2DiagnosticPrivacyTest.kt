package com.privacyhub.diagnostics

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.privacyhub.data.db.PrivacyHubDatabaseProvider
import com.privacyhub.data.db.entity.VaultItemEntity
import com.privacyhub.data.repository.TestReportRepositoryImpl
import com.privacyhub.domain.model.EntryPoint
import com.privacyhub.domain.model.LinkTarget
import com.privacyhub.router.AppIntentRouter
import com.privacyhub.router.BrowserFallback
import com.privacyhub.router.OpenResult
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** A2 uses the current encrypted repository; all writes and cleanup belong to this invocation. */
@RunWith(AndroidJUnit4::class)
class A2DiagnosticPrivacyTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun unresolvedUriAndMaliciousReportsLeaveNoDiagnosticResidueAfterVaultDeletion() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(PRIVATE_URI)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        @Suppress("DEPRECATION")
        val resolverCount = context.packageManager.queryIntentActivities(intent, 0).size
        assertEquals("The test URI must have no visible activity handlers", 0, resolverCount)

        val activity = noLaunchActivity()
        val browser = BrowserFallback.open(activity, PRIVATE_URI)
        assertFailure(browser, "浏览器启动失败", usedFallback = true)
        val web = AppIntentRouter.openTargetAppThenFallback(activity, PRIVATE_URI, LinkTarget.WEB)
        assertFailure(web, "浏览器启动失败", usedFallback = true)
        val target = AppIntentRouter.openTargetAppThenFallback(activity, PRIVATE_URI, LinkTarget.TAOBAO)
        assertFailure(target, "目标 App 与浏览器均无法打开链接", usedFallback = true)
        assertEquals(3, activity.blockedContext.blockedStarts)

        withFakeVault { fixture ->
            listOf(browser, web, target).forEach { result ->
                fixture.recordAndVerify(
                    result.failureReason,
                    result.routeNote ?: SAFE_NOTE,
                    result.failureReason,
                    SAFE_NOTE
                )
            }
            // Include an approved prefix: matching only part of the string must not preserve it.
            fixture.recordAndVerify(
                "目标 App 未安装 Intent { dat=$PRIVATE_URI } $TAOBAO_SECRET",
                "$SAFE_NOTE $PRIVATE_URI $TAOBAO_SECRET",
                "操作失败，详细原因已脱敏",
                "本地操作记录已脱敏"
            )
            fixture.deleteVaultAndVerifyReports()
        }
    }

    @Test
    fun absentTaobaoSourceTextIsNotRetainedAfterVaultDeletion() {
        @Suppress("DEPRECATION")
        val taobaoAbsent = try {
            context.packageManager.getPackageInfo("com.taobao.taobao", 0)
            false
        } catch (_: PackageManager.NameNotFoundException) {
            true
        }
        // The manifest declares this package in <queries>; never uninstall or open it for this test.
        assumeTrue("This subcase requires com.taobao.taobao to be absent", taobaoAbsent)

        val activity = noLaunchActivity()
        val result = AppIntentRouter.openTargetAppThenFallback(
            activity,
            url = null,
            target = LinkTarget.TAOBAO,
            sourceText = TAOBAO_SECRET
        )
        // The public method falls through from the failed search to the app-home result.
        assertFailure(result, "目标 App 未安装", usedFallback = false)
        assertEquals(0, activity.blockedContext.blockedStarts)
        withFakeVault { fixture ->
            fixture.recordAndVerify(result.failureReason, SAFE_NOTE, "目标 App 未安装", SAFE_NOTE)
            fixture.deleteVaultAndVerifyReports()
        }
    }

    private fun noLaunchActivity(): NoLaunchActivity {
        lateinit var activity: NoLaunchActivity
        instrumentation.runOnMainSync { activity = NoLaunchActivity(context) }
        return activity
    }

    private fun assertFailure(result: OpenResult, expectedReason: String, usedFallback: Boolean) {
        assertFalse(result.success)
        assertEquals(usedFallback, result.usedFallback)
        assertEquals(expectedReason, result.failureReason)
        assertNull(result.routeNote)
        assertNull(result.openedPackageName)
        assertSafe(result.failureReason.orEmpty())
        assertSafe(result.routeNote.orEmpty())
    }

    private fun withFakeVault(test: (Fixture) -> Unit) {
        val fixture = Fixture()
        try {
            fixture.createVault()
            test(fixture)
        } finally {
            fixture.cleanup()
        }
    }

    private inner class Fixture {
        private val database = PrivacyHubDatabaseProvider.get(context)
        private val repository = TestReportRepositoryImpl(context)
        private val vaultDao = database.vaultItemDao()
        private var vaultId: Long? = null
        private val reports = linkedMapOf<Long, Pair<String?, String>>()

        fun createVault() {
            val now = System.currentTimeMillis()
            vaultId = vaultDao.insert(
                VaultItemEntity(
                    backupId = UUID.randomUUID().toString(),
                    type = "TEXT",
                    title = "A2 diagnostic privacy fixture",
                    content = "$PRIVATE_URI $TAOBAO_SECRET",
                    normalizedLink = PRIVATE_URI,
                    originalLink = PRIVATE_URI,
                    sourceApp = "androidTest:A2",
                    entryPoint = EntryPoint.MANUAL.name,
                    category = null,
                    note = null,
                    favorite = false,
                    createdAtMillis = now,
                    updatedAtMillis = now,
                    lastOpenedAtMillis = null,
                    target = LinkTarget.WEB.name
                )
            )
            assertEquals("$PRIVATE_URI $TAOBAO_SECRET", vaultDao.getById(vaultId!!)!!.content)
        }

        fun recordAndVerify(
            failureReason: String?,
            note: String,
            expectedReason: String?,
            expectedNote: String
        ) {
            val id = repository.record(EntryPoint.MANUAL, false, failureReason, LinkTarget.WEB, note)
            reports[id] = expectedReason to expectedNote
            assertPersisted(id, expectedReason, expectedNote)
        }

        fun deleteVaultAndVerifyReports() {
            // test_reports has no vault-item FK: these report IDs are associated only by this fixture.
            vaultDao.deleteById(vaultId!!)
            assertNull(vaultDao.getById(vaultId!!))
            reports.forEach { (id, expected) -> assertPersisted(id, expected.first, expected.second) }
        }

        private fun assertPersisted(id: Long, expectedReason: String?, expectedNote: String) {
            // DAO returns the stored entity unchanged, so this verifies the encrypted Room write
            // boundary without opening or reading the raw SQLCipher file.
            val report = database.testReportDao().recent(1_000).firstOrNull { it.id == id }
            assertTrue("The test report must be persisted", report != null)
            assertEquals(expectedReason, report?.failureReason)
            assertEquals(expectedNote, report?.note)
            assertSafe(report?.failureReason.orEmpty())
            assertSafe(report?.note.orEmpty())
        }

        fun cleanup() {
            // Keep only the fixed, sanitized report evidence; remove this test's fake vault row.
            vaultId?.let(vaultDao::deleteById)
        }
    }

    /** Unattached Activity: only the two dependencies used by the current routers are supplied. */
    private class NoLaunchActivity(context: Context) : Activity() {
        val blockedContext = NoLaunchContext(context)
        override fun getPackageManager(): PackageManager = blockedContext.packageManager
        override fun getApplicationContext(): Context = blockedContext
    }

    private class NoLaunchContext(context: Context) : ContextWrapper(context) {
        var blockedStarts = 0
            private set

        override fun getApplicationContext(): Context = this

        override fun startActivity(intent: Intent) {
            blockedStarts++
            // Simulate the framework's sensitive Intent exception without dispatching any activity.
            throw ActivityNotFoundException("Unable to resolve Intent { dat=${intent.data} }")
        }

        override fun startActivity(intent: Intent, options: Bundle?) = startActivity(intent)

        override fun startActivities(intents: Array<out Intent>) {
            throw AssertionError("Activity launches are forbidden in A2")
        }

        override fun startActivities(intents: Array<out Intent>, options: Bundle?) = startActivities(intents)
    }

    private fun assertSafe(text: String) {
        listOf(
            PRIVATE_URI, "privacyhub-test", "private.example.invalid", "/path", "token=",
            "PH_SECRET_123", TAOBAO_SECRET, "123456", "Intent", "dat=", "s.m.taobao.com"
        ).forEach { forbidden ->
            assertFalse("Diagnostic text retained a forbidden fixture fragment", text.contains(forbidden))
        }
    }

    companion object {
        private const val PRIVATE_URI = "privacyhub-test://private.example.invalid/path?token=PH_SECRET_123"
        private const val TAOBAO_SECRET = "SECRET_TAOBAO_TOKEN_123456"
        private const val SAFE_NOTE = "从安全箱详情打开已保存链接"
    }
}
