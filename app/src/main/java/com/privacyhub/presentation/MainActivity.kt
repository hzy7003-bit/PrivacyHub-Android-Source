package com.privacyhub.presentation

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import com.privacyhub.clipboard.ClipboardReader
import com.privacyhub.data.repository.LocalStore
import com.privacyhub.data.settings.AppSettingsStore
import com.privacyhub.domain.model.SavedItem
import com.privacyhub.presentation.category.CategoryDetailActivity
import com.privacyhub.presentation.category.MoreCategoriesActivity
import com.privacyhub.presentation.guide.GuideContent
import com.privacyhub.presentation.guide.WelcomeDialogFragment
import com.privacyhub.presentation.search.SearchResultsActivity
import com.privacyhub.presentation.security.SecureActivity
import com.privacyhub.presentation.share.ShareReceiverActivity
import com.privacyhub.presentation.transfer.AddContentDraft
import com.privacyhub.presentation.ui.ButtonKind
import com.privacyhub.presentation.ui.FrostedUi
import com.privacyhub.presentation.ui.PorcelainViews
import com.privacyhub.presentation.ui.UiMetrics
import com.privacyhub.presentation.vault.VaultDetailActivity
import com.privacyhub.util.AppExecutors

class MainActivity : SecureActivity() {
    private val settingsStore by lazy { AppSettingsStore(this) }
    private val views by lazy { PorcelainViews(this) }
    private var selected = TRANSFER
    private var generation = 0
    private var vaultScrollY = 0
    private var scroll: ScrollView? = null
    private var trialNoticeChecked = false
    private var searchText = ""
    private var inputDialog: AlertDialog? = null
    private var inputField: EditText? = null
    private val inputDraft by lazy { ViewModelProvider(this)[AddContentDraft::class.java] }
    private var addSheet: Dialog? = null
    private var sheetOpen = false
    private lateinit var tabBack: OnBackPressedCallback

    override fun onSecureCreate(savedInstanceState: Bundle?) {
        selected = savedInstanceState?.getString(EXTRA_TAB) ?: intent.getStringExtra(EXTRA_TAB) ?: TRANSFER
        if (selected != VAULT) selected = TRANSFER
        vaultScrollY = savedInstanceState?.getInt("vault_scroll") ?: 0
        sheetOpen = savedInstanceState?.getBoolean("add_sheet_open") ?: false
        tabBack = object : OnBackPressedCallback(selected == VAULT) {
            override fun handleOnBackPressed() { selectTab(TRANSFER) }
        }
        onBackPressedDispatcher.addCallback(this, tabBack)
    }

