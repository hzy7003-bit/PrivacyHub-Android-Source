package com.privacyhub.presentation.guide

import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.privacyhub.BuildConfig
import com.privacyhub.presentation.security.SecureActivity
import com.privacyhub.presentation.ui.FrostedUi
import com.privacyhub.presentation.ui.PorcelainViews
import com.privacyhub.presentation.ui.UiMetrics

class TrialGuideActivity : SecureActivity() {
    private var scroll: ScrollView? = null
    private var scrollPosition = 0

    override fun onSecureCreate(savedInstanceState: Bundle?) {
        scrollPosition = savedInstanceState?.getInt("guide_scroll") ?: 0
    }

    override fun onPause() {
        scrollPosition = scroll?.scrollY ?: scrollPosition
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("guide_scroll", scroll?.scrollY ?: scrollPosition)
        super.onSaveInstanceState(outState)
    }

    override fun onUnlocked() {
        val views = PorcelainViews(this)
        val root = views.column().apply { FrostedUi.applyPageChrome(this) }
        root.addView(views.back { finish() })
        val content = views.column().apply {
            addView(views.title("使用指南"))
            addView(views.text("从中转开始，让内容留在自己手里。", muted = true))
            addView(views.text("当前版本 ${BuildConfig.VERSION_NAME} · 本地 Beta", UiMetrics.Caption, muted = true))
            GuideContent.sections.forEach { section ->
                addView(views.section(section.title))
                addView(views.text(section.body).apply { setLineSpacing(views.dp(UiMetrics.Micro).toFloat(), 1f) })
            }
            addView(views.button("返回") { finish() })
        }
        val width = (resources.configuration.screenWidthDp - UiMetrics.Gutter * 2)
            .coerceAtMost(UiMetrics.ReadingMaxWidth)
        val readingFrame = FrameLayout(this).apply {
            addView(content, FrameLayout.LayoutParams(views.dp(width), -2, Gravity.CENTER_HORIZONTAL))
        }
        scroll = ScrollView(this).apply { addView(readingFrame) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        scroll?.post { scroll?.scrollTo(0, scrollPosition) }
    }
}
