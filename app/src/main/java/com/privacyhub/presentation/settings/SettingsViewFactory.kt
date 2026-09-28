package com.privacyhub.presentation.settings

import android.graphics.Rect
import android.graphics.Typeface
import android.view.Gravity
import android.view.TouchDelegate
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.privacyhub.domain.model.TestReport
import com.privacyhub.presentation.SettingsActivity
import com.privacyhub.presentation.ui.ButtonKind
import com.privacyhub.presentation.ui.FrostedUi
import com.privacyhub.presentation.ui.UiMetrics
import com.privacyhub.util.Formatters

internal class SettingsViewFactory(private val activity: SettingsActivity) {
    fun notificationCard(
        enabled: Boolean,
        body: String,
        primaryLabel: String,
        primaryAction: () -> Unit,
        dangerAction: (() -> Unit)? = null
    ): LinearLayout {
        FrostedUi.refresh(activity)
        val actionArea = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            isBaselineAligned = false
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(UiMetrics.Content)
            }
        }
        actionArea.addView(actionButton(primaryLabel, ButtonKind.Primary, primaryAction))
        dangerAction?.let {
            actionArea.addView(actionButton("关闭通知按钮", ButtonKind.Danger, it).apply {
                (layoutParams as LinearLayout.LayoutParams).leftMargin = dp(UiMetrics.Small)
            })
        }

        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(UiMetrics.Content), dp(UiMetrics.Content), dp(UiMetrics.Content), dp(UiMetrics.Content))
            background = FrostedUi.rounded(activity, FrostedUi.surfaceContainerLow, UiMetrics.Radius)
            elevation = 0f
            addView(FrameLayout(activity).apply {
                layoutParams = LinearLayout.LayoutParams(-1, -2)
                addView(TextView(activity).apply {
                    text = "通知栏保存按钮"
                    textSize = UiMetrics.Title
                    typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                    setTextColor(FrostedUi.textPrimary)
                    layoutParams = FrameLayout.LayoutParams(-2, -2).apply {
                        gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    }
                })
                addView(TextView(activity).apply {
                    text = if (enabled) "已开启" else "未开启"
                    textSize = UiMetrics.Caption
                    setTextColor(FrostedUi.accent)
                    background = FrostedUi.rounded(activity, FrostedUi.primaryContainer, UiMetrics.Small)
                    setPadding(dp(UiMetrics.Small), dp(UiMetrics.Micro), dp(UiMetrics.Small), dp(UiMetrics.Micro))
                    layoutParams = FrameLayout.LayoutParams(-2, -2).apply {
                        gravity = Gravity.END or Gravity.CENTER_VERTICAL
                    }
                })
            })
            addView(text(body, UiMetrics.Caption, FrostedUi.textSecondary).apply {
                setPadding(0, dp(UiMetrics.Small), 0, 0)
                setLineSpacing(dp(UiMetrics.Micro).toFloat(), 1f)
            })
            addView(actionArea)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(UiMetrics.Small) }
        }
    }

    fun renderSection(content: LinearLayout, title: String, render: () -> Unit) {
        content.addView(sectionTitle(title).apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(if (content.childCount == 0) UiMetrics.Content else UiMetrics.Section)
            }
        })
        val firstContentIndex = content.childCount
        render()
        for (index in firstContentIndex until content.childCount) {
            val child = content.getChildAt(index)
            child.layoutParams = (child.layoutParams as? LinearLayout.LayoutParams
                ?: LinearLayout.LayoutParams(-1, -2)).apply {
                topMargin = if (index == firstContentIndex) 0 else dp(UiMetrics.Small)
                bottomMargin = 0
            }
        }
    }

    fun card(title: String, body: String, vararg buttons: Button?): LinearLayout {
        val actionButtons = buttons.filterNotNull()
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(UiMetrics.Content),
                dp(UiMetrics.Content),
                dp(UiMetrics.Content),
                dp(if (actionButtons.isNotEmpty()) UiMetrics.Small else UiMetrics.Content)
            )
            background = FrostedUi.rounded(activity, FrostedUi.surfaceContainerLow, UiMetrics.Radius)
            elevation = 0f
            addView(TextView(activity).apply {
                text = title
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(FrostedUi.textPrimary)
            })
            if (body.isNotBlank()) addView(bodyText(body))
            if (actionButtons.isNotEmpty()) {
                addView(LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                        topMargin = dp(UiMetrics.Content)
                    }
                    actionButtons.forEachIndexed { index, button ->
                        button.layoutParams = (button.layoutParams as? LinearLayout.LayoutParams
                            ?: LinearLayout.LayoutParams(-1, -2)).apply {
                            topMargin = 0
                            bottomMargin = if (index == actionButtons.lastIndex) 0 else dp(UiMetrics.Small)
                        }
                        addView(button)
                    }
                })
            }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(UiMetrics.Medium) }
        }
        if (actionButtons.size == 1) {
            val actionButton = actionButtons.single()
            actionButton.minHeight = 0
            actionButton.minimumHeight = 0
            val layoutListener = object : View.OnLayoutChangeListener {
                override fun onLayoutChange(
                    view: View,
                    left: Int,
                    top: Int,
                    right: Int,
                    bottom: Int,
                    oldLeft: Int,
                    oldTop: Int,
                    oldRight: Int,
                    oldBottom: Int
                ) {
                    card.removeOnLayoutChangeListener(this)
                    installSingleActionTouchDelegate(card, actionButton)
                }
            }
            card.addOnLayoutChangeListener(layoutListener)
        }
        return card
    }

    fun reportCard(report: TestReport): LinearLayout {
        val result = if (report.success) "成功" else "失败"
        val target = report.target?.displayName ?: "无目标"
        val failure = report.failureReason?.let { "\n失败原因：$it" }.orEmpty()
        val body = "${Formatters.time(report.createdAtMillis)}\n" +
            "入口：${report.entryPoint.displayName} · 结果：$result · 目标：$target\n" +
            "${report.brand} ${report.model} · Android ${report.androidVersion} · ${report.rom}\n" +
            "备注：${report.note}$failure"
        return card("测试记录", body)
    }

    fun button(label: String, kind: ButtonKind, action: () -> Unit): Button {
        return Button(activity).apply {
            text = label
            FrostedUi.styleMaterialButton(this, kind)
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }
    }

    private fun actionButton(
        label: String,
        kind: ButtonKind,
        action: () -> Unit
    ): Button {
        return Button(activity).apply {
            text = label
            FrostedUi.styleMaterialButton(this, kind)
            layoutParams = LinearLayout.LayoutParams(-2, -2)
            setOnClickListener { action() }
        }
    }

    fun title(value: String): TextView = text(value, 28f, FrostedUi.textPrimary, true)

    fun subtitle(value: String): TextView = text(value, 14f, FrostedUi.textSecondary).apply {
        setPadding(0, dp(6), 0, 0)
    }

    fun sectionTitle(value: String): TextView = text(value, 13f, FrostedUi.textMuted, true).apply {
        setPadding(0, 0, 0, dp(UiMetrics.Small))
    }

    fun emptyText(value: String): TextView = text(value, 14f, FrostedUi.textSecondary).apply {
        setPadding(0, dp(8), 0, dp(16))
    }

    private fun bodyText(value: String): TextView = text(value, 14f, FrostedUi.textSecondary).apply {
        setPadding(0, dp(8), 0, 0)
        setLineSpacing(dp(2).toFloat(), 1f)
    }

    private fun installSingleActionTouchDelegate(card: LinearLayout, button: Button) {
        if (card.width <= 0 || card.height <= 0 || button.width <= 0 || button.height <= 0) return

        val cardLocation = IntArray(2)
        val buttonLocation = IntArray(2)
        card.getLocationInWindow(cardLocation)
        button.getLocationInWindow(buttonLocation)
        val hitRect = Rect(
            buttonLocation[0] - cardLocation[0],
            buttonLocation[1] - cardLocation[1],
            buttonLocation[0] - cardLocation[0] + button.width,
            buttonLocation[1] - cardLocation[1] + button.height
        )
        var remaining = (dp(UiMetrics.Touch) - hitRect.height()).coerceAtLeast(0)
        if (remaining > 0) {
            val topMargin = (button.parent as? View)?.layoutParams
                ?.let { (it as? ViewGroup.MarginLayoutParams)?.topMargin }
                ?.coerceAtLeast(0) ?: 0
            val expandTop = minOf(remaining, topMargin)
            hitRect.top -= expandTop
            remaining -= expandTop

            val bottomSpace = (card.height - hitRect.bottom).coerceAtLeast(0)
            val expandBottom = minOf(remaining, bottomSpace)
            hitRect.bottom += expandBottom
            remaining -= expandBottom

            if (remaining > 0) {
                val extraTop = minOf(remaining, hitRect.top.coerceAtLeast(0))
                hitRect.top -= extraTop
                remaining -= extraTop
            }
            if (remaining > 0) {
                hitRect.bottom += minOf(remaining, (card.height - hitRect.bottom).coerceAtLeast(0))
            }
        }
        card.touchDelegate = TouchDelegate(hitRect, button)
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false): TextView {
        return TextView(activity).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }
    }

    fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()
}
