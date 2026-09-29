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

class CategoryManagementActivity : SecureActivity() {
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
        root.addView(title("分类管理"))
        root.addView(subtitle("管理分类名称和自动识别映射。整理分类不会删除安全箱内容。"))

        root.addView(emptyText("正在读取分类…"))
        root.addView(fullWidthButton("返回设置", ButtonKind.Secondary) {
            finish()
        })
        setContentView(root)

        val app = applicationContext
        val generation = tasks.nextGeneration()
        tasks.executeDisk(
            generation,
            task = {
                val localStore = LocalStore(app)
                val readError = localStore.categorySettingsReadError()
                CategoryState(
                    readError = readError,
                    customCategories = if (readError == null) {
                        localStore.categories().filterNot(localStore::isSystemCategory)
                    } else emptyList()
                )
            },
            onResult = { owner, result -> (owner as CategoryManagementActivity).renderCategories(result) }
        )
    }

    private fun renderCategories(result: Result<CategoryState>) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            FrostedUi.applyPageChrome(this)
            addView(title("分类管理"))
            addView(subtitle("管理分类名称和自动识别映射。整理分类不会删除安全箱内容。"))
        }
        val state = result.getOrElse { error ->
            root.addView(emptyText(error.message ?: "原有数据未更改，请重试。"))
            root.addView(fullWidthButton("重试", ButtonKind.Primary) { render() })
            root.addView(fullWidthButton("返回设置", ButtonKind.Secondary) { finish() })
            setContentView(root)
            return
        }
        if (state.readError != null) {
            root.addView(emptyText(state.readError))
            root.addView(fullWidthButton("返回设置", ButtonKind.Secondary) { finish() })
            setContentView(root)
            return
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(14), 0, 0)
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        content.addView(fullWidthButton("新增自定义标签", ButtonKind.Primary) { showAddCategoryDialog() })
        content.addView(sectionTitle("已有标签"))
        if (state.customCategories.isEmpty()) {
            content.addView(emptyText("还没有自定义标签。系统标签会自动存在，不需要手动创建。"))
        } else {
            state.customCategories.forEach { content.addView(customCategoryCard(it)) }
        }
        content.addView(sectionTitle("系统自动识别标签"))
        content.addView(emptyText("这些标签由本地识别规则生成，不能删除。你可以改成自己习惯的名称。"))
        AppSettingsStore.DEFAULT_CATEGORIES.forEach { content.addView(systemCategoryCard(it)) }
        root.addView(fullWidthButton("返回设置", ButtonKind.Secondary) { finish() })
        setContentView(root)
    }

    private fun customCategoryCard(category: String): LinearLayout {
        return card(
            heading = category,
            body = "自定义标签。可以重命名，或把这个标签下的内容移入“其他”。不会删除内容。"
        ) {
            addView(fullWidthButton("重命名标签", ButtonKind.Secondary) {
                showRenameCategoryDialog(category)
            })
            addView(fullWidthButton("移入其他", ButtonKind.Secondary) {
                confirmMoveToOther(category)
            })
        }
    }

    private fun systemCategoryCard(category: String): LinearLayout {
        return card(
            heading = category,
            body = "系统自动识别到“$category”时使用的标签。系统标签不能删除，也不会在这里清空内容。"
        ) {
            addView(fullWidthButton("改成我的标签名", ButtonKind.Secondary) {
                showRenameCategoryDialog(category)
            })
        }
    }

    private fun showAddCategoryDialog() {
        showCategoryNameDialog(
            title = "新增自定义标签",
            initialValue = "",
            positiveText = "新增"
        ) { name ->
            val app = applicationContext
            runOperation(
                task = { LocalStore(app).addCategory(name) },
                onSuccess = { activity, added ->
                    Toast.makeText(activity, if (added) "已新增标签" else "标签已存在、属于系统标签，或名称为空", Toast.LENGTH_SHORT).show()
                    activity.render()
                }
            )
        }
    }

    private fun showRenameCategoryDialog(oldName: String) {
        showCategoryNameDialog(
            title = "重命名标签",
            initialValue = oldName,
            positiveText = "保存"
        ) { newName ->
            if (newName.trim().isBlank()) {
                Toast.makeText(this, "标签名称不能为空", Toast.LENGTH_SHORT).show()
                return@showCategoryNameDialog
            }
            val app = applicationContext
            runOperation(
                task = { LocalStore(app).renameCategory(oldName, newName) },
                onSuccess = { activity, _ ->
                    Toast.makeText(activity, "已更新标签", Toast.LENGTH_SHORT).show()
                    activity.render()
                }
            )
        }
    }

    private fun confirmMoveToOther(category: String) {
        AlertDialog.Builder(this)
            .setTitle("移入其他")
            .setMessage("确定把“$category”下的内容移入“其他”？内容不会删除，只是不再属于这个标签。")
            .setNegativeButton("取消", null)
            .setPositiveButton("移入其他") { _, _ ->
                val app = applicationContext
                runOperation(
                    task = { LocalStore(app).let { localStore ->
                        localStore.moveCategoryAndRemoveCustomSource(category, "其他")
                    } },
                    onSuccess = { activity, count ->
                        Toast.makeText(activity, "已移入“其他”：$count 条", Toast.LENGTH_SHORT).show()
                        activity.render()
                    }
                )
            }
            .show()
    }

    private fun <R> runOperation(task: () -> R, onSuccess: (CategoryManagementActivity, R) -> Unit) {
        if (operationInFlight) return
        operationInFlight = true
        val generation = tasks.nextGeneration()
        tasks.executeDisk(generation, task, onResult = { owner, result ->
            val activity = owner as CategoryManagementActivity
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

    private fun showCategoryNameDialog(
        title: String,
        initialValue: String,
        positiveText: String,
        onConfirm: (String) -> Unit
    ) {
        val input = EditText(this).apply {
            hint = "标签名称，比如：密码提示"
            setText(initialValue)
            setSingleLine(true)
            FrostedUi.styleInput(this)
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton(positiveText) { _, _ ->
                onConfirm(input.text?.toString().orEmpty())
            }
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
            addView(TextView(this@CategoryManagementActivity).apply {
                text = heading
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(FrostedUi.textPrimary)
            })
            addView(TextView(this@CategoryManagementActivity).apply {
                text = body
                textSize = 14f
                setTextColor(FrostedUi.textSecondary)
                setPadding(0, dp(6), 0, dp(8))
            })
            configure()
        }
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
        setPadding(0, dp(14), 0, dp(8))
    }

    private fun emptyText(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(FrostedUi.textSecondary)
        setPadding(0, dp(6), 0, dp(12))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private data class CategoryState(val readError: String?, val customCategories: List<String>)
}
