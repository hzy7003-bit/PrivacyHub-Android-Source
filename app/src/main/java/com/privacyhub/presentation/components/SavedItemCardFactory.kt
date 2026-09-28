package com.privacyhub.presentation.components

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.privacyhub.data.repository.LocalStore
import com.privacyhub.data.settings.AppSettingsStore
import com.privacyhub.domain.model.LinkTarget
import com.privacyhub.domain.model.SavedItem
import com.privacyhub.presentation.ui.ButtonKind
import com.privacyhub.presentation.ui.FrostedUi
import com.privacyhub.presentation.vault.VaultDetailActivity
import com.privacyhub.router.AppIntentRouter
import com.privacyhub.router.OpenResult
import com.privacyhub.util.Formatters

object SavedItemCardFactory {
    fun create(
        activity: Activity,
        store: LocalStore,
        settingsStore: AppSettingsStore,
        item: SavedItem,
        onChanged: () -> Unit
    ): LinearLayout {
        val showPreview = settingsStore.isHomePreviewEnabled()
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            FrostedUi.applyPanel(this, radiusDp = 22)
            setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(0, 0, 0, dp(activity, 12))
            }

            addView(TextView(activity).apply {
                text = buildString {
                    appendLine("${if (item.favorite) "★ " else ""}${item.title.ifBlank { item.target.displayName }}")
                    appendLine("${Formatters.time(item.createdAtMillis)}  ${item.entryPoint.displayName}")
                    appendLine("识别：${item.target.displayName}")
                    item.category?.let { appendLine("分类：$it") }
                    appendLine("来源：${item.sourceApp}")
                    if (showPreview) {
                        item.primaryUrl?.let { appendLine("链接：$it") }
                        append(Formatters.preview(item.originalText))
                    } else {
                        append("内容已隐藏，点“查看/编辑”再查看原文。")
                    }
                }
                textSize = 14f
                setTextColor(FrostedUi.textPrimary)
            })

            val actions = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(activity, 8), 0, 0)
            }
            val firstRow = actionRow(activity)
            firstRow.addView(rowButton(activity, if (item.favorite) "取消收藏" else "收藏") {
                store.setFavorite(item.id, !item.favorite)
                onChanged()
            })
            if (canOpenTarget(item)) {
                firstRow.addView(space(activity, dp(activity, 8), 1))
                firstRow.addView(rowButton(activity, openButtonLabel(item.primaryUrl, item.target), ButtonKind.Primary) {
                    openSavedLink(activity, store, item)
                })
            }
            actions.addView(firstRow)

            val secondRow = actionRow(activity).apply {
                setPadding(0, dp(activity, 8), 0, 0)
            }
            secondRow.addView(rowButton(activity, "查看/编辑") {
                activity.startActivity(Intent(activity, VaultDetailActivity::class.java).apply {
                    putExtra(VaultDetailActivity.EXTRA_ITEM_ID, item.id)
                })
            })
            secondRow.addView(space(activity, dp(activity, 8), 1))
            secondRow.addView(rowButton(activity, "删除", ButtonKind.Danger) {
                confirmDelete(activity, store, item, onChanged)
            })
            actions.addView(secondRow)
            addView(actions)
        }
    }

    fun sectionTitle(activity: Activity, text: String): TextView {
        return TextView(activity).apply {
            this.text = text
            textSize = 18f
            setTextColor(FrostedUi.textPrimary)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(activity, 8), 0, dp(activity, 8))
        }
    }

    fun emptyText(activity: Activity, text: String): TextView {
        return TextView(activity).apply {
            this.text = text
            textSize = 15f
            setTextColor(FrostedUi.textSecondary)
            setPadding(0, dp(activity, 18), 0, 0)
        }
    }

    private fun openSavedLink(activity: Activity, store: LocalStore, item: SavedItem) {
        val result = AppIntentRouter.openTargetAppThenFallback(activity, item.primaryUrl, item.target, item.originalText)
        store.recordReport(
            entryPoint = item.entryPoint,
            success = result.success,
            failureReason = result.failureReason,
            target = item.target,
            note = result.routeNote ?: "从列表页打开已保存链接"
        )
        Toast.makeText(activity, toastForOpenResult(result), Toast.LENGTH_SHORT).show()
    }

    private fun canOpenTarget(item: SavedItem): Boolean {
        return !item.primaryUrl.isNullOrBlank() || item.target.packageName != null
    }

    private fun openButtonLabel(url: String?, target: LinkTarget): String {
        return when {
            target == LinkTarget.TAOBAO_PAYMENT -> "打开淘宝代付"
            url.isNullOrBlank() && target == LinkTarget.TAOBAO_FLASH_DELIVERY -> "去淘宝闪购代付"
            url.isNullOrBlank() && target.usesTaobaoSearchFallback -> "用淘宝搜索"
            url.isNullOrBlank() && target.packageName != null -> "打开${target.displayName}"
            else -> "打开链接"
        }
    }

    private fun toastForOpenResult(result: OpenResult): String {
        return when {
            result.success && result.usedFallback -> "目标 App 未接住，已用浏览器打开"
            result.success -> "已发起打开"
            else -> result.failureReason ?: "打开失败"
        }
    }

    private fun confirmDelete(
        activity: Activity,
        store: LocalStore,
        item: SavedItem,
        onChanged: () -> Unit
    ) {
        AlertDialog.Builder(activity)
            .setTitle("删除内容")
            .setMessage("确定删除“${item.title.ifBlank { item.target.displayName }}”？此操作只删除本地安全箱记录。")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ ->
                store.deleteItem(item.id)
                Toast.makeText(activity, "已删除", Toast.LENGTH_SHORT).show()
                onChanged()
            }
            .show()
    }

    private fun actionRow(activity: Activity): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
    }

    private fun rowButton(
        activity: Activity,
        text: String,
        kind: ButtonKind = ButtonKind.Secondary,
        action: () -> Unit
    ): Button {
        return Button(activity).apply {
            this.text = text
            FrostedUi.styleMaterialButton(this, kind)
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(0, dp(activity, 48), 1f)
        }
    }

    private fun space(activity: Activity, width: Int, height: Int): android.view.View {
        return android.view.View(activity).apply {
            layoutParams = LinearLayout.LayoutParams(width, height)
        }
    }

    private fun dp(activity: Activity, value: Int): Int {
        return (value * activity.resources.displayMetrics.density).toInt()
    }
}
