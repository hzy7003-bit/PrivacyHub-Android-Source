package com.privacyhub.presentation.category

import android.app.AlertDialog
import android.graphics.Typeface
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.privacyhub.data.repository.LocalStore
import com.privacyhub.data.settings.AppSettingsStore
import com.privacyhub.presentation.security.SecureActivity
import com.privacyhub.presentation.ui.ButtonKind
import com.privacyhub.presentation.ui.FrostedUi
import com.privacyhub.util.ActivityTaskScope

class HomeTagManagementActivity : SecureActivity() {
    private val tasks by lazy { ActivityTaskScope(this) }
    private var operationInFlight = false

    override fun onUnlocked() {
        if (operationInFlight) return
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
        root.addView(title("管理首页标签"))
        root.addView(subtitle("只控制首页显示，不会删除安全箱内容。最多显示 ${AppSettingsStore.MAX_HOME_TAGS} 个常用标签。"))

        root.addView(emptyText("正在读取标签…"))
        root.addView(fullWidthButton("返回设置", ButtonKind.Secondary) { finish() })
        setContentView(root)

        val app = applicationContext
        val generation = tasks.nextGeneration()
        tasks.executeDisk(
            generation,
            task = {
                val localStore = LocalStore(app)
                val error = localStore.categorySettingsReadError()
                HomeTagState(
                    error = error,
                    homeTags = if (error == null) localStore.homeTags() else emptyList(),
                    allTags = if (error == null) localStore.categories() else emptyList()
                )
            },
            onResult = { owner, result -> (owner as HomeTagManagementActivity).renderTags(result) }
        )
    }

    private fun renderTags(result: Result<HomeTagState>) {
        val state = result.getOrElse { error ->
            showReadFailure(error)
            return
        }
        if (state.error != null) {
            val root = pageRoot()
            root.addView(emptyText(state.error))
            root.addView(fullWidthButton("返回设置", ButtonKind.Secondary) { finish() })
            setContentView(root)
            return
        }
        val root = pageRoot()
        root.addView(title("管理首页标签"))
        root.addView(subtitle("只控制首页显示，不会删除安全箱内容。最多显示 ${AppSettingsStore.MAX_HOME_TAGS} 个常用标签。"))
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(14), 0, 0)
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        content.addView(fullWidthButton("新增标签", ButtonKind.Primary) { showAddCategoryDialog() })
        val homeTags = state.homeTags
        content.addView(sectionTitle("首页正在显示"))
        if (homeTags.isEmpty()) {
            content.addView(emptyText("当前首页只显示“更多”。"))
        } else {
            homeTags.forEachIndexed { index, category ->
                content.addView(homeTagCard(category, index, homeTags.lastIndex))
            }
        }

        val hiddenTags = state.allTags.filterNot { it in homeTags }
        content.addView(sectionTitle("更多里的标签"))
        if (hiddenTags.isEmpty()) {
            content.addView(emptyText("所有标签都已经显示在首页。"))
        } else {
            hiddenTags.forEach { category ->
                content.addView(hiddenTagCard(category))
            }
        }