    override fun shouldRequireAuthentication() = selected == VAULT && isSecurityLockEnabled()

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        selected = if (intent.getStringExtra(EXTRA_TAB) == VAULT) VAULT else TRANSFER
        tabBack.isEnabled = selected == VAULT
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(EXTRA_TAB, selected)
        outState.putInt("vault_scroll", scroll?.scrollY ?: vaultScrollY)
        outState.putBoolean("add_sheet_open", sheetOpen)
        super.onSaveInstanceState(outState)
    }

    override fun onPause() {
        if (selected == VAULT) vaultScrollY = scroll?.scrollY ?: vaultScrollY
        generation++
        super.onPause()
    }

    override fun onUnlocked() {
        tabBack.isEnabled = selected == VAULT
        if (selected == VAULT) renderVault() else renderTransfer()
        showTrialNoticeIfNeeded()
        if (inputDraft.open && inputDialog == null) showInput()
        else if (sheetOpen && addSheet == null) showAddSheet()
    }

    override fun onDestroy() {
        inputDialog?.setOnDismissListener(null)
        inputDialog?.dismiss()
        addSheet?.setOnDismissListener(null)
        addSheet?.dismiss()
        super.onDestroy()
    }

    private fun selectTab(tab: String) {
        if (tab == "设置") {
            startActivity(Intent(this, SettingsActivity::class.java))
            overridePendingTransition(0, 0)
            return
        }
        if (selected == VAULT) vaultScrollY = scroll?.scrollY ?: vaultScrollY
        selected = tab
        generation++
        tabBack.isEnabled = selected == VAULT
        refreshSecureContent()
    }

    private fun root() = views.column().apply { FrostedUi.applyPageChrome(this) }

    private fun renderTransfer() {
        scroll = null
        val root = root()
        root.addView(views.title("隐私中转站"))
        val content = views.column().apply { gravity = Gravity.CENTER }
        content.addView(addActionFab())
        content.addView(views.section("添加一段内容").apply { gravity = Gravity.CENTER })
        content.addView(views.text("粘贴 · 输入 · 从其他 App 分享", muted = true).apply { gravity = Gravity.CENTER })
        content.addView(views.text("先保存，再安心处理", muted = true).apply { gravity = Gravity.CENTER })
        root.addView(ScrollView(this).apply { isFillViewport = true; addView(content) },
            LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(views.text("通知栏保存入口可在设置中开启。", UiMetrics.Caption, muted = true))
        root.addView(views.navigation(TRANSFER, ::selectTab))
        setContentView(root)
    }

    private fun addActionFab() = android.widget.Button(this).apply {
        text = "＋"
        isAllCaps = false
        textSize = UiMetrics.AddGlyph
        setTextColor(FrostedUi.accent)
        typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
        contentDescription = "添加一段内容"
        gravity = Gravity.CENTER
        setPadding(0, 0, 0, 0)
        minWidth = views.dp(UiMetrics.AddTarget)
        minimumWidth = views.dp(UiMetrics.AddTarget)
        minHeight = views.dp(UiMetrics.AddTarget)
        minimumHeight = views.dp(UiMetrics.AddTarget)
        backgroundTintList = null
        background = StateListDrawable().apply {
            val pressed = ColorUtils.blendARGB(FrostedUi.primaryContainer, FrostedUi.accent, 0.12f)
            addState(intArrayOf(android.R.attr.state_pressed), fabBackground(pressed))
            addState(intArrayOf(android.R.attr.state_focused), fabBackground(pressed))
            addState(intArrayOf(), fabBackground(FrostedUi.primaryContainer))
        }
        elevation = 0f
        layoutParams = LinearLayout.LayoutParams(views.dp(UiMetrics.AddTarget), views.dp(UiMetrics.AddTarget))
        setOnClickListener { showAddSheet() }
    }

    private fun fabBackground(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = views.dp(UiMetrics.AddRadius).toFloat()
    }

    private fun renderVault() {
        val token = ++generation
        val root = root()
        root.addView(views.title("安全箱"))
        val content = views.column()
        val query = EditText(this).apply {
            hint = "搜索安全箱"
            contentDescription = "搜索安全箱"
            FrostedUi.styleInput(this, FrostedUi.surfaceContainer)
            setText(searchText)
            doAfterTextChanged { searchText = it?.toString().orEmpty() }
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener { _, action, _ ->
                if (action == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) { search(text.toString()); true } else false
            }
        }
        content.addView(query)
        val data = views.column()
        data.addView(views.text("正在读取安全箱…", muted = true))
        content.addView(data)
        val createContentButton = views.button("新建安全箱内容", backgroundColor = FrostedUi.surfaceContainerLow) {
            startActivity(Intent(this, VaultDetailActivity::class.java).putExtra(VaultDetailActivity.EXTRA_CREATE_MODE, true))
        }.apply { visibility = android.view.View.GONE }
        content.addView(createContentButton)
        val lockButton = if (isSecurityLockEnabled()) {
            views.button("立即锁定", ButtonKind.Plain) { lockNowAndAuthenticate() }
                .apply { visibility = android.view.View.GONE }
        } else null
        lockButton?.let { content.addView(it) }
        scroll = ScrollView(this).apply {
            setPadding(0, 0, 0, views.dp(UiMetrics.Section))
            ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
                val safe = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                )
                view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight,
                    views.dp(UiMetrics.Section) + safe.bottom)
                insets
            }
            addView(content)
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(views.navigation(VAULT, FrostedUi.primaryContainer, ::selectTab))
        setContentView(root)
        scroll?.let { ViewCompat.requestApplyInsets(it) }
        val app = applicationContext
        AppExecutors.executeDisk {
            val result = runCatching {
                val store = LocalStore(app)
                Overview(store.favoriteItems(3), store.recentItems(3), store.homeTags())
            }
            AppExecutors.executeMain {
                if (token != generation || selected != VAULT || isFinishing || isDestroyed) return@executeMain
                data.removeAllViews()
                result.fold({ overview ->
                    addFavoritesGroup(data, overview.favorites)
                    addRecentGroup(data, overview.recent)
                    data.addView(vaultSectionTitle("常用分类"))
                    val categoriesSurface = moduleSurface()
                    overview.categories.forEachIndexed { index, category ->
                        if (index > 0) {
                            categoriesSurface.addView(LinearLayout(this).apply {
                                setBackgroundColor(FrostedUi.divider)
                                layoutParams = LinearLayout.LayoutParams(-1, views.dp(1)).apply {
                                    marginStart = views.dp(UiMetrics.Content)
                                    marginEnd = views.dp(UiMetrics.Content)
                                }
                            })
                        }
                        categoriesSurface.addView(moduleActionRow(category) {
                            openCategory(CategoryDetailActivity.MODE_CATEGORY, category)
                        })
                    }
                    categoriesSurface.addView(LinearLayout(this).apply {
                        setBackgroundColor(FrostedUi.divider)
                        layoutParams = LinearLayout.LayoutParams(-1, views.dp(1)).apply {
                            marginStart = views.dp(UiMetrics.Content)
                            marginEnd = views.dp(UiMetrics.Content)
                        }
                    })
                    categoriesSurface.addView(moduleActionRow("全部分类与管理") {
                        startActivity(Intent(this, MoreCategoriesActivity::class.java))
                    })
                    data.addView(categoriesSurface)
                    scroll?.post { scroll?.scrollTo(0, vaultScrollY) }
                }, {
                    data.addView(views.text("暂时无法读取安全箱。原有数据不会被清除。", muted = true))
                    data.addView(views.button("重试") { renderVault() })
                })
                createContentButton.visibility = android.view.View.VISIBLE
                lockButton?.visibility = android.view.View.VISIBLE
            }
        }
    }

    private fun moduleSurface() = views.column().apply {
        background = FrostedUi.rounded(this@MainActivity, FrostedUi.surfaceContainerLow, UiMetrics.Radius)
        setPadding(views.dp(UiMetrics.Content), 0, views.dp(UiMetrics.Content), 0)
    }

    private fun vaultSectionTitle(label: String) = views.text(label, UiMetrics.Title).apply {
        typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        setPadding(0, views.dp(UiMetrics.Section), 0, views.dp(UiMetrics.Small))
        androidx.core.view.ViewCompat.setAccessibilityHeading(this, true)
    }

    private fun emptyInfoBlock(primary: String, secondary: String? = null) = views.column().apply {
        setPadding(0, views.dp(UiMetrics.Content), 0, views.dp(UiMetrics.Content))
        addView(views.text(primary).apply {
            gravity = Gravity.START
            setPadding(0, 0, 0, 0)
        })
        if (secondary != null) {
            addView(views.text(secondary, UiMetrics.Caption, muted = true).apply {
                gravity = Gravity.START
                setPadding(0, 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = views.dp(UiMetrics.Small)
                }
            })
        }
    }

    private fun moduleActionRow(label: String, action: () -> Unit) = views.row(label, action = action).apply {
        textSize = UiMetrics.Body
        typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        layoutParams = LinearLayout.LayoutParams(-1, views.dp(UiMetrics.Row)).apply {
            bottomMargin = 0
        }
        setPadding(0, 0, 0, 0)
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
    }

    private fun addFavoritesGroup(container: LinearLayout, items: List<SavedItem>) {
        container.addView(vaultSectionTitle("收藏"))
        val surface = moduleSurface()
        if (items.isEmpty()) {
            surface.addView(emptyInfoBlock("还没有收藏", "进入条目后可以收藏。"))
        } else {
            items.forEach { item ->
                surface.addView(views.row(item.title.ifBlank { item.target.displayName }, item.category) {
                    startActivity(Intent(this, VaultDetailActivity::class.java)
                        .putExtra(VaultDetailActivity.EXTRA_ITEM_ID, item.id))
                })
            }
        }
        surface.addView(LinearLayout(this).apply {
            setBackgroundColor(FrostedUi.divider)
            layoutParams = LinearLayout.LayoutParams(-1, views.dp(1))
        })
        surface.addView(moduleActionRow("查看全部收藏") { openCategory(CategoryDetailActivity.MODE_FAVORITES) })
        container.addView(surface)
    }

    private fun addRecentGroup(container: LinearLayout, items: List<SavedItem>) {
        container.addView(vaultSectionTitle("最近保存"))
        val surface = moduleSurface()
        if (items.isEmpty()) {
            surface.addView(emptyInfoBlock("还没有保存内容。"))
        } else {
            items.forEach { item ->
                surface.addView(views.row(item.title.ifBlank { item.target.displayName }, item.category) {
                    startActivity(Intent(this, VaultDetailActivity::class.java)
                        .putExtra(VaultDetailActivity.EXTRA_ITEM_ID, item.id))
                })
            }
        }
        surface.addView(LinearLayout(this).apply {
            setBackgroundColor(FrostedUi.divider)
            layoutParams = LinearLayout.LayoutParams(-1, views.dp(1))
        })
        surface.addView(moduleActionRow("查看全部最近保存") {
            openCategory(CategoryDetailActivity.MODE_RECENT)
        })
        container.addView(surface)
    }

    private fun openCategory(mode: String, category: String? = null) {
        startActivity(Intent(this, CategoryDetailActivity::class.java)
            .putExtra(CategoryDetailActivity.EXTRA_MODE, mode).putExtra(CategoryDetailActivity.EXTRA_CATEGORY, category))
    }

    private fun search(query: String) {
        searchText = query.trim()
        if (searchText.isBlank()) Toast.makeText(this, "请输入搜索内容", Toast.LENGTH_SHORT).show()
        else startActivity(Intent(this, SearchResultsActivity::class.java).putExtra(SearchResultsActivity.EXTRA_QUERY, searchText))
    }

    private fun showAddSheet() {
        val sheet = Dialog(this)
        addSheet = sheet
        sheetOpen = true
        sheet.setOnDismissListener { addSheet = null; sheetOpen = false }
        val content = views.column().apply {
            background = FrostedUi.rounded(this@MainActivity, FrostedUi.surfaceContainer, UiMetrics.SheetRadius)
            val horizontal = views.dp(UiMetrics.Gutter)
            val top = views.dp(UiMetrics.Content)
            val bottom = views.dp(UiMetrics.Content)
            setPadding(horizontal, top, horizontal, bottom)
            ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
                val safe = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                )
                view.setPadding(horizontal, top, horizontal, bottom + safe.bottom)
                insets
            }
            addView(views.text("添加内容", UiMetrics.Title).apply {
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                setPadding(0, 0, 0, views.dp(UiMetrics.Small))
                ViewCompat.setAccessibilityHeading(this, true)
            })
            addView(views.row("从剪贴板粘贴") {
                sheet.dismiss()
                val result = ClipboardReader.readText(this@MainActivity)
                if (result.text.isNullOrBlank()) Toast.makeText(this@MainActivity, "剪贴板中没有可读取的文本", Toast.LENGTH_SHORT).show()
                else receive(result.text)
            }.apply { layoutParams = LinearLayout.LayoutParams(-1, views.dp(UiMetrics.Row)).apply { bottomMargin = 0 } })
            addView(views.row("手动输入") { sheet.dismiss(); showInput() }.apply {
                layoutParams = LinearLayout.LayoutParams(-1, views.dp(UiMetrics.Row)).apply { bottomMargin = 0 }
            })
            addView(views.button("取消", ButtonKind.Plain) { sheet.dismiss() }.apply {
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = views.dp(UiMetrics.Small)
                    bottomMargin = 0
                }
            })
        }
        sheet.setContentView(content)
        sheet.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.BOTTOM)
            setLayout(-1, -2)
        }
        sheet.show()
        sheet.window?.setLayout(-1, -2)
    }

    private fun showInput() {
        val field = EditText(this).apply {
            hint = "输入链接、口令或一段文字"
            contentDescription = "要保存并处理的内容"
            FrostedUi.styleInput(this, FrostedUi.surfaceContainerLow)
            setSingleLine(false)
            minLines = 4
            maxLines = 6
            isVerticalScrollBarEnabled = true
            isScrollbarFadingEnabled = true
            setHorizontallyScrolling(false)
            gravity = Gravity.TOP
            setText(inputDraft.text)
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused),
                    GradientDrawable().apply {
                        setColor(FrostedUi.surfaceContainerLow)
                        cornerRadius = views.dp(UiMetrics.Radius).toFloat()
                        setStroke(views.dp(2), FrostedUi.accent)
                    })
                addState(intArrayOf(),
                    FrostedUi.rounded(this@MainActivity, FrostedUi.surfaceContainerLow, UiMetrics.Radius))
            }
            doAfterTextChanged { inputDraft.text = it?.toString().orEmpty() }
        }
        val inputContainer = views.column().apply {
            setPadding(views.dp(UiMetrics.Content), views.dp(UiMetrics.Small),
                views.dp(UiMetrics.Content), views.dp(UiMetrics.Small))
            addView(field)
        }
        val dialog = AlertDialog.Builder(this).setTitle("手动输入")
            .setView(inputContainer)
            .setNegativeButton("取消", null).setPositiveButton("保存并处理", null).create()
        inputField = field
        inputDialog = dialog
        inputDraft.open = true
        dialog.setOnShowListener {
            dialog.findViewById<TextView>(resources.getIdentifier("alertTitle", "id", "android"))?.apply {
                setTextColor(FrostedUi.textPrimary)
                textSize = UiMetrics.Title
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).apply {
                FrostedUi.styleButton(this, ButtonKind.Plain)
                setTextColor(FrostedUi.textSecondary)
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).apply {
                FrostedUi.styleTonalButton(this)
                setTextColor(FrostedUi.accent)
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (field.text.isNullOrBlank()) field.error = "请输入内容"
                else { receive(field.text.toString()); dialog.dismiss() }
            }
        }
        dialog.setOnDismissListener {
            inputField = null
            inputDialog = null
            if (!isChangingConfigurations) inputDraft.clear()
        }
        dialog.show()
        dialog.setCanceledOnTouchOutside(false)
        dialog.window?.apply {
            setBackgroundDrawable(FrostedUi.rounded(this@MainActivity, FrostedUi.surfaceContainer, UiMetrics.SheetRadius))
            decorView.elevation = 0f
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }
        field.requestFocus()
    }

    private fun receive(text: String) {
        startActivity(Intent(this, ShareReceiverActivity::class.java)
            .setAction(ShareReceiverActivity.ACTION_MANUAL).putExtra(Intent.EXTRA_TEXT, text))
    }

    private fun showTrialNoticeIfNeeded() {
        if (trialNoticeChecked) return
        trialNoticeChecked = true
        val versionName = appVersionName()
        if (!GuideContent.shouldShowWelcome(versionName, settingsStore.lastSeenTrialNoticeVersion())) return
        if (supportFragmentManager.findFragmentByTag(WelcomeDialogFragment.TAG) != null) return
        if (!supportFragmentManager.isStateSaved) {
            WelcomeDialogFragment.newInstance(GuideContent.noticeKey(versionName))
                .show(supportFragmentManager, WelcomeDialogFragment.TAG)
        }
    }

    private fun appVersionName(): String {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
        return packageInfo.versionName ?: "unknown"
    }


    private data class Overview(val favorites: List<SavedItem>, val recent: List<SavedItem>, val categories: List<String>)

    companion object {
        const val EXTRA_TAB = "destination"
        const val TRANSFER = "中转"
        const val VAULT = "安全箱"
    }
}
