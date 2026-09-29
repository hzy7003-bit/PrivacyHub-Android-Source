package com.privacyhub.presentation.transfer

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.privacyhub.clipboard.SafeClipboard
import com.privacyhub.data.repository.LocalStore
import com.privacyhub.data.settings.AppSettingsStore
import com.privacyhub.domain.model.VaultItem
import com.privacyhub.presentation.security.SecureActivity
import com.privacyhub.presentation.ui.ButtonKind
import com.privacyhub.presentation.ui.FrostedUi
import com.privacyhub.presentation.ui.PorcelainViews
import com.privacyhub.presentation.ui.UiMetrics
import com.privacyhub.presentation.vault.VaultDetailActivity
import com.privacyhub.presentation.vault.VaultLinkActionPanelFactory
import com.privacyhub.util.ActivityTaskScope
import com.privacyhub.util.Formatters

/** One already-persisted transfer. No duplicate plaintext draft or temporary database. */
class TransferActivity : SecureActivity() {
    private var itemId = 0L
    private var opened = false
    private val tasks by lazy { ActivityTaskScope(this) }
    private val store by lazy { LocalStore(applicationContext) }
    private val views by lazy { PorcelainViews(this) }

    override fun onSecureCreate(savedInstanceState: Bundle?) {
        itemId = savedInstanceState?.getLong(EXTRA_ITEM_ID) ?: intent.getLongExtra(EXTRA_ITEM_ID, 0L)
        opened = savedInstanceState?.getBoolean("opened") ?: false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong(EXTRA_ITEM_ID, itemId)
        outState.putBoolean("opened", opened)
        super.onSaveInstanceState(outState)
    }

    override fun onPause() {
        tasks.invalidate()
        super.onPause()
    }

    override fun onDestroy() {
        tasks.close()
        super.onDestroy()
    }

    override fun onUnlocked() {
        val token = tasks.nextGeneration()
        val root = shell()
        root.addView(views.text("正在读取已保存的内容…", muted = true))
        setContentView(root)
        val app = applicationContext
        val id = itemId
        tasks.executeDisk(
            generation = token,
            task = { LocalStore(app).getItem(id) },
            onResult = { activity, result ->
                result.fold(activity::render) { activity.renderError() }
            }
        )
    }

    private fun shell() = views.column().apply {
        FrostedUi.applyPageChrome(this)
        addView(views.back { finish() })
    }

