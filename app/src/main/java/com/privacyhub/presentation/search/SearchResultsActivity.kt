package com.privacyhub.presentation.search

import android.graphics.Typeface
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.privacyhub.data.repository.LocalStore
import com.privacyhub.data.settings.AppSettingsStore
import com.privacyhub.presentation.components.SavedItemCardFactory
import com.privacyhub.presentation.security.SecureActivity
import com.privacyhub.presentation.ui.ButtonKind
import com.privacyhub.presentation.ui.FrostedUi
import com.privacyhub.util.ActivityTaskScope

class SearchResultsActivity : SecureActivity() {
    private val store by lazy { LocalStore(this) }
    private val settingsStore by lazy { AppSettingsStore(this) }
    private val tasks by lazy { ActivityTaskScope(this) }
    private var query: String = ""

    override fun onSecureCreate(savedInstanceState: android.os.Bundle?) {
        query = intent.getStringExtra(EXTRA_QUERY).orEmpty()
    }

    override fun onUnlocked() {
        render()
    }

    override fun onDestroy() {
        tasks.close()
        super.onDestroy()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            FrostedUi.applyPageChrome(this)
        }
        root.addView(title("搜索结果"))
        root.addView(subtitle("关键词：$query"))

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(14), 0, 0)
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        content.addView(SavedItemCardFactory.emptyText(this, "正在搜索…"))
        root.addView(fullWidthButton("返回", ButtonKind.Secondary) { finish() })
        setContentView(root)

        val requestedQuery = query
        val app = applicationContext
        val generation = tasks.nextGeneration()
        tasks.executeDisk(
            generation,
            task = { LocalStore(app).searchItems(requestedQuery, limit = 100) },
            onResult = { owner, result ->
                (owner as SearchResultsActivity).renderResults(requestedQuery, result)
            }
        )
    }

    private fun renderResults(requestedQuery: String, result: Result<List<com.privacyhub.domain.model.SavedItem>>) {
        if (requestedQuery != query) return
        val items = result.getOrElse { error ->
            val root = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                FrostedUi.applyPageChrome(this)
                addView(title("暂时无法搜索"))
                addView(subtitle(error.message ?: "原有数据未更改，请重试。"))
                addView(fullWidthButton("重试", ButtonKind.Primary) { render() })
                addView(fullWidthButton("返回", ButtonKind.Secondary) { finish() })
            }
            setContentView(root)
            return
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            FrostedUi.applyPageChrome(this)
        }
        root.addView(title("搜索结果"))
        root.addView(subtitle("关键词：$requestedQuery"))
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(14), 0, 0)
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        content.addView(SavedItemCardFactory.sectionTitle(this, "匹配内容（${items.size}）"))
        if (items.isEmpty()) {
            content.addView(SavedItemCardFactory.emptyText(this, "没有匹配“$query”的内容。"))
        } else {
            items.forEach { item ->
                content.addView(
                    SavedItemCardFactory.create(
                        activity = this,
                        store = store,
                        settingsStore = settingsStore,
                        item = item,
                        onChanged = ::render
                    )
                )
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
        layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply {
            setMargins(0, dp(8), 0, 0)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_QUERY = "query"
    }
}
