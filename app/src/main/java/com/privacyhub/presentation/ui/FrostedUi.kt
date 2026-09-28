package com.privacyhub.presentation.ui

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.privacyhub.R
import com.privacyhub.data.settings.AppSettingsStore
import com.privacyhub.data.settings.ThemeModeOption

/** Compatibility facade: legacy screens and new screens share Porcelain tokens. */
object FrostedUi {
    private var darkMode = false
    var pageColor: Int = Color.WHITE; private set
    var surface: Int = Color.LTGRAY; private set
    var surfaceContainerLow: Int = Color.LTGRAY; private set
    var surfaceContainer: Int = Color.LTGRAY; private set
    var divider: Int = Color.GRAY; private set
    var primaryContainer: Int = Color.LTGRAY; private set
    var textPrimary: Int = Color.BLACK; private set
    var textSecondary: Int = Color.DKGRAY; private set
    val textMuted: Int get() = textSecondary
    var accent: Int = Color.BLUE; private set
    var onAccent: Int = Color.WHITE; private set
    var danger: Int = Color.RED; private set

    fun refresh(context: Context) {
        darkMode = when (AppSettingsStore(context).themeMode()) {
            ThemeModeOption.FollowSystem -> context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            ThemeModeOption.Light -> false
            ThemeModeOption.Dark -> true
        }
        fun color(light: Int, dark: Int) = context.getColor(if (darkMode) dark else light)
        pageColor = color(R.color.ph_light_canvas, R.color.ph_dark_canvas)
        surface = color(R.color.ph_light_surface, R.color.ph_dark_surface)
        surfaceContainerLow = color(R.color.ph_light_surface_container_low, R.color.ph_dark_surface_container_low)
        surfaceContainer = color(R.color.ph_light_surface_container, R.color.ph_dark_surface_container)
        divider = color(R.color.ph_light_outline_variant, R.color.ph_dark_outline_variant)
        primaryContainer = color(R.color.ph_light_primary_container, R.color.ph_dark_primary_container)
        textPrimary = color(R.color.ph_light_text, R.color.ph_dark_text)
        textSecondary = color(R.color.ph_light_muted, R.color.ph_dark_muted)
        accent = color(R.color.ph_light_primary, R.color.ph_dark_primary)
        onAccent = color(R.color.ph_light_on_primary, R.color.ph_dark_on_primary)
        danger = color(R.color.ph_light_danger, R.color.ph_dark_danger)
    }