    private fun render(item: VaultItem?) {
        val root = shell()
        if (item == null) {
            root.addView(views.title("内容已不存在"))
            root.addView(views.text("这条记录可能已被删除。返回即可结束本次中转。", muted = true))
            setContentView(root)
            return
        }
        val content = views.column()
        content.addView(bodyText(if (opened) "已发起打开 · 内容仍保存在安全箱" else "已保存到安全箱",
            UiMetrics.Caption, muted = true))
        content.addView(views.title(item.title.ifBlank { item.target.displayName }))
        content.addView(bodyText(item.target.displayName, UiMetrics.Caption, muted = true))

        content.addView(views.section("内容"))
        content.addView(materialSurface(FrostedUi.surfaceContainer).apply {
            addView(bodyText(item.content).apply {
                setPadding(views.dp(UiMetrics.Content), views.dp(UiMetrics.Content),
                    views.dp(UiMetrics.Content), views.dp(UiMetrics.Content))
                setTextIsSelectable(true)
                isSaveEnabled = false
            })
        })

        content.addView(views.section("信息"))
        val infoRows = mutableListOf<LinearLayout>().apply {
            add(metadataRow("识别类型", item.target.displayName))
            add(metadataRow("分类", item.category?.takeIf { it.isNotBlank() } ?: "未分类"))
            add(metadataRow("保存入口", item.entryPoint.displayName))
            item.sourceApp.takeIf { it.isNotBlank() && it != "unknown" }?.let {
                add(metadataRow("来源", it))
            }
            add(metadataRow("保存时间", Formatters.time(item.createdAtMillis)))
        }
        content.addView(materialSurface(verticalPadding = 0).apply {
            infoRows.forEachIndexed { index, row ->
                if (index > 0) addView(insetDivider(startInset = 0, endInset = 0))
                addView(row)
            }
        })

        val actions = VaultLinkActionPanelFactory(this, store, AppSettingsStore(this), { item.content }) {
            opened = true
        }
        actions.create(item)?.let { actionPanel ->
            content.addView(views.section("处理"))
            content.addView(materialSurface(verticalPadding = 0).apply {
                addView(actionPanel)
            })
        }

        content.addView(views.section("管理内容"))
        content.addView(materialSurface(horizontalPadding = 0, verticalPadding = 0).apply {
            addView(views.row("查看或编辑安全箱条目", "修改标题、备注、分类或提取码") {
                startActivity(Intent(this@TransferActivity, VaultDetailActivity::class.java)
                    .putExtra(VaultDetailActivity.EXTRA_ITEM_ID, item.id))
            }.withoutBottomMargin())
            addView(insetDivider())
            addView(views.row("安全复制全文", "60 秒后尝试清空系统剪切板") {
                val ok = SafeClipboard.copySensitiveText(this@TransferActivity, "中转内容", item.content)
                Toast.makeText(this@TransferActivity, if (ok) "已复制，60 秒后尝试清空" else "复制失败",
                    Toast.LENGTH_SHORT).show()
            }.withoutBottomMargin())
            addView(insetDivider())
            addView(bodyText("结束只关闭页面，不删除安全箱中的记录。", UiMetrics.Caption, muted = true).apply {
                setPadding(views.dp(UiMetrics.Content), views.dp(UiMetrics.Content),
                    views.dp(UiMetrics.Content), 0)
            })
            addView(outlinedActionButton("结束本次中转") { finish() })
        })

        root.addView(ScrollView(this).apply {
            addView(content)
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun outlinedActionButton(label: String, action: () -> Unit): Button =
        views.button(label, ButtonKind.Secondary, Color.TRANSPARENT, action).apply {
            setTextColor(FrostedUi.textPrimary)
            background = android.graphics.drawable.StateListDrawable().apply {
                addState(intArrayOf(-android.R.attr.state_enabled), outlinedButtonBackground(Color.TRANSPARENT,
                    FrostedUi.divider))
                addState(intArrayOf(android.R.attr.state_pressed), outlinedButtonBackground(
                    FrostedUi.surfaceContainer, FrostedUi.divider))
                addState(intArrayOf(android.R.attr.state_focused), outlinedButtonBackground(
                    FrostedUi.surfaceContainer, FrostedUi.accent))
                addState(intArrayOf(), outlinedButtonBackground(Color.TRANSPARENT, FrostedUi.divider))
            }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                marginStart = views.dp(UiMetrics.Content)
                topMargin = views.dp(UiMetrics.Small)
                marginEnd = views.dp(UiMetrics.Content)
                bottomMargin = views.dp(UiMetrics.Content)
            }
        }

    private fun outlinedButtonBackground(fillColor: Int, strokeColor: Int) =
        android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = views.dp(UiMetrics.Radius).toFloat()
            setColor(fillColor)
            setStroke(views.dp(1), strokeColor)
        }

    private fun materialSurface(
        backgroundColor: Int = FrostedUi.surfaceContainerLow,
        horizontalPadding: Int = UiMetrics.Content,
        verticalPadding: Int = UiMetrics.Content
    ) = views.column().apply {
        background = FrostedUi.rounded(this@TransferActivity, backgroundColor, UiMetrics.Radius)
        setPadding(views.dp(horizontalPadding), views.dp(verticalPadding),
            views.dp(horizontalPadding), views.dp(verticalPadding))
    }

    private fun bodyText(value: String, size: Float = UiMetrics.Body, muted: Boolean = false) =
        views.text(value, size, muted).apply {
            setPadding(0, 0, 0, 0)
        }

    private fun metadataRow(label: String, value: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = views.dp(UiMetrics.Touch)
        addView(bodyText(label, UiMetrics.Caption, muted = true).apply {
            layoutParams = LinearLayout.LayoutParams(0, -2, 0.35f)
        })
        addView(bodyText(value).apply {
            layoutParams = LinearLayout.LayoutParams(0, -2, 0.65f)
        })
    }

    private fun insetDivider(startInset: Int = UiMetrics.Content, endInset: Int = UiMetrics.Content) = View(this).apply {
        setBackgroundColor(FrostedUi.divider)
        layoutParams = LinearLayout.LayoutParams(-1, views.dp(1)).apply {
            marginStart = views.dp(startInset)
            marginEnd = views.dp(endInset)
        }
    }

    private fun TextView.withoutBottomMargin() = apply {
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }

    private fun renderError() {
        setContentView(shell().apply {
            addView(views.title("暂时无法读取"))
            addView(views.text("请重试。原有安全箱数据不会被清除。", muted = true))
            addView(views.button("重新读取", ButtonKind.Primary) { onUnlocked() })
        })
    }

    companion object { const val EXTRA_ITEM_ID = "item_id" }
}
