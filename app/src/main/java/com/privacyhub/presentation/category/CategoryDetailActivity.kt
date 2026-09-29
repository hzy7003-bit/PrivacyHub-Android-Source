package com.privacyhub.presentation.category

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.privacyhub.data.repository.LocalStore
import com.privacyhub.data.settings.AppSettingsStore
import com.privacyhub.domain.model.SavedItem
import com.privacyhub.presentation.components.SavedItemCardFactory
import com.privacyhub.presentation.security.SecureActivity
import com.privacyhub.presentation.ui.ButtonKind
import com.privacyhub.presentation.ui.FrostedUi
import com.privacyhub.util.ActivityTaskScope

class CategoryDetailActivity : SecureActivity() {
    private val store by lazy { LocalStore(this) }
    private val settingsStore by lazy { AppSettingsStore(this) }
    private val tasks by lazy { ActivityTaskScope(this) }
    private var mode: String = MODE_CATEGORY
    private var category: String = ""
    private var operationInFlight = false

    override fun onSecureCreate(savedInstanceState: android.os.Bundle?) {
        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_CATEGORY
        category = intent.getStringExtra(EXTRA_CATEGORY).orEmpty()
    }

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

        root.addView(title(screenTitle()))
        root.addView(subtitle(screenSubtitle()))
        root.addView(TextView(this).apply {
            text = "正在读取内容…"
            textSize = 14f
            setTextColor(FrostedUi.textSecondary)
            setPadding(0, dp(14), 0, 0)
        })
        root.addView(fullWidthButton("返回", ButtonKind.Secondary) { finish() })
        setContentView(root)

