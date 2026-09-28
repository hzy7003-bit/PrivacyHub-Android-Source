package com.privacyhub.presentation.autofill

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.privacyhub.presentation.security.SecureActivity
import com.privacyhub.presentation.ui.ButtonKind
import com.privacyhub.presentation.ui.FrostedUi

class AutofillTestActivity : SecureActivity() {
    override fun onSecureCreate(savedInstanceState: Bundle?) {
        super.onSecureCreate(savedInstanceState)
        title = "自动填充调试页"
    }

    override fun onUnlocked() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            FrostedUi.applyPageChrome(this)
        }
        root.addView(titleText("自动填充调试页"))
        root.addView(bodyText("仅存在于 debug 测试包。没有网络、没有提交按钮，也不会保存输入内容。"))
        val username = autofillInput("测试账号", View.AUTOFILL_HINT_USERNAME)
        val password = autofillInput("测试密码", View.AUTOFILL_HINT_PASSWORD).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val status = bodyText("请点进账号或密码输入框，查看系统候选。")
        root.addView(username, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(24) })
        root.addView(password, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(12) })
        root.addView(Button(this).apply {
            text = "检查回填结果"
            FrostedUi.styleMaterialButton(this, ButtonKind.Secondary)
            setOnClickListener {
                status.text = if (username.text.isNotBlank() && password.text.isNotBlank()) "已回填测试值。页面不会提交或保存。" else "尚未同时回填账号和密码。"
            }
            layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(16) }
        })
        root.addView(status)
        root.addView(Button(this).apply {
            text = "返回"
            FrostedUi.styleMaterialButton(this, ButtonKind.Secondary)
            setOnClickListener { finish() }
            layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(16) }
        })
        setContentView(root)
    }

    private fun autofillInput(hint: String, autofillHint: String) = EditText(this).apply {
        this.hint = hint
        id = View.generateViewId()
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
        setAutofillHints(autofillHint)
        FrostedUi.styleInput(this)
    }

    private fun titleText(text: String) = TextView(this).apply {
        this.text = text
        textSize = 28f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        setTextColor(FrostedUi.textPrimary)
    }

    private fun bodyText(text: String) = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(FrostedUi.textSecondary)
        setPadding(0, dp(10), 0, 0)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
