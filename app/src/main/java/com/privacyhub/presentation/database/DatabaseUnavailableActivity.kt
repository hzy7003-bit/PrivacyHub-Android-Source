package com.privacyhub.presentation.database

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.privacyhub.presentation.MainActivity
import com.privacyhub.presentation.ui.ButtonKind
import com.privacyhub.presentation.ui.FrostedUi
import com.privacyhub.security.lock.SecureWindow

class DatabaseUnavailableActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SecureWindow.apply(this)
        FrostedUi.applyWindow(this)
        render()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            FrostedUi.applyPageChrome(this, leftDp = 24, topDp = 48, rightDp = 24, bottomDp = 24)
        }
        root.addView(TextView(this).apply {
            text = "安全箱暂时无法打开"
            textSize = 24f
            setTextColor(FrostedUi.textPrimary)
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "为避免覆盖本地数据，隐私中转站已经停止打开数据库。请不要卸载 App，也不要在系统设置中清除数据。"
            textSize = 15f
            setTextColor(FrostedUi.textSecondary)
            setPadding(0, dp(12), 0, dp(20))
        })
        root.addView(actionButton("重试打开", ButtonKind.Primary) {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            finish()
        })
        root.addView(actionButton("复制本地错误码", ButtonKind.Secondary) {
            val clipboard = getSystemService(ClipboardManager::class.java)
            clipboard?.setPrimaryClip(ClipData.newPlainText("Privacy Hub Error", ERROR_CODE))
            Toast.makeText(this, "已复制错误码 $ERROR_CODE", Toast.LENGTH_SHORT).show()
        })
        root.addView(TextView(this).apply {
            text = "错误码：$ERROR_CODE\n反馈时请同时提供手机品牌、Android 版本和 App 版本。错误码不包含安全箱内容。"
            textSize = 13f
            setTextColor(FrostedUi.textSecondary)
            setPadding(0, dp(16), 0, 0)
        })
        setContentView(root)
    }

    private fun actionButton(label: String, kind: ButtonKind, action: () -> Unit): Button {
        return Button(this).apply {
            text = label
            FrostedUi.styleMaterialButton(this, kind)
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(10) }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val ERROR_CODE = "PH-DB-OPEN-001"
    }
}
