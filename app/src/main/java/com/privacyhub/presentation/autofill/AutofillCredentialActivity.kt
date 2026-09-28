package com.privacyhub.presentation.autofill

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.privacyhub.BuildConfig
import com.privacyhub.autofill.AutofillScopePolicy
import com.privacyhub.data.db.entity.AutofillCredentialEntity
import com.privacyhub.data.repository.AutofillCredentialRepository
import com.privacyhub.data.settings.AppSettingsStore
import com.privacyhub.presentation.security.SecureActivity
import com.privacyhub.presentation.ui.ButtonKind
import com.privacyhub.presentation.ui.FrostedUi
import com.privacyhub.pro.ProFeature
import com.privacyhub.pro.ProGate
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AutofillCredentialActivity : SecureActivity() {
    private val repository by lazy { AutofillCredentialRepository(this) }
    private val settingsStore by lazy { AppSettingsStore(this) }
    private val proGate by lazy { ProGate(this) }
    private val databaseExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var credentials: List<AutofillCredentialEntity> = emptyList()

    override fun onSecureCreate(savedInstanceState: Bundle?) {
        super.onSecureCreate(savedInstanceState)
        title = "系统自动填充"
    }

    override fun onUnlocked() = loadCredentials()

    override fun onDestroy() {
        databaseExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun loadCredentials() {
        databaseExecutor.execute {
            val loaded = repository.all()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                credentials = loaded
                render()
            }
        }
    }

    private fun render() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; FrostedUi.applyPageChrome(this) }
        root.addView(titleText("系统自动填充 Beta"))
        root.addView(bodyText("只在身份明确的原生 App 标准账号或密码输入框中提供候选，不使用无障碍，不自动登录或提交。当前网站回填已关闭：无网络权限下，Android App Links 状态不能证明 DAL get_login_creds 授权或当前 WebView 页面来源。已有网站凭据会保留，但不会返回给网页。"))
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(14), 0, 0) }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val autofillEnabled = settingsStore.isAutofillEnabled()
        content.addView(
            card(
                if (autofillEnabled) "系统自动填充已允许" else "系统自动填充已关闭",
                if (autofillEnabled) {
                    "点进支持的登录输入框时会提供候选。开启安全锁后，每次回填还需验证指纹或系统密码。"
                } else {
                    "关闭时不返回候选，也不会读取任何第三方输入框内容。已保存的登录信息不会被删除。"
                },
                if (autofillEnabled) "关闭系统自动填充" else "允许系统自动填充",
                if (autofillEnabled) ButtonKind.Danger else ButtonKind.Primary
            ) { toggleAutofill() }
        )
        content.addView(card("启用系统服务", "需要在 Android 系统设置中明确选择隐私中转站。App 不会替你修改默认自动填充服务。", "打开系统设置", ButtonKind.Secondary) {
            runCatching {
                startActivity(Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                    data = Uri.parse("package:$packageName")
                })
            }.onFailure { Toast.makeText(this, "当前系统不支持直接打开自动填充设置", Toast.LENGTH_SHORT).show() }
        })
        content.addView(card("添加登录信息", "新增凭据仅绑定本机原生 App 包名和签名证书。网站回填暂不支持；不会自动读取或保存第三方 App 的账号、密码。", "新增登录信息", ButtonKind.Secondary) { showCredentialDialog() })
        if (BuildConfig.DEBUG) {
            content.addView(card("自动填充调试页", "仅 debug 测试包提供。页面无网络、无提交，用于验证候选和回填。", "打开调试页", ButtonKind.Secondary) {
                startActivity(Intent().setClassName(packageName, "$packageName.presentation.autofill.AutofillTestActivity"))
            })
        }
        credentials.forEach { credential -> content.addView(credentialCard(credential)) }
        if (credentials.isEmpty()) content.addView(bodyText("还没有登录信息。新增时请先选择适用的原生 App 或网站。"))
        root.addView(button("返回设置", ButtonKind.Secondary) { finish() })
        setContentView(root)
    }

    private fun toggleAutofill() {
        val enabled = settingsStore.isAutofillEnabled()
        if (!enabled) {
            val gate = proGate.check(ProFeature.Autofill)
            if (!gate.allowed) {
                Toast.makeText(this, gate.message, Toast.LENGTH_LONG).show()
                return
            }
        }
        settingsStore.setAutofillEnabled(!enabled)
        render()
    }

    private fun showCredentialDialog(existing: AutofillCredentialEntity? = null) {
        if (existing == null) {
            val gate = proGate.check(ProFeature.Autofill)
            if (!gate.allowed) {
                Toast.makeText(this, gate.message, Toast.LENGTH_LONG).show()
                return
            }
        }
        val label = input("名称，例如：淘宝主账号", false).apply { setText(existing?.label.orEmpty()) }
        val username = input("账号", false).apply { setText(existing?.username.orEmpty()) }
        val password = input("密码", true).apply { setText(existing?.password.orEmpty()) }
        var websiteScope = existing?.webDomain != null
        val scopeChooser = Button(this).apply { FrostedUi.styleOutlinedButton(this) }
        val selectedApp = Button(this).apply { FrostedUi.styleOutlinedButton(this) }
        val manualPackage = input("高级：手动输入应用包名", false).apply { visibility = View.GONE }
        val websiteDomain = input("适用网站域名，例如：accounts.example.com", false).apply {
            setText(existing?.webDomain.orEmpty())
            visibility = View.GONE
        }
        var selectedPackage = existing?.packageName
        val toggleManual = Button(this).apply {
            text = "高级：手动输入包名"
            FrostedUi.styleOutlinedButton(this)
            setOnClickListener {
                manualPackage.visibility = if (manualPackage.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        }
        fun refreshScope() {
            scopeChooser.text = if (websiteScope) "适用范围：网站" else "适用范围：本机 App"
            selectedApp.visibility = if (websiteScope) View.GONE else View.VISIBLE
            toggleManual.visibility = if (websiteScope) View.GONE else View.VISIBLE
            manualPackage.visibility = if (websiteScope) View.GONE else manualPackage.visibility
            websiteDomain.visibility = if (websiteScope) View.VISIBLE else View.GONE
        }
        fun refreshSelectedApp() {
            selectedApp.text = selectedPackage?.let { "适用应用：${appLabel(it)}" } ?: "选择适用应用"
        }
        scopeChooser.setOnClickListener {
            val options = if (existing?.webDomain != null) arrayOf("本机 App", "网站") else arrayOf("本机 App")
            AlertDialog.Builder(this)
                .setTitle("选择适用范围")
                .setItems(options) { _, which ->
                    websiteScope = which == 1
                    refreshScope()
                    refreshSelectedApp()
                }
                .show()
        }
        refreshSelectedApp()
        selectedApp.setOnClickListener {
            showAppPicker { app ->
                selectedPackage = app.packageName
                manualPackage.setText("")
                refreshSelectedApp()
            }
        }
        refreshScope()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            listOf(label, username, password, scopeChooser, selectedApp, toggleManual, manualPackage, websiteDomain).forEach { view ->
                addView(view, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(8) })
            }
        }
        val title = if (existing == null) "新增登录信息" else "编辑登录信息"
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage("候选不会显示账号或密码。原生 App 按包名和签名证书匹配。网站回填暂不可用；历史网站凭据会保留但不会被填入网页。")
            .setView(box)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val valueWebDomain = if (websiteScope) {
                    AutofillScopePolicy.normalizeDomain(websiteDomain.text.toString())
                } else {
                    null
                }
                val packageName = if (websiteScope) null else {
                    manualPackage.text.toString().trim().ifBlank { selectedPackage }
                }
                if (label.text.isNullOrBlank() || username.text.isNullOrBlank() || password.text.isNullOrBlank()) {
                    Toast.makeText(this, "名称、账号和密码不能为空", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (websiteScope && valueWebDomain == null) {
                    Toast.makeText(this, "请输入有效的网站域名", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                if (!websiteScope && packageName.isNullOrBlank()) {
                    Toast.makeText(this, "请先选择一个已安装的适用应用", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val valueLabel = label.text.toString()
                val valueUsername = username.text.toString()
                val valuePassword = password.text.toString()
                password.text?.clear()
                dialog.dismiss()
                databaseExecutor.execute {
                    val result = runCatching {
                        if (existing == null) {
                            repository.save(valueLabel, valueUsername, valuePassword, packageName, valueWebDomain)
                        } else {
                            repository.update(existing, valueLabel, valueUsername, valuePassword, packageName, valueWebDomain)
                        }
                    }
                    runOnUiThread {
                        if (result.isSuccess) {
                            loadCredentials()
                        } else {
                            Toast.makeText(
                                this,
                                if (websiteScope) "网站域名无效或不支持该范围" else "应用未安装或无法读取签名身份，请重新选择应用",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        }
        dialog.show()
    }

    private fun showAppPicker(onSelected: (LaunchableApp) -> Unit) {
        databaseExecutor.execute {
            val apps = loadLaunchableApps()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                val search = input("搜索本机应用", false)
                val list = ListView(this)
                val shownApps = apps.toMutableList()
                val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, shownApps.map(LaunchableApp::displayText).toMutableList())
                list.adapter = adapter
                fun update(query: String) {
                    shownApps.clear()
                    shownApps += apps.filter { app ->
                        app.label.contains(query, ignoreCase = true) || app.packageName.contains(query, ignoreCase = true)
                    }
                    adapter.clear()
                    adapter.addAll(shownApps.map(LaunchableApp::displayText))
                    adapter.notifyDataSetChanged()
                }
                search.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = update((s ?: "").toString())
                    override fun afterTextChanged(s: Editable?) = Unit
                })
                val content = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(6), 0, dp(6), 0)
                    addView(search, LinearLayout.LayoutParams(-1, dp(52)))
                    addView(list, LinearLayout.LayoutParams(-1, dp(340)))
                }
                val dialog = AlertDialog.Builder(this)
                    .setTitle("选择适用应用")
                    .setMessage("只显示系统桌面可发现的本机应用，不申请全量应用列表权限。")
                    .setView(content)
                    .setNegativeButton("取消", null)
                    .create()
                list.setOnItemClickListener { _, _, position, _ ->
                    onSelected(shownApps[position])
                    dialog.dismiss()
                }
                dialog.show()
            }
        }
    }

    private fun loadLaunchableApps(): List<LaunchableApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .map { info ->
                LaunchableApp(
                    label = info.loadLabel(packageManager).toString(),
                    packageName = info.activityInfo.packageName
                )
            }
            .distinctBy(LaunchableApp::packageName)
            .sortedBy { it.label.lowercase() }
    }

    private fun appLabel(packageName: String): String {
        return runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        }.getOrElse { packageName }
    }

    private fun credentialCard(credential: AutofillCredentialEntity): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        FrostedUi.applyPanel(this, 24)
        addView(titleText(credential.label, 16f))
        val scopeText = credential.webDomain?.let {
            if (AutofillScopePolicy.hasRegistrableDomain(it)) {
                "\n仅适用网站：$it（网站 Autofill 暂停；不向网页返回凭据，记录已保留）"
            } else {
                "\n网站范围：$it（公共后缀或无效范围，已停用；请编辑后重新设置）"
            }
        }
            ?: credential.packageName?.let {
                if (credential.packageSigningCertificateSha256.isNullOrBlank()) {
                    "\n仅适用原生 App：${appLabel(it)}\n旧凭据未绑定签名身份；不会回填，请编辑并重新绑定。"
                } else {
                    "\n仅适用原生 App：${appLabel(it)}（签名身份已绑定）"
                }
            }
            ?: "\n未绑定适用范围；不会回填，请编辑并指定 App 或网站。"
        addView(bodyText(credential.username + scopeText))
        addView(button("编辑登录信息", ButtonKind.Secondary) { showCredentialDialog(credential) })
        addView(button("删除登录信息", ButtonKind.Danger) { confirmDelete(credential) })
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
    }

    private fun confirmDelete(credential: AutofillCredentialEntity) {
        AlertDialog.Builder(this).setTitle("删除登录信息").setMessage("将删除“${credential.label}”。")
            .setNegativeButton("取消", null).setPositiveButton("删除") { _, _ ->
                databaseExecutor.execute {
                    repository.delete(credential.id)
                    loadCredentials()
                }
            }.show()
    }

    private fun card(title: String, body: String, action: String, kind: ButtonKind = ButtonKind.Primary, onClick: () -> Unit): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14)); FrostedUi.applyPanel(this, 24)
        addView(titleText(title, 16f)); addView(bodyText(body)); addView(button(action, kind, onClick))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
    }

    private fun input(hint: String, password: Boolean) = EditText(this).apply {
        this.hint = hint
        if (password) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        FrostedUi.styleInput(this)
    }

    private fun button(text: String, kind: ButtonKind, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        when (kind) {
            ButtonKind.Primary -> FrostedUi.styleTonalButton(this)
            ButtonKind.Danger -> FrostedUi.styleDangerOutlinedButton(this)
            ButtonKind.Secondary, ButtonKind.Plain -> FrostedUi.styleOutlinedButton(this)
        }
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(12) }
    }

    private fun titleText(text: String, size: Float = 28f) = TextView(this).apply {
        this.text = text; textSize = size; typeface = android.graphics.Typeface.DEFAULT_BOLD; setTextColor(FrostedUi.textPrimary)
    }

    private fun bodyText(text: String) = TextView(this).apply {
        this.text = text; textSize = 14f; setTextColor(FrostedUi.textSecondary); setPadding(0, dp(8), 0, 0)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private data class LaunchableApp(val label: String, val packageName: String) {
        val displayText: String get() = "$label\n$packageName"
    }
}
