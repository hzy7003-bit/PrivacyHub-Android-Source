package com.privacyhub.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle

internal const val SENSITIVE_CLIP_EXTRA_KEY = "android.content.extra.IS_SENSITIVE"

object SafeClipboard {
    private const val DEFAULT_CLEAR_DELAY_MILLIS = 60_000L
    private val handler = Handler(Looper.getMainLooper())
    private var copyToken: Long = 0L
    private var pendingClear: Runnable? = null

    fun copySensitiveText(
        context: Context,
        label: String,
        text: String,
        clearDelayMillis: Long = DEFAULT_CLEAR_DELAY_MILLIS
    ): Boolean {
        val appContext = context.applicationContext
        val value = text.trim()
        if (value.isBlank()) return false
        val clipboard = appContext.getSystemService(ClipboardManager::class.java) ?: return false
        val clip = ClipData.newPlainText(label, value)
        clip.description.extras = PersistableBundle().apply {
            putBoolean(SENSITIVE_CLIP_EXTRA_KEY, true)
        }

        return runCatching {
            clipboard.setPrimaryClip(clip)
            val token = nextToken()
            pendingClear?.let(handler::removeCallbacks)
            val clearTask = Runnable {
                clearIfStillOwned(appContext, token, value)
            }
            pendingClear = clearTask
            handler.postDelayed(clearTask, clearDelayMillis)
            true
        }.getOrDefault(false)
    }

    private fun nextToken(): Long {
        copyToken += 1
        return copyToken
    }

    private fun clearIfStillOwned(context: Context, token: Long, expectedText: String) {
        if (token != copyToken) return
        pendingClear = null
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        runCatching {
            val current = clipboard.primaryClip
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(context)
                ?.toString()
                ?.trim()
            if (current == expectedText) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    clipboard.clearPrimaryClip()
                } else {
                    clipboard.setPrimaryClip(ClipData.newPlainText("Privacy Hub", ""))
                }
            }
        }
    }
}
