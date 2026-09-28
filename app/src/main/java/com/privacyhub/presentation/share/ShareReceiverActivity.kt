package com.privacyhub.presentation.share

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.privacyhub.data.repository.LocalStore
import com.privacyhub.domain.model.EntryPoint
import com.privacyhub.presentation.transfer.TransferActivity
import com.privacyhub.presentation.ui.ButtonKind
import com.privacyhub.presentation.ui.FrostedUi
import com.privacyhub.presentation.ui.PorcelainViews
import com.privacyhub.router.LinkParser
import com.privacyhub.router.LinkTargetDetector
import com.privacyhub.security.lock.SecureWindow
import com.privacyhub.security.privacy.RecentsVisibility
import com.privacyhub.util.AppExecutors

/** Exported intake only. Reading a saved item happens behind TransferActivity's lock. */
open class ShareReceiverActivity : Activity() {
    private lateinit var receipt: Receipt
    private var forwarded = false
    private var resumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SecureWindow.apply(this)
        RecentsVisibility.apply(this)
        FrostedUi.applyWindow(this)
        val restoredId = savedInstanceState?.getLong(KEY_SAVED_ID, 0L) ?: 0L
        if (restoredId > 0) {
            receipt = Receipt().apply { result = Result.success(restoredId) }
            return
        }
        @Suppress("DEPRECATION")
        val retained = lastNonConfigurationInstance as? Receipt
        receipt = retained ?: Receipt()
        showStatus("正在保存", "内容会先保存到本地加密安全箱。")
        receipt.observer = ::renderResult
        if (receipt.result != null) renderResult() else if (!receipt.started) saveIncoming()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        if (::receipt.isInitialized) renderResult()
    }

    override fun onPause() {
        resumed = false
        super.onPause()
    }

    @Deprecated("Retains an in-flight save across configuration changes without retaining the Activity.")
    override fun onRetainNonConfigurationInstance(): Any? = if (::receipt.isInitialized) receipt else null

    override fun onSaveInstanceState(outState: Bundle) {
        if (::receipt.isInitialized) receipt.result?.getOrNull()?.let { outState.putLong(KEY_SAVED_ID, it) }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        if (::receipt.isInitialized) receipt.observer = null
        super.onDestroy()
    }

    private fun saveIncoming() {
        val text = when (intent.action) {
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            Intent.ACTION_SEND, ACTION_MANUAL -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            else -> null
        }.orEmpty().trim()
        if (text.isBlank()) {
            showStatus("没有收到文本", "请在来源 App 分享文本，或返回中转页手动输入。", retry = false)
            val app = applicationContext
            AppExecutors.executeDisk { runCatching {
                LocalStore(app).recordReport(EntryPoint.SHARE, false, "未收到可处理的文本", null, "文本入口接收失败")
            } }
            return
        }
        receipt.started = true
        val currentReceipt = receipt
        val app = applicationContext
        val source = callingPackage ?: referrer?.host ?: "unknown"
        val entry = if (intent.action == ACTION_MANUAL) EntryPoint.MANUAL else EntryPoint.SHARE
        AppExecutors.executeDisk {
            val result = runCatching {
                val store = LocalStore(app)
                val target = LinkTargetDetector.detect(text)
                val id = store.saveItem(entry, source, text, LinkParser.extractFirstUrl(text), target)
                // A diagnostics failure must never turn a successful save into a retry.
                runCatching { store.recordReport(entry, true, null, target, "文本已保存到本地安全箱") }
                id
            }
            AppExecutors.executeMain {
                currentReceipt.result = result
                currentReceipt.observer?.invoke()
            }
        }
    }

    private fun renderResult() {
        if (!resumed) return
        receipt.result?.fold(
            onSuccess = ::openSaved,
            onFailure = { showStatus("保存未完成", "本次内容尚未确认保存。请重试；原有安全箱数据不会被清除。", retry = true) }
        )
    }

    private fun openSaved(id: Long) {
        if (!resumed || forwarded || isFinishing) return
        forwarded = true
        startActivity(Intent(this, TransferActivity::class.java).putExtra(TransferActivity.EXTRA_ITEM_ID, id))
        finish()
    }

    private fun showStatus(title: String, message: String, retry: Boolean = false) {
        val views = PorcelainViews(this)
        setContentView(views.column().apply {
            FrostedUi.applyPageChrome(this)
            addView(views.title(title))
            addView(views.text(message, muted = true))
            if (retry) addView(views.button("重试保存", ButtonKind.Primary) {
                receipt.result = null
                receipt.started = false
                showStatus("正在保存", "请稍候")
                saveIncoming()
            })
            addView(views.button("返回", ButtonKind.Plain) { finish() })
        })
    }

    private class Receipt {
        var started = false
        var result: Result<Long>? = null
        var observer: (() -> Unit)? = null
    }

    companion object {
        const val ACTION_MANUAL = "com.privacyhub.action.TRANSFER_TEXT"
        private const val KEY_SAVED_ID = "saved_item_id"
    }
}
