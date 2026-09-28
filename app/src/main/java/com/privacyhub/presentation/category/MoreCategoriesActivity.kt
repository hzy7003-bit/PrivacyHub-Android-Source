package com.privacyhub.presentation.category

import android.content.Intent
import android.graphics.Typeface
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.privacyhub.data.repository.LocalStore
import com.privacyhub.presentation.security.SecureActivity
import com.privacyhub.presentation.ui.ButtonKind
import com.privacyhub.presentation.ui.FrostedUi

class MoreCategoriesActivity : SecureActivity() {
    private val store by lazy { LocalStore(this) }

    override fun onUnlocked() {
        render()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            FrostedUi.applyPageChrome(this)
        }
        root.addView(title("更多标签"))
        root.addView(subtitle("这里显示所有分类标签，不包含收藏夹。"))

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(14), 0, 0)
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val categories = store.categories()
        if (categories.isEmpty()) {
            content.addView(TextView(this).apply {
                text = "还没有标签。保存内容后会自动出现，也可以在设置里添加。"
                textSize = 15f
                setTextColor(FrostedUi.textSecondary)
            })
        } else {
            categories.forEach { category ->
                content.addView(fullWidthButton(category, ButtonKind.Secondary) {
                    startActivity(Intent(this, CategoryDetailActivity::class.java).apply {
                        putExtra(CategoryDetailActivity.EXTRA_MODE, CategoryDetailActivity.MODE_CATEGORY)
                        putExtra(CategoryDetailActivity.EXTRA_CATEGORY, category)
                    })
                })
            }
        }

        root.addView(fullWidthButton("返回", ButtonKind.Secondary) { finish() })
        setContentView(root)
    }

    private fun title(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 26f
        setTextColor(FrostedUi.textPrimary)
        typeface = Typeface.DEFAULT_BOLD
    }

    private fun subtitle(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(FrostedUi.textSecondary)
        setPadding(0, dp(6), 0, 0)
    }

    private fun fullWidthButton(text: String, kind: ButtonKind, action: () -> Unit): Button = Button(this).apply {
        this.text = text
        FrostedUi.styleMaterialButton(this, kind)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(50)).apply {
            setMargins(0, 0, 0, dp(10))
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
