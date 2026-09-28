package com.privacyhub.presentation.diagnostics

import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.privacyhub.diagnostics.DiagnosticReport
import com.privacyhub.diagnostics.DiagnosticReportBuilder
import com.privacyhub.presentation.security.SecureActivity
import com.privacyhub.presentation.ui.ButtonKind
import com.privacyhub.presentation.ui.FrostedUi

class DiagnosticsActivity : SecureActivity() {
    override fun onUnlocked() {
        render()
    }

    private fun render() {
        val report = DiagnosticReportBuilder(this).build()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            FrostedUi.applyPageChrome(this)
        }
        root.addView(title("诊断报告"))
        root.addView(subtitle("可截图发给开发者，不包含安全箱原文"))

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(14), 0, 0)
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        renderReport(content, report)
        root.addView(fullWidthButton("返回设置", ButtonKind.Secondary) {
            finish()
        })
        setContentView(root)
    }

    private fun renderReport(content: LinearLayout, report: DiagnosticReport) {
        content.addView(section("基本信息", listOf(
            "生成时间：${report.generatedAt}",
            "App：${report.appVersion}"
        )))
        content.addView(section("设备信息", report.deviceInfo))
        content.addView(section("安全状态", report.securityInfo))
        content.addView(section("健康检查", report.healthChecks))
        content.addView(section("最近失败", report.recentFailures.ifEmpty { listOf("无") }))
        content.addView(section("最近操作", report.recentEvents.ifEmpty { listOf("无") }))
        content.addView(section("最近崩溃", listOf(report.lastCrash)))
    }

    private fun section(heading: String, lines: List<String>): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            FrostedUi.applyPanel(this, radiusDp = 22)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(0, 0, 0, dp(12))
            }
            addView(TextView(this@DiagnosticsActivity).apply {
                text = heading
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(FrostedUi.textPrimary)
            })
            addView(TextView(this@DiagnosticsActivity).apply {
                text = lines.joinToString("\n\n")
                textSize = 14f
                setTextColor(FrostedUi.textSecondary)
                setPadding(0, dp(8), 0, 0)
            })
        }
    }

    private fun fullWidthButton(text: String, kind: ButtonKind, action: () -> Unit): Button = Button(this).apply {
        this.text = text
        FrostedUi.styleMaterialButton(this, kind)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply {
            setMargins(0, dp(4), 0, 0)
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
