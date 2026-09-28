package com.privacyhub.presentation.vault

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.os.Build
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import com.privacyhub.classifier.VaultContentSuggester
import com.privacyhub.data.repository.LocalStore
import com.privacyhub.data.settings.AppSettingsStore
import com.privacyhub.domain.model.VaultItem
import com.privacyhub.presentation.security.SecureActivity
import com.privacyhub.presentation.ui.ButtonKind
import com.privacyhub.presentation.ui.FrostedUi
import com.privacyhub.presentation.ui.PorcelainViews
import com.privacyhub.presentation.ui.UiMetrics
import com.privacyhub.presentation.transfer.TransferActivity
import com.privacyhub.router.LinkParser
import com.privacyhub.router.LinkTargetDetector
import com.privacyhub.router.NetdiskAccessCodeParser
import com.privacyhub.util.Formatters

class VaultDetailActivity : SecureActivity() {
    private val store by lazy { LocalStore(this) }
    private val settingsStore by lazy { AppSettingsStore(this) }
    private var itemId: Long = 0L
    private var createMode: Boolean = false
    private var item: VaultItem? = null

    private lateinit var titleInput: EditText
    private lateinit var contentInput: EditText
    private var accessCodeInput: EditText? = null
    private lateinit var categoryInput: EditText
    private lateinit var noteInput: EditText
    private var autoFillingSuggestion = false
    private var titleEditedByUser = false
    private var categoryEditedByUser = false
    private val draftModel by lazy { ViewModelProvider(this)[DetailDraftModel::class.java] }
    private val views by lazy { PorcelainViews(this) }
    private var editing = false
    private var formVisible = false

