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

class CategoryDetailActivity : SecureActivity() {
    private val store by lazy { LocalStore(this) }
    private val settingsStore by lazy { AppSettingsStore(this) }
    private var mode: String = MODE_CATEGORY
    private var category: String = ""

    override fun onSecureCreate(savedInstanceState: android.os.Bundle?) {
        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_CATEGORY
        category = intent.getStringExtra(EXTRA_CATEGORY).orEmpty()
    }

    override fun onUnlocked() {
        render()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            FrostedUi.applyPageChrome(this)
        }

        root.addView(title(screenTitle()))
        root.addView(subtitle(screenSubtitle()))
        if (mode == MODE_CATEGORY) {
            root.addView(manageRow())
        }

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(14), 0, 0)
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val items = loadItems()
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

    private fun manageRow(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, dp(4))
            addView(rowButton("标签重命名") {
                showRenameDialog()
            })
            addView(space(dp(8), 1))
            val isSystem = store.isSystemCategory(category)
            addView(rowButton(if (isSystem) "清空/整理内容" else "删除标签") {
                showDeleteOrOrganizeOptions(isSystem)
            })
        }
    }

    private fun loadItems(): List<SavedItem> {
        return when (mode) {
            MODE_FAVORITES -> store.favoriteItems(limit = 100)
            MODE_RECENT -> store.recentItems(limit = 100)
            else -> store.categoryItems(category, limit = 100)
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
                    0 -> moveToOtherAndMaybeRemoveTag(isSystem)
                    1 -> confirmDeleteItemsAndMaybeRemoveTag(isSystem)
                    2 -> chooseTargetCategory(isSystem)
                }
            }
            .show()
    }

    private fun moveToOtherAndMaybeRemoveTag(isSystem: Boolean) {
        val count = store.moveCategory(category, "其他")
        if (!isSystem) {
            store.deleteCustomCategory(category)
        }
        Toast.makeText(this, "已移入“其他”：$count 条", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun confirmDeleteItemsAndMaybeRemoveTag(isSystem: Boolean) {
        val count = store.countByCategory(category)
        AlertDialog.Builder(this)
            .setTitle("确认删除内容")
            .setMessage("将删除“$category”中的 $count 条内容，此操作不可恢复。")
            .setNegativeButton("取消", null)
            .setPositiveButton("确认删除内容") { _, _ ->
                val deleted = store.deleteCategoryItems(category)
                if (!isSystem) {
                    store.deleteCustomCategory(category)
                }
                Toast.makeText(this, "已删除 $deleted 条内容", Toast.LENGTH_SHORT).show()
                finish()
            }
            .show()
    }

    private fun chooseTargetCategory(isSystem: Boolean) {
        val categories = store.categories().filterNot { it == category }
        val options = (categories + "创建新标签").toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("移入哪个标签")
            .setItems(options) { _, which ->
                val selected = options[which]
                if (selected == "创建新标签") {
                    showMoveToNewCategoryDialog(isSystem)
                } else {
                    moveToCategory(selected, isSystem)
                }
            }
            .show()
    }

    private fun showMoveToNewCategoryDialog(isSystem: Boolean) {
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
                    moveToCategory(target, isSystem)
                }
            }
            .show()
    }

    private fun moveToCategory(target: String, isSystem: Boolean) {
        val count = store.moveCategory(category, target)
        if (!isSystem) {
            store.deleteCustomCategory(category)
        }
        Toast.makeText(this, "已移动 $count 条内容", Toast.LENGTH_SHORT).show()
        finish()
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
                store.renameCategory(category, newName)
                Toast.makeText(this, "已重命名", Toast.LENGTH_SHORT).show()
                category = newName
                render()
            }
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

    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_CATEGORY = "category"
        const val MODE_CATEGORY = "category"
        const val MODE_FAVORITES = "favorites"
        const val MODE_RECENT = "recent"
    }
}
