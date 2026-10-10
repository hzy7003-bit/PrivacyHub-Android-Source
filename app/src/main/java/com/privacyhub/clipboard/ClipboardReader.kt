package com.privacyhub.clipboard

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import com.privacyhub.backup.BackupPayloadCodec
import com.privacyhub.domain.model.ClipboardReadResult

object ClipboardReader {
    const val TEXT_TOO_LARGE_REASON = "剪切板文本超过 1 MiB 备份上限，未保存"

    fun readText(context: Context): ClipboardReadResult {
        return try {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
                ?: return ClipboardReadResult(null, "系统 ClipboardManager 不可用")
            if (!clipboard.hasPrimaryClip()) {
                return ClipboardReadResult(null, "系统剪切板为空")
            }

            val clip = clipboard.primaryClip
                ?: return ClipboardReadResult(null, "系统返回 primaryClip=null")
            val description = clip.description
            if (!description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) &&
                !description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)
            ) {
                return ClipboardReadResult(null, "剪切板内容不是文本类型：${description.label ?: "unknown"}")
            }
            if (clip.itemCount <= 0) {
                return ClipboardReadResult(null, "剪切板没有可读取的 item")
            }

            val text = clip.getItemAt(0).coerceToText(context)?.toString()?.trim().orEmpty()
            if (text.isBlank()) {
                ClipboardReadResult(null, "剪切板文本为空")
            } else if (!BackupPayloadCodec.isCompatibleText(text)) {
                ClipboardReadResult(null, TEXT_TOO_LARGE_REASON)
            } else {
                ClipboardReadResult(text, null)
            }
        } catch (error: SecurityException) {
            ClipboardReadResult(null, "SecurityException：${error.message ?: "系统拒绝读取剪切板"}")
        } catch (error: RuntimeException) {
            ClipboardReadResult(null, "${error.javaClass.simpleName}：${error.message ?: "读取剪切板失败"}")
        }
    }
}
