package com.privacyhub.router

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri

data class OpenResult(
    val success: Boolean,
    val usedFallback: Boolean,
    val failureReason: String?,
    val routeNote: String? = null,
    val openedPackageName: String? = null
)

object BrowserFallback {
    /** Resolves the user's browser without matching a URL that may be verified by a target app. */
    fun resolveDefaultBrowserPackage(activity: Activity): String? = resolvePackage(
        activity,
        browserIntent("https://privacyhub-browser-resolution.invalid/")
    )

    fun resolveBrowserPackage(activity: Activity, url: String?): String? {
        if (url.isNullOrBlank()) return null
        return resolvePackage(activity, browserIntent(url))
    }

    fun open(activity: Activity, url: String?): OpenResult {
        if (url.isNullOrBlank()) {
            return OpenResult(false, true, RouteFailureReason.NO_WEB_LINK)
        }
        val intent = browserIntent(url)
        val resolvedPackage = resolveBrowserPackage(activity, url)
        return try {
            activity.applicationContext.startActivity(intent)
            OpenResult(true, true, null, "浏览器兜底已发起", resolvedPackage)
        } catch (error: ActivityNotFoundException) {
            OpenResult(false, true, RouteFailureReason.browserStartFailure(error))
        } catch (error: SecurityException) {
            OpenResult(false, true, RouteFailureReason.browserStartFailure(error))
        }
    }

    /** Opens a URL in the explicitly selected browser, avoiding app-link handoff to a target app. */
    fun openInPackage(activity: Activity, url: String?, packageName: String): OpenResult {
        if (url.isNullOrBlank()) {
            return OpenResult(false, true, RouteFailureReason.NO_WEB_LINK)
        }
        val intent = browserIntent(url).setPackage(packageName)
        return try {
            activity.applicationContext.startActivity(intent)
            OpenResult(true, true, null, "浏览器兜底已发起", packageName)
        } catch (error: ActivityNotFoundException) {
            OpenResult(false, true, RouteFailureReason.browserStartFailure(error))
        } catch (error: SecurityException) {
            OpenResult(false, true, RouteFailureReason.browserStartFailure(error))
        }
    }

    private fun browserIntent(url: String): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun resolvePackage(activity: Activity, intent: Intent): String? = try {
        activity.packageManager.resolveActivity(intent, 0)
            ?.activityInfo
            ?.packageName
    } catch (_: SecurityException) {
        null
    }
}

/** Stable, non-sensitive route failure descriptions safe for display and diagnostics. */
internal object RouteFailureReason {
    const val NO_WEB_LINK = "没有可打开的网页链接"
    const val NO_TARGET_APP = "没有可启动的目标 App"
    const val TARGET_PACKAGE_UNAVAILABLE = "目标 App 信息不可用"
    const val TARGET_APP_NOT_INSTALLED = "目标 App 未安装"
    const val TARGET_APP_NO_HANDLER = "目标 App 无法处理该链接"
    const val TARGET_APP_START_FAILED = "目标 App 启动失败"
    const val TARGET_APP_START_REJECTED = "系统拒绝启动目标 App"
    const val TAOBAO_APP_NOT_INSTALLED = "淘宝 App 未安装"
    const val TAOBAO_SEARCH_UNAVAILABLE = "淘宝搜索入口不可用"
    const val TAOBAO_START_REJECTED = "系统拒绝启动淘宝 App"
    const val BROWSER_UNAVAILABLE = "没有可用浏览器"
    const val BROWSER_START_FAILED = "浏览器启动失败"
    const val BROWSER_START_REJECTED = "系统拒绝启动浏览器"
    const val TARGET_AND_BROWSER_FAILED = "目标 App 与浏览器均无法打开链接"
    const val BROWSER_FALLBACK_USED = "目标 App 打开失败，已使用浏览器兜底"

    fun targetAppStartFailure(error: Exception): String = when (error) {
        is SecurityException -> TARGET_APP_START_REJECTED
        is ActivityNotFoundException -> TARGET_APP_START_FAILED
        else -> TARGET_APP_START_FAILED
    }

    fun browserStartFailure(error: Exception): String = when (error) {
        is SecurityException -> BROWSER_START_REJECTED
        is ActivityNotFoundException -> BROWSER_START_FAILED
        else -> BROWSER_START_FAILED
    }

    fun taobaoSearchFailure(outcome: CandidateLaunchOutcome): String = when (outcome) {
        CandidateLaunchOutcome.SECURITY_REJECTED -> TAOBAO_START_REJECTED
        CandidateLaunchOutcome.STARTED -> TAOBAO_SEARCH_UNAVAILABLE
        CandidateLaunchOutcome.NO_RESOLVABLE_ACTIVITY,
        CandidateLaunchOutcome.ACTIVITY_NOT_FOUND -> TAOBAO_SEARCH_UNAVAILABLE
    }

    fun targetAppCandidateFailure(outcome: CandidateLaunchOutcome): String = when (outcome) {
        CandidateLaunchOutcome.SECURITY_REJECTED -> TARGET_APP_START_REJECTED
        CandidateLaunchOutcome.NO_RESOLVABLE_ACTIVITY -> TARGET_APP_NO_HANDLER
        CandidateLaunchOutcome.ACTIVITY_NOT_FOUND -> TARGET_APP_START_FAILED
        CandidateLaunchOutcome.STARTED -> TARGET_APP_NO_HANDLER
    }
}