    fun applyWindow(activity: Activity) {
        refresh(activity)
        activity.setTheme(if (darkMode) R.style.PorcelainDark else R.style.PorcelainLight)
        activity.window.statusBarColor = Color.TRANSPARENT
        activity.window.navigationBarColor = Color.TRANSPARENT
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !darkMode
            isAppearanceLightNavigationBars = !darkMode
        }
        activity.window.setBackgroundDrawable(ColorDrawable(pageColor))
    }

    fun pageBackground(context: Context): Drawable {
        refresh(context)
        return ColorDrawable(pageColor)
    }

    fun applyPageBackground(view: View) { view.background = pageBackground(view.context) }

    fun applyPageChrome(view: View, leftDp: Int = UiMetrics.Gutter, topDp: Int = UiMetrics.Content,
                        rightDp: Int = UiMetrics.Gutter, bottomDp: Int = UiMetrics.Small,
                        includeIme: Boolean = true) {
        applyPageBackground(view)
        val left = dp(view.context, leftDp)
        val top = dp(view.context, topDp)
        val right = dp(view.context, rightDp)
        val bottom = dp(view.context, bottomDp)
        val insetTypes = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or
            if (includeIme) WindowInsetsCompat.Type.ime() else 0
        view.setPadding(left, top, right, bottom)
        ViewCompat.setOnApplyWindowInsetsListener(view) { target, insets ->
            val safe = insets.getInsets(insetTypes)
            target.setPadding(left + safe.left, top + safe.top, right + safe.right, bottom + safe.bottom)
            insets
        }
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { ViewCompat.requestApplyInsets(v) }
            override fun onViewDetachedFromWindow(v: View) = Unit
        })
        ViewCompat.requestApplyInsets(view)
    }

    fun rounded(context: Context, color: Int, radius: Int = UiMetrics.Radius): Drawable =
        GradientDrawable().apply { setColor(color); cornerRadius = dp(context, radius).toFloat() }

    @Suppress("UNUSED_PARAMETER")
    fun panel(context: Context, radiusDp: Int = UiMetrics.Radius): Drawable {
        refresh(context)
        return rounded(context, surface)
    }

    fun inputBackground(context: Context): Drawable = panel(context)

    fun applyPanel(view: View, radiusDp: Int = UiMetrics.Radius) {
        view.background = panel(view.context, radiusDp)
        view.elevation = 0f
    }

    fun styleButton(button: Button, kind: ButtonKind = ButtonKind.Secondary, backgroundColor: Int? = null) {
        refresh(button.context)
        button.isAllCaps = false
        button.minHeight = dp(button.context, UiMetrics.Touch)
        button.minimumHeight = dp(button.context, UiMetrics.Touch)
        button.minWidth = 0
        button.minimumWidth = 0
        button.textSize = UiMetrics.Body
        button.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        button.setPadding(dp(button.context, UiMetrics.Content), dp(button.context, UiMetrics.Small),
            dp(button.context, UiMetrics.Content), dp(button.context, UiMetrics.Small))
        val foreground = when (kind) {
            ButtonKind.Primary -> onAccent
            ButtonKind.Danger -> danger
            ButtonKind.Plain -> textSecondary
            else -> textPrimary
        }
        val background = when (kind) {
            ButtonKind.Primary -> accent
            ButtonKind.Plain, ButtonKind.Danger -> Color.TRANSPARENT
            else -> backgroundColor ?: surface
        }
        button.setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(textSecondary, foreground)))
        val pressed = androidx.core.graphics.ColorUtils.blendARGB(background, foreground, 0.16f)
        button.background = StateListDrawable().apply {
            addState(intArrayOf(-android.R.attr.state_enabled), rounded(button.context, backgroundColor ?: surface))
            addState(intArrayOf(android.R.attr.state_pressed), rounded(button.context, pressed))
            addState(intArrayOf(android.R.attr.state_focused), rounded(button.context, pressed))
            addState(intArrayOf(), rounded(button.context, background))
        }
        button.stateListAnimator = null
        button.elevation = 0f
    }

    /** Material 3 Filled Tonal Button for a page's primary action. */
    fun styleTonalButton(button: Button) {
        styleButton(button, ButtonKind.Secondary, primaryContainer)
        button.setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(textSecondary, accent)))
    }

    /** Material 3 Outlined Button for a page's secondary action. */
    fun styleOutlinedButton(
        button: Button,
        textColor: Int = textPrimary,
        outlineColor: Int = divider
    ) {
        styleButton(button, ButtonKind.Secondary, Color.TRANSPARENT)
        button.setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(textSecondary, textColor)))
        button.background = StateListDrawable().apply {
            addState(intArrayOf(-android.R.attr.state_enabled), outlinedButtonBackground(
                button.context, Color.TRANSPARENT, outlineColor))
            addState(intArrayOf(android.R.attr.state_pressed), outlinedButtonBackground(
                button.context, surfaceContainer, outlineColor))
            addState(intArrayOf(android.R.attr.state_focused), outlinedButtonBackground(
                button.context, surfaceContainer, outlineColor))
            addState(intArrayOf(), outlinedButtonBackground(
                button.context, Color.TRANSPARENT, outlineColor))
        }
        button.stateListAnimator = null
        button.elevation = 0f
    }

    /** Material 3 outlined destructive action, without a filled error surface. */
    fun styleDangerOutlinedButton(button: Button) {
        styleOutlinedButton(button, danger, danger)
    }

    /**
     * Material 3 semantic button mapping for screens that still expose ButtonKind.
     * The legacy styleButton remains available for screens that intentionally use
     * its older visual contract.
     */
    fun styleMaterialButton(button: Button, kind: ButtonKind) {
        when (kind) {
            ButtonKind.Primary -> styleTonalButton(button)
            ButtonKind.Danger -> styleDangerOutlinedButton(button)
            ButtonKind.Secondary, ButtonKind.Plain -> styleOutlinedButton(button)
        }
    }

    private fun outlinedButtonBackground(context: Context, fillColor: Int, strokeColor: Int): Drawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(context, UiMetrics.Radius).toFloat()
            setColor(fillColor)
            setStroke(dp(context, 1), strokeColor)
        }

    fun styleInput(input: EditText, backgroundColor: Int? = null) {
        refresh(input.context)
        input.textSize = UiMetrics.Body
        input.setTextColor(textPrimary)
        input.setHintTextColor(textSecondary)
        input.setSingleLine(true)
        input.background = rounded(input.context, backgroundColor ?: surface)
        input.setPadding(dp(input.context, UiMetrics.Content), dp(input.context, UiMetrics.Medium),
            dp(input.context, UiMetrics.Content), dp(input.context, UiMetrics.Medium))
        input.minHeight = dp(input.context, UiMetrics.Touch)
        input.isSaveEnabled = false
    }

    fun styleBodyText(textView: TextView, muted: Boolean = false) {
        refresh(textView.context)
        textView.setTextColor(if (muted) textSecondary else textPrimary)
        textView.includeFontPadding = true
    }

    fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}

enum class ButtonKind { Primary, Secondary, Danger, Plain }
