package com.privacyhub.presentation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import com.privacyhub.data.repository.LocalStore
import com.privacyhub.data.settings.AppSettingsStore
import com.privacyhub.domain.model.EntryPoint
import com.privacyhub.domain.model.LinkTarget
import com.privacyhub.presentation.security.SecureActivity
import com.privacyhub.presentation.settings.SettingsEntryController
import com.privacyhub.presentation.settings.SettingsSectionsRenderer
import com.privacyhub.presentation.settings.SettingsViewFactory
import com.privacyhub.presentation.ui.ButtonKind
import com.privacyhub.presentation.ui.FrostedUi
import com.privacyhub.pro.ProLicenseStore
import com.privacyhub.util.AppExecutors
import com.privacyhub.domain.model.TestReport

class SettingsActivity : SecureActivity() {
    private val store by lazy { LocalStore(this) }
    private val settingsStore by lazy { AppSettingsStore(this) }
    private val proLicenseStore by lazy { ProLicenseStore(this) }
    private val views by lazy { SettingsViewFactory(this) }
    private val entryController by lazy {
        SettingsEntryController(this, store, settingsStore, views, ::rerenderKeepingScroll)
    }
    private val sectionsRenderer by lazy {
        SettingsSectionsRenderer(
            this,
            store,
            settingsStore,
            proLicenseStore,
            views,
            ::rerenderKeepingScroll,
            ::lockNowAndAuthenticate,
            ::writeDemoClipboard
        )
    }
    private var currentScrollView: ScrollView? = null
    private var pendingScrollY: Int = 0

    override fun onUnlocked() = render()

    override fun onPause() {
        rememberScroll()
        super.onPause()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            FrostedUi.applyPageChrome(this)
        }
        val restoreScrollY = pendingScrollY
        val scroll = ScrollView(this)
        currentScrollView = scroll
        val scrollContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(scrollContent)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        scrollContent.addView(views.title("设置"))
        scrollContent.addView(views.subtitle("管理保存入口、安全锁、外观和本地诊断。"))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, views.dp(14), 0, 0)
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }
        scrollContent.addView(content)

        entryController.render(content)
        sectionsRenderer.render(content)
        root.addView(com.privacyhub.presentation.ui.PorcelainViews(this).navigation("设置") { tab ->
            startActivity(Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_TAB, tab))
            finish()
            overridePendingTransition(0, 0)
        })

        setContentView(root)
        if (restoreScrollY > 0) scroll.post { scroll.scrollTo(0, restoreScrollY) }
    }

    private fun writeDemoClipboard() {
        val clipboard = getSystemService(ClipboardManager::class.java)
        val text = "隐私中转站测试文本 https://example.com/?utm_source=test"
        clipboard.setPrimaryClip(ClipData.newPlainText("Privacy Hub Test", text))
        val localStore = store
        AppExecutors.executeDisk {
            runCatching { localStore.recordReport(EntryPoint.MANUAL, true, null, LinkTarget.WEB, "已写入测试剪切板") }
        }
        Toast.makeText(this, "已写入测试剪切板", Toast.LENGTH_SHORT).show()
    }

    internal fun renderRecentReports(container: LinearLayout, result: Result<List<TestReport>>) {
        if (isFinishing || isDestroyed) return
        container.removeAllViews()
        result.fold(
            onSuccess = { reports ->
                if (reports.isEmpty()) container.addView(views.emptyText("还没有测试记录。"))
                else reports.forEach { container.addView(views.reportCard(it)) }
            },
            onFailure = { error ->
                container.addView(views.emptyText(error.message ?: "暂时无法读取测试报告。"))
            }
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        entryController.onRequestPermissionsResult(requestCode, grantResults)
    }

    private fun rememberScroll() {
        currentScrollView?.let { pendingScrollY = it.scrollY }
    }

    private fun rerenderKeepingScroll() {
        rememberScroll()
        render()
    }
}
