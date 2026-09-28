package com.privacyhub.presentation

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.privacyhub.data.repository.LocalStore
import com.privacyhub.data.settings.AppSettingsStore
import com.privacyhub.data.settings.ThemeModeOption
import com.privacyhub.presentation.transfer.TransferActivity
import com.privacyhub.presentation.vault.VaultDetailActivity
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Capture deterministic content only; no screenshots of the user's vault list. */
@RunWith(AndroidJUnit4::class)
class VisualReviewTest {
    @Test fun capturePairedThemes() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val context = inst.targetContext
        val settings = AppSettingsStore(context)
        val originalTheme = settings.themeMode()
        val originalLock = settings.isSecurityLockEnabled()
        val originalNotice = settings.lastSeenTrialNoticeVersion()
        settings.setLastSeenTrialNoticeVersion(com.privacyhub.presentation.guide.GuideContent.noticeKey(com.privacyhub.BuildConfig.VERSION_NAME))
        val store = LocalStore(context)
        val id = store.saveManualItem("百度网盘链接 · UI 验证",
            "https://pan.baidu.com/s/PrivacyHubUiDemo\n提取码：7a3K", "网盘", "这是一条用于界面验证的虚构内容。")
        val output = File(context.getExternalFilesDir(null), "ui-review").apply { mkdirs() }
        settings.setSecurityLockEnabled(false)
        inst.uiAutomation.executeShellCommand("am start -W -n com.privacyhub/.presentation.MainActivity").use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
        }
        inst.waitForIdleSync()
        try {
            for (theme in listOf(ThemeModeOption.Light, ThemeModeOption.Dark)) {
                settings.setThemeMode(theme)
                capture<MainActivity>(Intent(context, MainActivity::class.java), "添加一段内容", File(output, "idle-${theme.name}.png"))
                capture<TransferActivity>(Intent(context, TransferActivity::class.java).putExtra(TransferActivity.EXTRA_ITEM_ID, id),
                    "百度网盘链接 · UI 验证", File(output, "transfer-${theme.name}.png"))
                capture<VaultDetailActivity>(Intent(context, VaultDetailActivity::class.java).putExtra(VaultDetailActivity.EXTRA_ITEM_ID, id),
                    "百度网盘链接 · UI 验证", File(output, "detail-${theme.name}.png"))
                capture<SettingsActivity>(Intent(context, SettingsActivity::class.java), "保存入口", File(output, "settings-${theme.name}.png"))
            }
        } finally {
            store.deleteItem(id)
            settings.setThemeMode(originalTheme)
            settings.setSecurityLockEnabled(originalLock)
            settings.setLastSeenTrialNoticeVersion(originalNotice)
        }
    }

    private inline fun <reified T : Activity> capture(intent: Intent, expected: String, file: File) {
        val inst = InstrumentationRegistry.getInstrumentation()
        inst.sendStatus(2, android.os.Bundle().apply { putString("stream", "Capturing ${file.name}\n") })
        ActivityScenario.launch<T>(intent).use { scenario ->
            val deadline = System.currentTimeMillis() + 8000
            var ready = false
            while (!ready && System.currentTimeMillis() < deadline) {
                inst.waitForIdleSync()
                scenario.onActivity { ready = contains(it.window.decorView, expected) }
                if (!ready) Thread.sleep(50)
            }
            assertTrue("Screen must render before screenshot", ready)
            Thread.sleep(3500) // Let transitions, scrollbars and unrelated short system toasts expire.
            val bitmap = inst.uiAutomation.takeScreenshot()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    private fun contains(view: View, expected: String): Boolean =
        (view is TextView && view.text.toString().contains(expected)) ||
            (view is ViewGroup && (0 until view.childCount).any { contains(view.getChildAt(it), expected) })
}
