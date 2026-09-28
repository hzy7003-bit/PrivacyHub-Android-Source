package com.privacyhub.presentation.legal

import android.app.AlertDialog
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.privacyhub.R
import com.privacyhub.presentation.security.SecureActivity
import com.privacyhub.presentation.ui.ButtonKind
import com.privacyhub.presentation.ui.FrostedUi

class ThirdPartyLicensesActivity : SecureActivity() {
    override fun onSecureCreate(savedInstanceState: Bundle?) {
        super.onSecureCreate(savedInstanceState)
        title = getString(R.string.third_party_license_title)
    }

    override fun onUnlocked() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(16))
            FrostedUi.applyPageChrome(this)
        }
        root.addView(TextView(this).apply {
            text = getString(R.string.third_party_license_title)
            textSize = 26f
            setTextColor(FrostedUi.textPrimary)
        })
        root.addView(TextView(this).apply {
            text = getString(R.string.third_party_license_intro)
            textSize = 14f
            setTextColor(FrostedUi.textSecondary)
            setPadding(0, dp(10), 0, dp(16))
        })
        root.addView(licenseButton(getString(R.string.third_party_sqlcipher_license), R.raw.sqlcipher_community_license))
        root.addView(licenseButton(getString(R.string.third_party_guava_license), R.raw.apache_2_license))
        root.addView(licenseButton(getString(R.string.third_party_androidx_license), R.raw.apache_2_license))
        setContentView(root)
    }

    private fun licenseButton(label: String, resourceId: Int) = android.widget.Button(this).apply {
        text = label
        FrostedUi.styleOutlinedButton(this)
        setOnClickListener { showLicense(label, resourceId) }
        layoutParams = LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(8) }
    }

    private fun showLicense(title: String, resourceId: Int) {
        val license = resources.openRawResource(resourceId).bufferedReader().use { it.readText() }
        val text = TextView(this).apply {
            this.text = license
            textSize = 13f
            setTextColor(FrostedUi.textPrimary)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setTextIsSelectable(true)
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(ScrollView(this).apply { addView(text) })
            .setPositiveButton(R.string.third_party_license_close, null)
            .show()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