        val requestedMode = mode
        val requestedCategory = category
        val app = applicationContext
        val generation = tasks.nextGeneration()
        tasks.executeDisk(
            generation,
            task = {
                val localStore = LocalStore(app)
                val items = when (requestedMode) {
                    MODE_FAVORITES -> localStore.favoriteItems(limit = 100)
                    MODE_RECENT -> localStore.recentItems(limit = 100)
                    else -> localStore.categoryItems(requestedCategory, limit = 100)
                }
                CategoryContents(
                    items = items,
                    isSystemCategory = requestedMode == MODE_CATEGORY && localStore.isSystemCategory(requestedCategory)
                )
            },
            onResult = { owner, result -> (owner as CategoryDetailActivity).renderContents(result) }
        )
    }

    private fun renderContents(result: Result<CategoryContents>) {
        val loaded = result.getOrElse { error ->
            val root = pageRoot()
            root.addView(title("暂时无法读取内容"))
            root.addView(subtitle(error.message ?: "原有数据未更改，请重试。"))
            root.addView(fullWidthButton("重试", ButtonKind.Primary) { render() })
            root.addView(fullWidthButton("返回", ButtonKind.Secondary) { finish() })
            setContentView(root)
            return
        }
        val root = pageRoot()
        root.addView(title(screenTitle()))
        root.addView(subtitle(screenSubtitle()))
        if (mode == MODE_CATEGORY) root.addView(manageRow(loaded.isSystemCategory))
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(14), 0, 0)
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val items = loaded.items
        val heading = listHeading(items.size)
        content.addView(SavedItemCardFactory.sectionTitle(this, heading))
        if (items.isEmpty()) {
            content.addView(SavedItemCardFactory.emptyText(this, "这里还没有内容。"))
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

    private fun pageRoot() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        FrostedUi.applyPageChrome(this)
    }

    private fun manageRow(isSystem: Boolean): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, dp(4))
            addView(rowButton("标签重命名") {
                showRenameDialog()
            })
            addView(space(dp(8), 1))
            addView(rowButton(if (isSystem) "清空/整理内容" else "删除标签") {
                showDeleteOrOrganizeOptions(isSystem)
            })
        }
    }

    private fun screenTitle(): String {
        return when (mode) {
            MODE_FAVORITES -> "收藏夹"
            MODE_RECENT -> "最近保存"
            else -> category.ifBlank { "分类" }
        }
    }

    private fun screenSubtitle(): String {
        return when (mode) {
            MODE_FAVORITES -> "跨分类收藏内容"
            MODE_RECENT -> "按保存时间查看全部内容"
            else -> "查看和整理此标签下的内容"
        }
    }

    private fun listHeading(count: Int): String {
        return when (mode) {
            MODE_FAVORITES -> "收藏内容（$count）"
            MODE_RECENT -> "最近保存（$count）"
            else -> "$category（$count）"
        }
    }

    private fun showDeleteOrOrganizeOptions(isSystem: Boolean) {
        val title = if (isSystem) "清空/整理内容" else "删除标签"
        val first = if (isSystem) {
            "把内容移入“其他”"
        } else {
            "删除标签，内容移入“其他”"
        }
        val second = if (isSystem) {
            "删除此标签下所有内容"
        } else {
            "删除标签，并删除里面所有内容"
        }
        val third = if (isSystem) {
            "把内容移入已有标签或新标签"
        } else {
            "删除标签，内容移入已有标签或新标签"
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(arrayOf(first, second, third)) { _, which ->
                when (which) {
                    0 -> moveToOtherAndMaybeRemoveTag()
                    1 -> confirmDeleteItemsAndMaybeRemoveTag(isSystem)
                    2 -> chooseTargetCategory()
                }
            }
            .show()
    }

    private fun moveToOtherAndMaybeRemoveTag() {
        val app = applicationContext
        val source = category
        runMutation(
            task = { LocalStore(app).let { localStore ->
                localStore.moveCategoryAndRemoveCustomSource(source, "其他")
            } },
            onSuccess = { activity, count ->
                Toast.makeText(activity, "已移入“其他”：$count 条", Toast.LENGTH_SHORT).show()
                activity.finish()
            }
        )
    }

    private fun confirmDeleteItemsAndMaybeRemoveTag(isSystem: Boolean) {
        if (operationInFlight) return
        val app = applicationContext
        val source = category
        runQuery(
            task = { LocalStore(app).countByCategory(source) },
            onSuccess = { activity, count -> activity.showConfirmDeleteItems(count, isSystem) }
        )
    }

    private fun showConfirmDeleteItems(count: Int, isSystem: Boolean) {
        val source = category
        AlertDialog.Builder(this)
            .setTitle("确认删除内容")
            .setMessage("将删除“$source”中的 $count 条内容，此操作不可恢复。")
            .setNegativeButton("取消", null)
            .setPositiveButton("确认删除内容") { _, _ ->
                val app = applicationContext
                runMutation(
                    task = { LocalStore(app).let { localStore ->
                        localStore.deleteCategoryItemsAndMaybeRemoveCustomCategory(source, removeCustomCategory = !isSystem)
                    } },
                    onSuccess = { activity, deleted ->
                        Toast.makeText(activity, "已删除 $deleted 条内容", Toast.LENGTH_SHORT).show()
                        activity.finish()
                    }
                )
            }
            .show()
    }

    private fun chooseTargetCategory() {
        val app = applicationContext
        val source = category
        runQuery(
            task = { LocalStore(app).categories().filterNot { it == source } },
            onSuccess = { activity, categories -> activity.showTargetCategories(categories) }
        )
    }

    private fun showTargetCategories(categories: List<String>) {
        val options = (categories + "创建新标签").toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("移入哪个标签")
            .setItems(options) { _, which ->
                val selected = options[which]
                if (selected == "创建新标签") showMoveToNewCategoryDialog()
                else moveToCategory(selected)
            }
            .show()
    }

    private fun showMoveToNewCategoryDialog() {
        val input = EditText(this).apply {
            hint = "新标签名称"
            setSingleLine(true)
            FrostedUi.styleInput(this)
        }
        AlertDialog.Builder(this)
            .setTitle("创建新标签")
            .setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton("移动") { _, _ ->
                val target = input.text?.toString().orEmpty().trim()
                if (target.isBlank()) {
                    Toast.makeText(this, "标签名不能为空", Toast.LENGTH_SHORT).show()
                } else {
                    moveToCategory(target)
                }
            }
            .show()
    }

    private fun moveToCategory(target: String) {
        val app = applicationContext
        val source = category
        runMutation(
            task = { LocalStore(app).let { localStore ->
                localStore.moveCategoryAndRemoveCustomSource(source, target)
            } },
            onSuccess = { activity, count ->
                Toast.makeText(activity, "已移动 $count 条内容", Toast.LENGTH_SHORT).show()
                activity.finish()
            }
        )
    }

    private fun showRenameDialog() {
        val input = EditText(this).apply {
            hint = "新标签名称"
            setText(category)
            setSingleLine(true)
            FrostedUi.styleInput(this)
        }
        AlertDialog.Builder(this)
            .setTitle("标签重命名")
            .setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存") { _, _ ->
                val newName = input.text?.toString().orEmpty().trim()
                if (newName.isBlank()) {
                    Toast.makeText(this, "标签名不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val app = applicationContext
                val source = category
                runMutation(
                    task = { LocalStore(app).renameCategory(source, newName) },
                    onSuccess = { activity, _ ->
                        activity.category = newName
                        Toast.makeText(activity, "已重命名", Toast.LENGTH_SHORT).show()
                        activity.render()
                    }
                )
            }
            .show()
    }

    private fun <R> runQuery(task: () -> R, onSuccess: (CategoryDetailActivity, R) -> Unit) {
        if (operationInFlight) return
        operationInFlight = true
        val generation = tasks.nextGeneration()
        tasks.executeDisk(generation, task, onResult = { owner, result ->
            val activity = owner as CategoryDetailActivity
            activity.operationInFlight = false
            result.fold(onSuccess = { onSuccess(activity, it) }, onFailure = activity::showOperationFailure)
        })
    }

    private fun <R> runMutation(task: () -> R, onSuccess: (CategoryDetailActivity, R) -> Unit) {
        runQuery(task, onSuccess)
    }

    private fun showOperationFailure(error: Throwable) {
        AlertDialog.Builder(this)
            .setTitle("操作未完成")
            .setMessage(error.message ?: "原有数据未更改，请重试。")
            .setPositiveButton("知道了", null)
            .show()
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

    private fun rowButton(
        text: String,
        kind: ButtonKind = ButtonKind.Secondary,
        action: () -> Unit
    ): Button = Button(this).apply {
        this.text = text
        FrostedUi.styleMaterialButton(this, kind)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f)
    }

    private fun fullWidthButton(text: String, kind: ButtonKind, action: () -> Unit): Button = Button(this).apply {
        this.text = text
        FrostedUi.styleMaterialButton(this, kind)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply {
            setMargins(0, dp(8), 0, 0)
        }
    }

    private fun space(width: Int, height: Int): android.view.View = android.view.View(this).apply {
        layoutParams = LinearLayout.LayoutParams(width, height)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private data class CategoryContents(
        val items: List<SavedItem>,
        val isSystemCategory: Boolean
    )

    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_CATEGORY = "category"
        const val MODE_CATEGORY = "category"
        const val MODE_FAVORITES = "favorites"
        const val MODE_RECENT = "recent"
    }
}