        root.addView(fullWidthButton("返回设置", ButtonKind.Secondary) { finish() })
        setContentView(root)
    }

    private fun pageRoot() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        FrostedUi.applyPageChrome(this)
    }

    private fun showReadFailure(error: Throwable) {
        val root = pageRoot()
        root.addView(title("暂时无法读取标签"))
        root.addView(emptyText(error.message ?: "原有数据未更改，请重试。"))
        root.addView(fullWidthButton("重试", ButtonKind.Primary) { render() })
        root.addView(fullWidthButton("返回设置", ButtonKind.Secondary) { finish() })
        setContentView(root)
    }

    private fun homeTagCard(category: String, index: Int, lastIndex: Int): LinearLayout {
        return card(category, "显示在首页常用标签区。") {
            val firstRow = LinearLayout(this@HomeTagManagementActivity).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            firstRow.addView(rowButton("上移", enabled = index > 0) {
                val app = applicationContext
                runOperation({ LocalStore(app).moveHomeTag(category, -1) }) { activity, _ -> activity.render() }
            })
            firstRow.addView(space(dp(8), 1))
            firstRow.addView(rowButton("下移", enabled = index < lastIndex) {
                val app = applicationContext
                runOperation({ LocalStore(app).moveHomeTag(category, 1) }) { activity, _ -> activity.render() }
            })
            addView(firstRow)
            addView(fullWidthButton("从首页移除", ButtonKind.Secondary) {
                val app = applicationContext
                runOperation({ LocalStore(app).removeHomeTag(category) }) { activity, _ -> activity.render() }
            })
        }
    }

    private fun hiddenTagCard(category: String): LinearLayout {
        return card(category, "不在首页显示，但可以从“更多”进入。") {
            addView(fullWidthButton("添加到首页", ButtonKind.Secondary) {
                val app = applicationContext
                runOperation({ LocalStore(app).addHomeTag(category) }) { activity, added ->
                    if (added) activity.render() else Toast.makeText(
                        activity,
                        "首页最多显示 ${AppSettingsStore.MAX_HOME_TAGS} 个标签",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
        }
    }

    private fun showAddCategoryDialog() {
        val input = EditText(this).apply {
            hint = "标签名称"
            setSingleLine(true)
            FrostedUi.styleInput(this)
        }
        AlertDialog.Builder(this)
            .setTitle("新增标签")
            .setMessage("新增的是自定义标签。系统自动识别标签不能删除，但可以从首页移除。")
            .setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton("新增") { _, _ ->
                val name = input.text?.toString().orEmpty().trim()
                if (name.isBlank()) {
                    Toast.makeText(this, "标签名不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val app = applicationContext
                runOperation({
                    val localStore = LocalStore(app)
                    val added = localStore.addCategory(name)
                    val addedToHome = if (added) localStore.addHomeTag(name) else false
                    AddHomeTagResult(added, addedToHome)
                }) { activity, result ->
                    if (!result.added) {
                        Toast.makeText(activity, "标签已存在或属于系统标签", Toast.LENGTH_SHORT).show()
                    } else if (!result.addedToHome) {
                        Toast.makeText(activity, "标签已新增，可在更多里找到", Toast.LENGTH_SHORT).show()
                    }
                    activity.render()
                }
            }
            .show()
    }

    private fun <R> runOperation(task: () -> R, onSuccess: (HomeTagManagementActivity, R) -> Unit) {
        if (operationInFlight) return
        operationInFlight = true
        val generation = tasks.nextGeneration()
        tasks.executeDisk(generation, task, onResult = { owner, result ->
            val activity = owner as HomeTagManagementActivity
            activity.operationInFlight = false
            result.fold(
                onSuccess = { onSuccess(activity, it) },
                onFailure = { activity.showOperationFailure(it) }
            )
        })
    }

    private fun showOperationFailure(error: Throwable) {
        AlertDialog.Builder(this)
            .setTitle("操作未完成")
            .setMessage(error.message ?: "原有数据未更改，请重试。")
            .setPositiveButton("知道了", null)
            .show()
    }

    private fun card(heading: String, body: String, configure: LinearLayout.() -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            FrostedUi.applyPanel(this, radiusDp = 22)
            setPadding(dp(14), dp(12), dp(14), dp(14))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(0, 0, 0, dp(12))
            }
            addView(TextView(this@HomeTagManagementActivity).apply {
                text = heading
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(FrostedUi.textPrimary)
            })
            addView(TextView(this@HomeTagManagementActivity).apply {
                text = body
                textSize = 14f
                setTextColor(FrostedUi.textSecondary)
                setPadding(0, dp(6), 0, dp(8))
            })
            configure()
        }
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

    private fun sectionTitle(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 18f
        setTextColor(FrostedUi.textPrimary)
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(16), 0, dp(8))
    }

    private fun emptyText(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(FrostedUi.textSecondary)
        setPadding(0, dp(6), 0, dp(12))
    }

    private fun rowButton(text: String, enabled: Boolean = true, action: () -> Unit): Button = Button(this).apply {
        this.text = text
        isEnabled = enabled
        FrostedUi.styleOutlinedButton(this)
        setOnClickListener { if (enabled) action() }
        layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f)
    }

    private fun fullWidthButton(text: String, kind: ButtonKind, action: () -> Unit): Button = Button(this).apply {
        this.text = text
        styleActionButton(this, kind)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply {
            setMargins(0, dp(4), 0, 0)
        }
    }

    private fun styleActionButton(button: Button, kind: ButtonKind) {
        when (kind) {
            ButtonKind.Primary -> FrostedUi.styleTonalButton(button)
            ButtonKind.Danger -> FrostedUi.styleDangerOutlinedButton(button)
            ButtonKind.Secondary, ButtonKind.Plain -> FrostedUi.styleOutlinedButton(button)
        }
    }

    private fun space(width: Int, height: Int): android.view.View = android.view.View(this).apply {
        layoutParams = LinearLayout.LayoutParams(width, height)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private data class HomeTagState(
        val error: String?,
        val homeTags: List<String>,
        val allTags: List<String>
    )

    private data class AddHomeTagResult(val added: Boolean, val addedToHome: Boolean)
}
