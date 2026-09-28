package com.privacyhub.presentation.security

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.privacyhub.data.settings.AppSettingsStore
import com.privacyhub.data.db.VaultDatabaseUnavailableException
import com.privacyhub.presentation.database.DatabaseUnavailableActivity
import com.privacyhub.presentation.ui.FrostedUi
import com.privacyhub.security.lock.AppLockSession
import com.privacyhub.security.lock.SecureWindow
import com.privacyhub.security.privacy.RecentsVisibility

abstract class SecureActivity : FragmentActivity() {
    private var authPromptActive = false
    private val settingsStore by lazy { AppSettingsStore(this) }

    final override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SecureWindow.apply(this)
        RecentsVisibility.apply(this)
        FrostedUi.applyWindow(this)
        onSecureCreate(savedInstanceState)
        if (shouldRequireAuthentication()) {
            showLockedContent()
        }
    }

    final override fun onResume() {
        super.onResume()
        SecureWindow.apply(this)
        RecentsVisibility.apply(this)
        FrostedUi.applyWindow(this)
        refreshSecureContent()
    }

    /** Re-evaluate authentication when a host changes sensitive destinations. */
    protected fun refreshSecureContent() {
        if (!shouldRequireAuthentication()) {
            AppLockSession.setAuthInProgress(false)
            notifyUnlocked()
            return
        }
        if (AppLockSession.isUnlocked()) {
            notifyUnlocked()
        } else {
            showLockedContent()
            requestAuthentication()
        }
    }

    protected open fun onSecureCreate(savedInstanceState: Bundle?) = Unit

    protected abstract fun onUnlocked()

    private fun notifyUnlocked() {
        try {
            onUnlocked()
        } catch (_: VaultDatabaseUnavailableException) {
            startActivity(Intent(this, DatabaseUnavailableActivity::class.java))
            finish()
        }
    }

    protected fun lockNowAndAuthenticate() {
        settingsStore.setSecurityLockEnabled(true)
        AppLockSession.lockNow()
        showLockedContent()
        requestAuthentication()
    }

    protected fun isSecurityLockEnabled(): Boolean {
        return settingsStore.isSecurityLockEnabled()
    }

    protected open fun shouldRequireAuthentication(): Boolean {
        return settingsStore.isSecurityLockEnabled()
    }

    protected fun canDisplaySecureContent(): Boolean =
        !shouldRequireAuthentication() || AppLockSession.isUnlocked()

    private fun requestAuthentication() {
        if (authPromptActive) return
        if (canUseBiometricPrompt()) {
            showBiometricPrompt()
        } else {
            launchDeviceCredential()
        }
    }

    private fun canUseBiometricPrompt(): Boolean {
        val manager = BiometricManager.from(this)
        val status = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        return status == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun showBiometricPrompt() {
        authPromptActive = true
        AppLockSession.setAuthInProgress(true)
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    authPromptActive = false
                    AppLockSession.markUnlocked()
                    notifyUnlocked()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    authPromptActive = false
                    if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        launchDeviceCredential()
                    } else {
                        AppLockSession.setAuthInProgress(false)
                        showLockedContent()
                    }
                }

                override fun onAuthenticationFailed() {
                    Toast.makeText(this@SecureActivity, "验证失败", Toast.LENGTH_SHORT).show()
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("解锁隐私中转站")
            .setSubtitle("验证后查看本地安全箱")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            promptInfo.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
        } else {
            promptInfo.setNegativeButtonText("使用系统密码")
        }

        prompt.authenticate(promptInfo.build())
    }

    private fun launchDeviceCredential() {
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!keyguardManager.isDeviceSecure) {
            AppLockSession.setAuthInProgress(false)
            showAuthUnavailable()
            return
        }

        val intent: Intent? = keyguardManager.createConfirmDeviceCredentialIntent(
            "解锁隐私中转站",
            "验证系统密码后查看本地安全箱"
        )
        if (intent == null) {
            AppLockSession.setAuthInProgress(false)
            showAuthUnavailable()
            return
        }

        authPromptActive = true
        AppLockSession.setAuthInProgress(true)
        startActivityForResult(intent, REQUEST_DEVICE_CREDENTIAL)
    }

    @Deprecated("Device credential fallback uses the platform result contract on API 26+.")
    final override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_DEVICE_CREDENTIAL) return
        authPromptActive = false
        if (resultCode == RESULT_OK) {
            AppLockSession.markUnlocked()
            notifyUnlocked()
        } else {
            AppLockSession.setAuthInProgress(false)
            showLockedContent()
        }
    }

    private fun showLockedContent() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            FrostedUi.applyPageChrome(this, leftDp = 24, topDp = 48, rightDp = 24, bottomDp = 24)
        }
        root.addView(TextView(this).apply {
            text = "隐私中转站已锁定"
            textSize = 24f
            setTextColor(FrostedUi.textPrimary)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "验证指纹、面容或系统密码后，才能查看安全箱。"
            textSize = 15f
            setTextColor(FrostedUi.textSecondary)
            setPadding(0, dp(10), 0, 0)
        })
        val views = com.privacyhub.presentation.ui.PorcelainViews(this)
        root.addView(views.button("重新验证") { requestAuthentication() })
        root.addView(views.button("返回") { onBackPressedDispatcher.onBackPressed() })
        setContentView(root)
    }

    private fun showAuthUnavailable() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            FrostedUi.applyPageChrome(this, leftDp = 24, topDp = 48, rightDp = 24, bottomDp = 24)
        }
        root.addView(TextView(this).apply {
            text = "无法启用安全箱锁"
            textSize = 24f
            setTextColor(FrostedUi.textPrimary)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "请先在系统设置中启用锁屏密码或生物识别。"
            textSize = 15f
            setTextColor(FrostedUi.textSecondary)
            setPadding(0, dp(10), 0, 0)
        })
        root.addView(com.privacyhub.presentation.ui.PorcelainViews(this).button("返回") {
            onBackPressedDispatcher.onBackPressed()
        })
        setContentView(root)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_DEVICE_CREDENTIAL = 4101
    }
}
