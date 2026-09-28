package com.privacyhub.presentation.guide

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.fragment.app.DialogFragment
import com.privacyhub.R
import com.privacyhub.data.settings.AppSettingsStore
import com.privacyhub.presentation.ui.ButtonKind
import com.privacyhub.presentation.ui.FrostedUi
import com.privacyhub.presentation.ui.PorcelainViews
import com.privacyhub.presentation.ui.UiMetrics

/** Non-sensitive welcome content; FragmentManager owns rotation and Back dismissal. */
class WelcomeDialogFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        FrostedUi.refresh(context)
        val views = PorcelainViews(context)
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val content = views.column().apply {
            val padding = views.dp(UiMetrics.Section)
            setPadding(padding, padding, padding, padding)
            addView(ImageView(context).apply {
                setImageResource(R.drawable.brand_flower)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                background = FrostedUi.rounded(context, Color.WHITE)
                clipToOutline = true
            }, LinearLayout.LayoutParams(views.dp(UiMetrics.BrandBadge), views.dp(UiMetrics.BrandBadge)))
            addView(views.section("欢迎来到隐私中转站"))
            addView(views.text("先保存，再安心处理。", muted = true))
            addView(views.text("中转 · 添加、分享和处理内容\n安全箱 · 查找、收藏与整理\n设置 · 按需开启辅助能力"))
            addView(views.text("本机加密保存 · 无广告 · 无统计\n默认无网络权限，不自动云同步。", UiMetrics.Caption, muted = true))
            addView(views.button("开始使用", ButtonKind.Primary) { acknowledge(); dismiss() })
            addView(views.button("阅读使用指南", ButtonKind.Plain) {
                acknowledge()
                startActivity(Intent(context, TrialGuideActivity::class.java))
                dismiss()
            })
        }
        dialog.setContentView(ScrollView(context).apply {
            background = FrostedUi.rounded(context, FrostedUi.pageColor, UiMetrics.SheetRadius)
            clipToOutline = true
            addView(content)
        })
        dialog.setCanceledOnTouchOutside(false)
        return dialog
    }

    override fun onStart() {
        super.onStart()
        val window = dialog?.window ?: return
        val config = resources.configuration
        val width = FrostedUi.dp(requireContext(), (config.screenWidthDp - UiMetrics.Gutter * 2)
            .coerceAtMost(UiMetrics.DialogMaxWidth).coerceAtLeast(UiMetrics.Touch))
        val heightLimit = (FrostedUi.dp(requireContext(), config.screenHeightDp) * UiMetrics.DialogHeightFraction).toInt()
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setDimAmount(UiMetrics.ModalDim)
        val content = window.findViewById<View>(android.R.id.content)
        content.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightLimit, View.MeasureSpec.AT_MOST))
        window.setLayout(width, content.measuredHeight.coerceAtMost(heightLimit))
    }

    private fun acknowledge() {
        AppSettingsStore(requireContext()).setLastSeenTrialNoticeVersion(requireArguments().getString(KEY_NOTICE)!!)
    }

    companion object {
        const val TAG = "welcome-guide"
        private const val KEY_NOTICE = "notice_key"
        fun newInstance(key: String) = WelcomeDialogFragment().apply {
            arguments = Bundle().apply { putString(KEY_NOTICE, key) }
        }
    }
}
