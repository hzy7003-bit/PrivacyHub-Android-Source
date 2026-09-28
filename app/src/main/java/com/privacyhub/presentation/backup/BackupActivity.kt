package com.privacyhub.presentation.backup

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.privacyhub.backup.BackupImportAttempt
import com.privacyhub.backup.BackupImportReadResult
import com.privacyhub.backup.BackupImportReader
import com.privacyhub.backup.BackupImportSession
import com.privacyhub.backup.BackupFormatException
import com.privacyhub.backup.BackupPayload
import com.privacyhub.backup.BackupPayloadCodec
import com.privacyhub.backup.BackupPasswordValidator
import com.privacyhub.backup.EncryptedBackupCodec
import com.privacyhub.backup.RestoreMode
import com.privacyhub.backup.VaultBackupRepository
import com.privacyhub.presentation.security.SecureActivity
import com.privacyhub.presentation.ui.ButtonKind
import com.privacyhub.presentation.ui.FrostedUi
import com.privacyhub.presentation.pro.ProLicenseActivity
import com.privacyhub.pro.ProFeature
import com.privacyhub.pro.ProGate
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupActivity : SecureActivity() {
    private val backupRepository by lazy { VaultBackupRepository(this) }
    private val proGate by lazy { ProGate(this) }
    private val backupImportSession = BackupImportSession()
    private var importPasswordDialog: AlertDialog? = null
    private var importPasswordInput: EditText? = null
    private var pendingExportPassword: CharArray? = null
    private var pendingRestorePayload: BackupPayload? = null

    private val createBackupFile = registerForActivityResult(
        ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE)
    ) { uri ->
        val password = pendingExportPassword
        pendingExportPassword = null
        if (uri == null || password == null) {
            password?.fill('\u0000')
            return@registerForActivityResult
        }
        exportBackup(uri.toString(), password)
    }

    private val openBackupFile = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            backupImportSession.select(uri.toString())
            showImportPasswordDialog()
        } else {
            backupImportSession.cancel()
        }
    }

    override fun onSecureCreate(savedInstanceState: Bundle?) {
        super.onSecureCreate(savedInstanceState)
        title = "加密离线备份"
    }

    override fun onUnlocked() {
        render()
    }

    override fun onDestroy() {
        backupImportSession.cancel()
        importPasswordInput?.text?.clear()
        importPasswordInput = null
        importPasswordDialog = null
        pendingExportPassword?.fill('\u0000')
        pendingExportPassword = null
        pendingRestorePayload = null
        super.onDestroy()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            FrostedUi.applyPageChrome(this)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        content.addView(titleText("加密离线备份"))
        content.addView(bodyText("备份文件只保存在你选择的位置。导出需要单独设置备份口令；该口令不会保存，也无法找回。"))
        content.addView(
            infoPanel(
                "本机备份与恢复",
                "适合重装前留存、误删后恢复或手动留档。\n\n" +
                    "1. 点击“创建备份”，设置独立备份口令。\n" +
                    "2. 把 .phub 文件保存到下载目录、U 盘或你信任的位置。\n" +
                    "3. 需要恢复时点击“选择备份文件”，输入口令。\n" +
                    "4. 选择“合并恢复”保留当前内容；只有确认清空当前安全箱时才选择“替换全部”。"
            )
        )
        content.addView(
            infoPanel(
                "换机数据迁移",
                "旧手机：创建 .phub 备份。\n\n" +
                    "传输：通过数据线、电脑、手机互传、蓝牙、U 盘或 SD 卡把文件传到新手机。\n\n" +
                    "新手机：安装隐私中转站后，在本页选择该文件并输入原备份口令。恢复不要求先激活 Pro。\n\n" +
                    "不要把备份口令和 .phub 文件放在同一聊天记录、同一网盘目录或同一张截图中。"
            )
        )
        content.addView(
            panel(
                "导出加密备份（Pro）",
                "导出安全箱内容、自动填充登录信息和便携设置。不会包含 Pro 授权、设备绑定、安全锁、通知栏状态或诊断报告。",
                "创建备份",
                ButtonKind.Primary
            ) { requestExport() }
        )
        content.addView(
            panel(
                "恢复已有备份",
                "即使尚未激活 Pro，也可以恢复此前创建的加密备份。恢复时可选择合并或替换。",
                "选择备份文件",
                ButtonKind.Secondary
            ) {
                openBackupFile.launch(arrayOf(BACKUP_MIME_TYPE, "application/octet-stream"))
            }
        )
        content.addView(Button(this).apply {
            text = "返回设置"
            FrostedUi.styleOutlinedButton(this)
            setOnClickListener { finish() }
            layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) }
        })
        root.addView(ScrollView(this).apply {
            addView(content)
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun requestExport() {
        val gate = proGate.check(ProFeature.BatchExport)
        if (!gate.allowed) {
            val dialog = AlertDialog.Builder(this)
                .setTitle(gate.title)
                .setMessage(gate.message)
                .setPositiveButton("管理 Pro 授权", null)
                .setNegativeButton("取消", null)
                .create()
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    startActivity(Intent(this, ProLicenseActivity::class.java))
                    dialog.dismiss()
                }
            }
            dialog.show()
            return
        }
        showExportPasswordDialog()
    }

    private fun showExportPasswordDialog() {
        val password = passwordInput("设置备份口令")
        val confirmation = passwordInput("再次输入备份口令")
        val content = dialogContent(password, confirmation)
        val dialog = AlertDialog.Builder(this)
            .setTitle("设置备份口令")
            .setMessage("请使用独立且可长期保管的口令。丢失口令后，备份无法恢复。")
            .setView(content)
            .setNegativeButton("取消", null)
            .setPositiveButton("继续", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val first = password.text.toString()
                val second = confirmation.text.toString()
                val error = BackupPasswordValidator.validationError(first, second)
                if (error != null) {
                    Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                pendingExportPassword = first.toCharArray()
                password.text?.clear()
                confirmation.text?.clear()
                dialog.dismiss()
                createBackupFile.launch(defaultBackupFileName())
            }
        }
        dialog.show()
    }

    private fun exportBackup(uriText: String, password: CharArray) {
        Thread {
            var plain: ByteArray? = null
            try {
                if (!proGate.check(ProFeature.BatchExport).allowed) {
                    throw BackupFormatException("Pro 授权当前不可用。")
                }
                val payload = backupRepository.createPayload()
                plain = BackupPayloadCodec.encode(payload)
                val encrypted = EncryptedBackupCodec.encrypt(plain, password)
                contentResolver.openOutputStream(android.net.Uri.parse(uriText), "w")?.use { output ->
                    output.write(encrypted)
                    output.flush()
                } ?: throw BackupFormatException("无法创建备份文件。")
                runOnUiThread {
                    Toast.makeText(this, "已创建加密备份：${payload.items.size} 条内容，${payload.autofillCredentials.size} 条登录信息", Toast.LENGTH_LONG).show()
                }
            } catch (error: Exception) {
                runOnUiThread { showFailure(error.message ?: "创建备份失败。") }
            } finally {
                plain?.fill(0)
                password.fill('\u0000')
            }
        }.start()
    }

    private fun showImportPasswordDialog() {
        val password = passwordInput("输入备份口令")
        val dialog = AlertDialog.Builder(this)
            .setTitle("输入备份口令")
            .setMessage("备份口令只用于本次恢复，不会保存到设备。")
            .setView(dialogContent(password))
            .setNegativeButton("取消", null)
            .setPositiveButton("读取备份", null)
            .create()
        importPasswordDialog = dialog
        importPasswordInput = password
        dialog.setOnShowListener {
            val readButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val cancelButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            readButton.setOnClickListener {
                if (backupImportSession.isReading) return@setOnClickListener
                val value = password.text.toString().toCharArray()
                password.text?.clear()
                if (value.isEmpty()) {
                    value.fill('\u0000')
                    dialog.setMessage("请输入备份口令。")
                    password.requestFocus()
                    return@setOnClickListener
                }

                val attempt = backupImportSession.beginAttempt(value)
                if (attempt == null) {
                    value.fill('\u0000')
                    return@setOnClickListener
                }
                readButton.isEnabled = false
                cancelButton.isEnabled = false
                dialog.setCancelable(false)
                readBackup(attempt, value) { result ->
                    if (isFinishing || isDestroyed || !dialog.isShowing) return@readBackup
                    if (!backupImportSession.finishAttempt(attempt, result)) return@readBackup

                    readButton.isEnabled = true
                    cancelButton.isEnabled = true
                    dialog.setCancelable(true)
                    when (result) {
                        is BackupImportReadResult.AuthenticationFailed -> {
                            dialog.setMessage("备份口令错误，或备份文件已损坏，请重新输入后重试。")
                            password.text?.clear()
                            password.requestFocus()
                            password.post {
                                if (!isFinishing && !isDestroyed && dialog.isShowing) {
                                    (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)
                                        ?.showSoftInput(password, InputMethodManager.SHOW_IMPLICIT)
                                }
                            }
                        }
                        is BackupImportReadResult.Success -> {
                            password.text?.clear()
                            dialog.dismiss()
                            showRestoreModeDialog(result.payload)
                        }
                        is BackupImportReadResult.Failure -> {
                            password.text?.clear()
                            dialog.dismiss()
                            showFailure(result.message)
                        }
                    }
                }
            }

            cancelButton.setOnClickListener {
                backupImportSession.cancel()
                password.text?.clear()
                dialog.dismiss()
            }
        }
        dialog.setOnCancelListener {
            backupImportSession.cancel()
            password.text?.clear()
        }
        dialog.setOnDismissListener {
            backupImportSession.cancel()
            password.text?.clear()
            if (importPasswordDialog === dialog) importPasswordDialog = null
            if (importPasswordInput === password) importPasswordInput = null
        }
        dialog.show()
    }

    private fun readBackup(
        attempt: BackupImportAttempt,
        password: CharArray,
        onCompleted: (BackupImportReadResult) -> Unit
    ) {
        Thread {
            val result = BackupImportReader.read(password) {
                contentResolver.openInputStream(Uri.parse(attempt.uri))?.use(::readBackupBytes)
                    ?: throw BackupFormatException("无法读取备份文件。")
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                onCompleted(result)
            }
        }.start()
    }

    private fun showRestoreModeDialog(payload: BackupPayload) {
        pendingRestorePayload = payload
        AlertDialog.Builder(this)
            .setTitle("选择恢复方式")
            .setMessage("备份包含 ${payload.items.size} 条内容和 ${payload.autofillCredentials.size} 条登录信息。合并会保留当前内容并跳过已恢复过的条目；替换会先清空当前安全箱。")
            .setNegativeButton("取消") { _, _ -> pendingRestorePayload = null }
            .setNeutralButton("替换全部") { _, _ -> confirmReplace(payload) }
            .setPositiveButton("合并恢复") { _, _ -> restore(payload, RestoreMode.Merge) }
            .show()
    }

    private fun confirmReplace(payload: BackupPayload) {
        AlertDialog.Builder(this)
            .setTitle("确认替换当前安全箱？")
            .setMessage("当前安全箱内容和自动填充登录信息会被删除，然后恢复此备份。此操作无法撤销。")
            .setNegativeButton("取消") { _, _ -> pendingRestorePayload = null }
            .setPositiveButton("确认替换") { _, _ -> restore(payload, RestoreMode.Replace) }
            .show()
    }

    private fun restore(payload: BackupPayload, mode: RestoreMode) {
        Thread {
            try {
                val result = backupRepository.restore(payload, mode)
                pendingRestorePayload = null
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "恢复完成：内容新增 ${result.insertedItems} 条、跳过 ${result.skippedItems} 条；登录信息新增 ${result.insertedCredentials} 条、跳过 ${result.skippedCredentials} 条",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (error: Exception) {
                runOnUiThread { showFailure(error.message ?: "恢复失败。") }
            }
        }.start()
    }

    private fun readBackupBytes(input: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            if (output.size() + read > MAX_BACKUP_FILE_BYTES) {
                throw BackupFormatException("备份文件过大。")
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun showFailure(message: String) {
        AlertDialog.Builder(this)
            .setTitle("操作未完成")
            .setMessage(message)
            .setPositiveButton("知道了", null)
            .show()
    }

    private fun panel(
        title: String,
        body: String,
        actionLabel: String,
        kind: ButtonKind,
        action: () -> Unit
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            FrostedUi.applyPanel(this, radiusDp = 24)
            addView(titleText(title, 17f))
            addView(bodyText(body))
            addView(Button(this@BackupActivity).apply {
                text = actionLabel
                when (kind) {
                    ButtonKind.Primary -> FrostedUi.styleTonalButton(this)
                    ButtonKind.Danger -> FrostedUi.styleDangerOutlinedButton(this)
                    ButtonKind.Secondary, ButtonKind.Plain -> FrostedUi.styleOutlinedButton(this)
                }
                setOnClickListener { action() }
                layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(14) }
            })
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) }
        }
    }

    private fun infoPanel(title: String, body: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            FrostedUi.applyPanel(this, radiusDp = 24)
            addView(titleText(title, 17f))
            addView(bodyText(body))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) }
        }
    }

    private fun titleText(text: String, size: Float = 28f): TextView = TextView(this).apply {
        this.text = text
        textSize = size
        gravity = Gravity.START
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        setTextColor(FrostedUi.textPrimary)
    }

    private fun bodyText(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(FrostedUi.textSecondary)
        setLineSpacing(dp(2).toFloat(), 1f)
        setPadding(0, dp(8), 0, 0)
    }

    private fun passwordInput(hint: String): EditText = EditText(this).apply {
        this.hint = hint
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        FrostedUi.styleInput(this)
    }

    private fun dialogContent(vararg views: EditText): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(4), dp(4), dp(4), 0)
        views.forEachIndexed { index, view ->
            addView(view, LinearLayout.LayoutParams(-1, dp(52)).apply {
                if (index > 0) topMargin = dp(10)
            })
        }
    }

    private fun defaultBackupFileName(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        return "PrivacyHub-$timestamp.phub"
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val BACKUP_MIME_TYPE = "application/vnd.privacyhub.backup"
        private const val MAX_BACKUP_FILE_BYTES = 33 * 1024 * 1024
    }
}
