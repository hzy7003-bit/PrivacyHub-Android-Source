package com.privacyhub.presentation.ui

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.privacyhub.R

/** Shared component metrics; screens supply content and actions only. */
class PorcelainViews(private val context: Context) {
    fun dp(value: Int) = FrostedUi.dp(context, value)
    fun column() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    fun text(value: String, size: Float = UiMetrics.Body, muted: Boolean = false) = TextView(context).apply {
        text = value
        textSize = size
        setTextColor(if (muted) FrostedUi.textSecondary else FrostedUi.textPrimary)
        setPadding(0, dp(UiMetrics.Small), 0, dp(UiMetrics.Small))
    }
    fun title(value: String) = text(value, UiMetrics.Headline).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        androidx.core.view.ViewCompat.setAccessibilityHeading(this, true)
    }
    fun section(value: String) = text(value, UiMetrics.Title).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setPadding(0, dp(UiMetrics.Section), 0, dp(UiMetrics.Small))
        androidx.core.view.ViewCompat.setAccessibilityHeading(this, true)
    }
    fun button(label: String, kind: ButtonKind = ButtonKind.Secondary, action: () -> Unit) =
        button(label, kind, null, action)

    fun button(label: String, kind: ButtonKind = ButtonKind.Secondary, backgroundColor: Int?,
               action: () -> Unit) = Button(context).apply {
        text = label
        FrostedUi.styleButton(this, kind, backgroundColor)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(UiMetrics.Small) }
        setOnClickListener { action() }
    }
    fun row(label: String, detail: String? = null, action: () -> Unit): Button =
        button(if (detail.isNullOrBlank()) label else "$label\n$detail", ButtonKind.Plain, action).apply {
            if (!detail.isNullOrBlank()) {
                text = android.text.SpannableString("$label\n$detail").apply {
                    setSpan(android.text.style.RelativeSizeSpan(UiMetrics.Caption / UiMetrics.Body),
                        label.length + 1, length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    setSpan(android.text.style.ForegroundColorSpan(FrostedUi.textSecondary),
                        label.length + 1, length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setTextColor(FrostedUi.textPrimary)
            minHeight = dp(UiMetrics.Row)
            val arrow = ContextCompat.getDrawable(context, R.drawable.ph_chevron)?.mutate()
            arrow?.setTint(FrostedUi.textSecondary)
            arrow?.setBounds(0, 0, dp(UiMetrics.Icon), dp(UiMetrics.Icon))
            compoundDrawablePadding = dp(UiMetrics.Small)
            setCompoundDrawablesRelative(null, null, arrow, null)
        }
    fun back(action: () -> Unit) = button("返回", ButtonKind.Plain, action).apply {
        layoutParams = LinearLayout.LayoutParams(-2, -2)
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        val icon = ContextCompat.getDrawable(context, R.drawable.ph_back)?.mutate()
        icon?.setTint(FrostedUi.textPrimary)
        icon?.setBounds(0, 0, dp(UiMetrics.BackIcon), dp(UiMetrics.BackIcon))
        setCompoundDrawablesRelative(icon, null, null, null)
        compoundDrawablePadding = dp(UiMetrics.Micro)
        setPadding(0, dp(UiMetrics.Small), dp(UiMetrics.Content), dp(UiMetrics.Small))
    }
    fun navigation(selected: String, onSelect: (String) -> Unit) =
        navigation(selected, null, onSelect)

    fun navigation(selected: String, selectedBackgroundColor: Int?,
                   onSelect: (String) -> Unit) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        isBaselineAligned = false
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(UiMetrics.Navigation)
        setPadding(0, dp(UiMetrics.Small), 0, 0)
        val selectedBackground = selectedBackgroundColor ?: FrostedUi.primaryContainer
        listOf("中转", "安全箱", "设置").forEach { destination ->
            val isSelected = destination == selected
            addView(button(destination, if (isSelected) ButtonKind.Secondary else ButtonKind.Plain,
                if (isSelected) selectedBackground else null) {
                if (destination != selected) onSelect(destination)
            }.apply {
                this.isSelected = isSelected
                if (isSelected) setTextColor(FrostedUi.accent)
                textSize = UiMetrics.Caption
                val iconRes = when (destination) {
                    "中转" -> R.drawable.ph_transfer
                    "安全箱" -> R.drawable.ph_vault
                    else -> R.drawable.ph_settings
                }
                val icon = ContextCompat.getDrawable(context, iconRes)?.mutate()
                icon?.setTint(if (isSelected) FrostedUi.accent else FrostedUi.textSecondary)
                icon?.setBounds(0, 0, dp(UiMetrics.Icon), dp(UiMetrics.Icon))
                setCompoundDrawables(null, icon, null, null)
                compoundDrawablePadding = dp(UiMetrics.Micro)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                contentDescription = if (isSelected) "$destination，已选中" else destination
            })
        }
    }
}