    override fun onSecureCreate(savedInstanceState: Bundle?) {
        itemId = savedInstanceState?.getLong(EXTRA_ITEM_ID) ?: intent.getLongExtra(EXTRA_ITEM_ID, 0L)
        createMode = savedInstanceState?.getBoolean(EXTRA_CREATE_MODE)
            ?: intent.getBooleanExtra(EXTRA_CREATE_MODE, itemId <= 0L)
        editing = createMode || (savedInstanceState?.getBoolean("editing") ?: false)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { leaveEditor() }
        })
    }

    override fun onPause() {
        if (formVisible && ::contentInput.isInitialized) {
            draftModel.draft = DetailDraft(titleInput.text.toString(), contentInput.text.toString(),
                categoryInput.text.toString(), noteInput.text.toString(), accessCodeInput?.text?.toString())
        }
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong(EXTRA_ITEM_ID, itemId)
        outState.putBoolean(EXTRA_CREATE_MODE, createMode)
        outState.putBoolean("editing", editing)
        super.onSaveInstanceState(outState)
    }

    override fun onUnlocked() {
        item = if (createMode) null else store.getItem(itemId)
        render()
    }

    private fun render() {
        val current = item
        formVisible = false
        if (current == null && !createMode) {
            setContentView(views.column().apply {
                FrostedUi.applyPageChrome(this)
                addView(views.title("内容不存在"))
                addView(views.button("返回") { finish() })
            })
            return
        }
        if (!editing && current != null) { renderDetail(current); return }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            FrostedUi.applyPageChrome(this, includeIme = false)
        }
        root.addView(views.title(if (createMode) "新建内容" else "编辑内容"))

        val scroll = ScrollView(this)
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        titleInput = input("标题，比如：淘宝商品、银行卡提示", current?.title.orEmpty(), singleLine = true)
        contentInput = input("保存的内容", current?.content.orEmpty(), singleLine = false,
            minLines = 3, maxLines = 8)
        accessCodeInput = current?.takeIf { it.target.supportsAccessCodeAutomation }?.let { item ->
            input(
                "网盘提取码（可选）",
                item.accessCode ?: NetdiskAccessCodeParser.extract(item.content).orEmpty(),
                singleLine = true
            )
        }
        categoryInput = input("分类，比如：购物、证件、口令", current?.category.orEmpty(), singleLine = true)
        noteInput = input("备注，可不填", current?.note.orEmpty(), singleLine = false,
            minLines = 2, maxLines = 6)
        draftModel.draft?.let { draft ->
            titleInput.setText(draft.title)
            contentInput.setText(draft.content)
            categoryInput.setText(draft.category)
            noteInput.setText(draft.note)
            accessCodeInput?.setText(draft.accessCode)
            titleEditedByUser = draft.title.isNotBlank()
            categoryEditedByUser = draft.category.isNotBlank()
        }
        if (createMode) {
            installSuggestionWatchers()
        }

        fun field(label: String, input: EditText) {
            val heading = views.text(label, UiMetrics.Caption, muted = true).apply {
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setPadding(0, dp(UiMetrics.Small), 0, dp(UiMetrics.Micro))
                id = android.view.View.generateViewId()
            }
            input.id = android.view.View.generateViewId()
            heading.labelFor = input.id
            input.layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(UiMetrics.Small)
            }
            form.addView(heading)
            form.addView(input)
        }
        field("标题", titleInput)
        field("内容", contentInput)
        accessCodeInput?.let { field("提取码", it) }
        field("分类", categoryInput)
        field("备注", noteInput)
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        actions.addView(tonalButton(if (createMode) "保存到安全箱" else "保存修改") { save() }
            .withVerticalMargins(bottomDp = UiMetrics.Small))
        actions.addView(outlinedButton("取消编辑") { leaveEditor() })
        form.addView(actions)
        scroll.setPadding(0, 0, 0, dp(UiMetrics.Content))
        scroll.clipToPadding = true
        scroll.addView(form)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        installEditorKeyboardHandling(root, scroll, actions,
            listOfNotNull(titleInput, contentInput, accessCodeInput, categoryInput, noteInput))
        formVisible = true
    }

    private fun installEditorKeyboardHandling(
        root: View,
        scroll: ScrollView,
        actions: View,
        fields: List<EditText>
    ) {
        val firstField = fields.firstOrNull()
        var imeInsetBottom = 0

        fields.forEach { field ->
            field.setOnFocusChangeListener { focusedView, hasFocus ->
                if (hasFocus) {
                    focusedView.post {
                        revealEditorFocusAndActions(scroll, focusedView, actions)
                        if (focusedView !== firstField) {
                            scrollToEditorBottom(scroll)
                            focusedView.post {
                                revealEditorFocusAndActions(scroll, focusedView, actions)
                            }
                        }
                    }
                }
            }
        }

        val left = dp(UiMetrics.Gutter)
        val top = dp(UiMetrics.Content)
        val right = dp(UiMetrics.Gutter)
        val bottom = dp(UiMetrics.Small)
        ViewCompat.setOnApplyWindowInsetsListener(root) { target, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            imeInsetBottom = ime.bottom
            target.setPadding(
                left + systemBars.left,
                top + systemBars.top,
                right + systemBars.right,
                bottom + systemBars.bottom
            )
            updateEditorInsets(root, scroll, actions, fields, imeInsetBottom)
            insets
        }
        root.viewTreeObserver.addOnGlobalLayoutListener {
            val systemBars = ViewCompat.getRootWindowInsets(root)?.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            updateEditorInsets(root, scroll, actions, fields, imeInsetBottom,
                systemBars?.bottom ?: 0)
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun updateEditorInsets(
        root: View,
        scroll: ScrollView,
        actions: View,
        fields: List<EditText>,
        imeInsetBottom: Int,
        systemBottom: Int = 0
    ) {
        val visibleFrame = Rect()
        root.getWindowVisibleDisplayFrame(visibleFrame)
        val frameImeBottom = (root.rootView.height - visibleFrame.bottom - systemBottom).coerceAtLeast(0)
        val keyboardBottom = maxOf(imeInsetBottom, frameImeBottom)
        val desiredBottomPadding = dp(UiMetrics.Content) + keyboardBottom
        if (scroll.paddingBottom == desiredBottomPadding) return

        scroll.setPadding(scroll.paddingLeft, scroll.paddingTop, scroll.paddingRight, desiredBottomPadding)
        if (keyboardBottom > 0) {
            root.post {
                scroll.findFocus()?.takeIf { it is EditText }?.let { focusedView ->
                    revealEditorFocusAndActions(scroll, focusedView, actions)
                    if (focusedView !== fields.firstOrNull()) {
                        scrollToEditorBottom(scroll)
                        focusedView.post {
                            revealEditorFocusAndActions(scroll, focusedView, actions)
                        }
                    }
                }
            }
        }
    }

    private fun revealEditorFocusAndActions(scroll: ScrollView, focused: View, actions: View) {
        if (!focused.isShown || !actions.isShown || scroll.height <= 0) return

        val focusedRect = Rect()
        val actionsRect = Rect()
        scroll.offsetDescendantRectToMyCoords(focused, focusedRect)
        scroll.offsetDescendantRectToMyCoords(actions, actionsRect)

        val safeTop = scroll.paddingTop + dp(UiMetrics.Small)
        val safeBottom = scroll.height - scroll.paddingBottom - dp(UiMetrics.Small)
        if (safeBottom <= safeTop) return

        var delta = when {
            focusedRect.top < safeTop -> focusedRect.top - safeTop
            focusedRect.bottom > safeBottom -> focusedRect.bottom - safeBottom
            else -> 0
        }

        val canKeepFocusVisible = actionsRect.bottom - focusedRect.top <= safeBottom - safeTop
        if (canKeepFocusVisible && actionsRect.bottom > safeBottom) {
            delta = maxOf(delta, actionsRect.bottom - safeBottom)
        }
        if (delta != 0) scroll.smoothScrollBy(0, delta)
    }

    private fun scrollToEditorBottom(scroll: ScrollView) {
        val content = scroll.getChildAt(0) ?: return
        val viewportHeight = scroll.height - scroll.paddingTop - scroll.paddingBottom
        val bottom = (content.height - viewportHeight).coerceAtLeast(0)
        scroll.smoothScrollTo(0, bottom)
    }

    private fun renderDetail(current: VaultItem) {
        val root = views.column().apply { FrostedUi.applyPageChrome(this) }
        root.addView(views.back { finish() })
        val content = views.column()
        content.addView(views.title(current.title.ifBlank { current.target.displayName }))
        content.addView(views.text("已保存 · ${current.category ?: "未分类"}", UiMetrics.Caption, muted = true).apply {
            setPadding(0, 0, 0, 0)
        })
        content.addView(views.section("内容"))
        content.addView(materialSurface().apply {
            addView(views.text(current.content).apply {
                setPadding(0, 0, 0, 0)
                setTextIsSelectable(true)
                isSaveEnabled = false
            })
            addView(tonalButton("开始中转") {
                startActivity(Intent(this@VaultDetailActivity, TransferActivity::class.java)
                    .putExtra(TransferActivity.EXTRA_ITEM_ID, current.id))
            }.withVerticalMargins(topDp = UiMetrics.Small))
        })
        current.accessCode?.takeIf { it.isNotBlank() }?.let {
            content.addView(views.section("提取码")); content.addView(views.text(it))
        }
        current.note?.takeIf { it.isNotBlank() }?.let {
            content.addView(views.section("备注")); content.addView(views.text(it))
        }
        content.addView(materialSurface(FrostedUi.surfaceContainerLow).apply {
            addView(tonalButton("编辑") {
                editing = true; draftModel.draft = null; render()
            }.withVerticalMargins(bottomDp = UiMetrics.Small))
            addView(outlinedButton(if (current.favorite) "取消收藏" else "收藏") {
                store.setFavorite(current.id, !current.favorite)
                item = store.getItem(itemId); render()
            })
        })
        // Existing copy extraction stays in the shared factory, reading the saved content.
        content.addView(VaultSafeCopyPanelFactory(this) { current.content }.create(current))
        content.addView(dangerOutlinedButton("删除内容") { confirmDelete() })
        content.addView(views.text("最近修改 ${Formatters.time(current.updatedAtMillis)}", muted = true))
        root.addView(ScrollView(this).apply { addView(content) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun materialSurface(
        backgroundColor: Int = FrostedUi.surfaceContainer,
        horizontalPadding: Int = UiMetrics.Content,
        verticalPadding: Int = UiMetrics.Content
    ) = views.column().apply {
        background = FrostedUi.rounded(this@VaultDetailActivity, backgroundColor, UiMetrics.Radius)
        setPadding(views.dp(horizontalPadding), views.dp(verticalPadding),
            views.dp(horizontalPadding), views.dp(verticalPadding))
    }

    private fun tonalButton(label: String, action: () -> Unit) =
        views.button(label, ButtonKind.Secondary, FrostedUi.primaryContainer, action).apply {
            setTextColor(FrostedUi.accent)
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }

    private fun outlinedButton(
        label: String,
        textColor: Int = FrostedUi.textPrimary,
        outlineColor: Int = FrostedUi.divider,
        action: () -> Unit
    ) =
        views.button(label, ButtonKind.Secondary, Color.TRANSPARENT, action).apply {
            setTextColor(textColor)
            background = StateListDrawable().apply {
                addState(intArrayOf(-android.R.attr.state_enabled), outlinedButtonBackground(
                    Color.TRANSPARENT, outlineColor))
                addState(intArrayOf(android.R.attr.state_pressed), outlinedButtonBackground(
                    FrostedUi.surfaceContainer, outlineColor))
                addState(intArrayOf(android.R.attr.state_focused), outlinedButtonBackground(
                    FrostedUi.surfaceContainer, outlineColor))
                addState(intArrayOf(), outlinedButtonBackground(Color.TRANSPARENT, outlineColor))
            }
            stateListAnimator = null
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }

    private fun dangerOutlinedButton(label: String, action: () -> Unit) =
        outlinedButton(label, FrostedUi.danger, FrostedUi.danger, action).apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                marginStart = dp(UiMetrics.Content)
                topMargin = dp(UiMetrics.Small)
                marginEnd = dp(UiMetrics.Content)
            }
        }

    private fun outlinedButtonBackground(fillColor: Int, strokeColor: Int) =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = views.dp(UiMetrics.Radius).toFloat()
            setColor(fillColor)
            setStroke(views.dp(1), strokeColor)
        }

    private fun Button.withVerticalMargins(topDp: Int = 0, bottomDp: Int = 0) = apply {
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(topDp)
            bottomMargin = dp(bottomDp)
        }
    }

    private fun leaveEditor() {
        if (!canDisplaySecureContent()) { finish(); return }
        if (!editing) { finish(); return }
        val changed = formVisible && (contentInput.text.toString() != item?.content.orEmpty() ||
            titleInput.text.toString() != item?.title.orEmpty() ||
            categoryInput.text.toString() != item?.category.orEmpty() ||
            noteInput.text.toString() != item?.note.orEmpty() ||
            accessCodeInput?.text?.toString().orEmpty() != item?.accessCode.orEmpty())
        val discard = {
            draftModel.draft = null
            if (createMode) finish() else { editing = false; refreshSecureContent() }
        }
        if (!changed) discard()
        else AlertDialog.Builder(this).setTitle("放弃未保存的修改？")
            .setMessage("安全箱中已保存的内容不会改变。")
            .setNegativeButton("继续编辑", null).setPositiveButton("放弃修改") { _, _ -> discard() }.show()
    }

    private fun save() {
        val content = contentInput.text?.toString().orEmpty().trim()
        if (content.isBlank()) {
            Toast.makeText(this, "请先填写要保存的内容", Toast.LENGTH_SHORT).show()
            return
        }
        applySuggestionIfAllowed(force = false)
        val accessCode = accessCodeInput?.text?.toString()?.trim().orEmpty()
        if (accessCode.isNotEmpty() && !accessCode.matches(Regex("[A-Za-z0-9]{4,8}"))) {
            Toast.makeText(this, "提取码需为 4 至 8 位字母或数字", Toast.LENGTH_SHORT).show()
            return
        }
        if (createMode) {
            itemId = store.saveManualItem(
                title = titleInput.text?.toString().orEmpty().trim(),
                content = content,
                category = categoryInput.text?.toString().orEmpty().trim(),
                note = noteInput.text?.toString().orEmpty().trim()
            )
            createMode = false
            editing = false
            draftModel.draft = null
            Toast.makeText(this, "已保存到安全箱", Toast.LENGTH_SHORT).show()
            item = store.getItem(itemId)
            render()
            return
        }
        store.updateItem(
            id = itemId,
            title = titleInput.text?.toString().orEmpty().trim(),
            content = content,
            accessCode = accessCode.ifBlank { null },
            category = categoryInput.text?.toString().orEmpty().trim(),
            note = noteInput.text?.toString().orEmpty().trim()
        )
        Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
        editing = false
        draftModel.draft = null
        item = store.getItem(itemId)
        render()
    }

    private fun confirmDelete() {
        val name = item?.title?.takeIf { it.isNotBlank() } ?: "这条内容"
        AlertDialog.Builder(this)
            .setTitle("删除内容")
            .setMessage("确定删除“$name”吗？此操作无法撤销。")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ -> delete() }
            .show()
    }

    private fun delete() {
        store.deleteItem(itemId)
        Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun input(
        hintText: String,
        value: String,
        singleLine: Boolean,
        minLines: Int = if (singleLine) 1 else 3,
        maxLines: Int = if (singleLine) 1 else 8
    ): EditText {
        return EditText(this).apply {
            hint = hintText
            setText(value)
            FrostedUi.styleInput(this, FrostedUi.surfaceContainerLow)
            setSingleLine(singleLine)
            if (!singleLine) {
                this.minLines = minLines
                this.maxLines = maxLines
            }
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), editorInputBackground(
                    FrostedUi.accent, 2))
                addState(intArrayOf(), editorInputBackground(FrostedUi.divider, 1))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                textCursorDrawable = android.graphics.drawable.ColorDrawable(FrostedUi.accent)
            }
        }
    }

    private fun editorInputBackground(strokeColor: Int, strokeWidthDp: Int) =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = views.dp(UiMetrics.Radius).toFloat()
            setColor(FrostedUi.surfaceContainerLow)
            setStroke(views.dp(strokeWidthDp), strokeColor)
        }

    private fun installSuggestionWatchers() {
        titleInput.addTextChangedListener(afterTextChanged {
            if (!autoFillingSuggestion) {
                titleEditedByUser = titleInput.text?.isNotBlank() == true
            }
        })
        categoryInput.addTextChangedListener(afterTextChanged {
            if (!autoFillingSuggestion) {
                categoryEditedByUser = categoryInput.text?.isNotBlank() == true
            }
        })
        contentInput.addTextChangedListener(afterTextChanged {
            applySuggestionIfAllowed(force = false)
        })
    }

    private fun applySuggestionIfAllowed(force: Boolean) {
        if (!createMode) return
        val content = contentInput.text?.toString().orEmpty().trim()
        if (content.isBlank()) return
        val url = LinkParser.extractFirstUrl(content)
        val target = LinkTargetDetector.detect(content)
        val suggestion = VaultContentSuggester.suggest(content, url, target)
        val category = settingsStore.resolveSuggestedCategory(suggestion.category)

        autoFillingSuggestion = true
        if (force || !titleEditedByUser || titleInput.text.isNullOrBlank()) {
            titleInput.setText(suggestion.title)
        }
        if (force || !categoryEditedByUser || categoryInput.text.isNullOrBlank()) {
            categoryInput.setText(category)
        }
        autoFillingSuggestion = false
    }


    private fun afterTextChanged(action: () -> Unit): TextWatcher {
        return object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = action()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_ITEM_ID = "item_id"
        const val EXTRA_CREATE_MODE = "create_mode"
    }

}
