package com.privacyhub.router

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.privacyhub.domain.model.LinkTarget

object AppIntentRouter {
    fun openTargetAppThenFallback(
        activity: Activity,
        url: String?,
        target: LinkTarget,
        sourceText: String? = null
    ): OpenResult {
        if (url.isNullOrBlank()) {
            if (target.usesTaobaoSearchFallback && !sourceText.isNullOrBlank()) {
                val searchResult = openTaobaoSearch(activity, sourceText)
                if (searchResult.success) return searchResult
            }
            return openTargetAppHome(activity, target)
        }

        val packageName = target.packageName
        if (packageName.isNullOrBlank()) {
            return BrowserFallback.open(activity, url)
        }

        val appResult = openWithPackage(activity, url, target)
        if (appResult.success) return appResult

        val browserResult = BrowserFallback.open(activity, url)
        return afterBrowserFallback(browserResult)
    }

    /** Never carries per-URL or exception details across the route/diagnostic boundary. */
    internal fun afterBrowserFallback(browserResult: OpenResult): OpenResult =
        if (browserResult.success) {
            OpenResult(
                success = true,
                usedFallback = true,
                failureReason = null,
                routeNote = RouteFailureReason.BROWSER_FALLBACK_USED,
                openedPackageName = browserResult.openedPackageName
            )
        } else {
            OpenResult(
                success = false,
                usedFallback = true,
                failureReason = RouteFailureReason.TARGET_AND_BROWSER_FAILED,
                routeNote = null
            )
        }

    private fun openTaobaoSearch(activity: Activity, sourceText: String): OpenResult {
        val packageName = LinkTarget.TAOBAO.packageName
            ?: return OpenResult(false, false, RouteFailureReason.TARGET_PACKAGE_UNAVAILABLE)
        val packageManager = activity.packageManager
        if (!isPackageInstalled(packageManager, packageName)) {
            return OpenResult(false, false, RouteFailureReason.TAOBAO_APP_NOT_INSTALLED)
        }

        val outcome = launchFirstResolvableCandidate(
            candidates = taobaoSearchIntents(packageName, sourceText),
            canResolve = { canResolve(packageManager, it) },
            launch = { activity.applicationContext.startActivity(it) }
        )
        if (outcome == CandidateLaunchOutcome.STARTED) {
            return OpenResult(
                success = true,
                usedFallback = false,
                failureReason = null,
                routeNote = "已通过淘宝搜索入口发起打开；没有写入系统剪切板",
                openedPackageName = packageName
            )
        }
        return OpenResult(false, false, RouteFailureReason.taobaoSearchFailure(outcome))
    }

    private fun taobaoSearchIntents(packageName: String, sourceText: String): List<Intent> {
        val searchUri = Uri.Builder()
            .scheme("https")
            .authority("s.m.taobao.com")
            .path("search.htm")
            .appendQueryParameter("q", sourceText)
            .build()
        return listOf(
            Intent(Intent.ACTION_VIEW, searchUri).apply {
                component = ComponentName(packageName, "com.taobao.browser.BrowserActivity")
                addCategory(Intent.CATEGORY_BROWSABLE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent(Intent.ACTION_VIEW, searchUri).apply {
                setPackage(packageName)
                addCategory(Intent.CATEGORY_BROWSABLE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    private fun openTargetAppHome(activity: Activity, target: LinkTarget): OpenResult {
        val packageName = target.packageName
            ?: return OpenResult(false, false, RouteFailureReason.NO_TARGET_APP)
        val packageManager = activity.packageManager
        if (!isPackageInstalled(packageManager, packageName)) {
            return OpenResult(false, false, RouteFailureReason.TARGET_APP_NOT_INSTALLED)
        }

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage(packageName)
            }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            activity.applicationContext.startActivity(launchIntent)
            OpenResult(
                success = true,
                usedFallback = false,
                failureReason = null,
                routeNote = "已启动目标 App；口令类文本没有自动复制到剪切板",
                openedPackageName = packageName
            )
        } catch (error: ActivityNotFoundException) {
            OpenResult(false, false, RouteFailureReason.targetAppStartFailure(error))
        } catch (error: SecurityException) {
            OpenResult(false, false, RouteFailureReason.targetAppStartFailure(error))
        }
    }

    private fun openWithPackage(activity: Activity, url: String, target: LinkTarget): OpenResult {
        val packageName = target.packageName
            ?: return OpenResult(false, false, RouteFailureReason.TARGET_PACKAGE_UNAVAILABLE)
        val packageManager = activity.packageManager
        if (!isPackageInstalled(packageManager, packageName)) {
            return OpenResult(false, false, RouteFailureReason.TARGET_APP_NOT_INSTALLED)
        }

        val intents = intentCandidates(url, target).map { candidate ->
            Intent(Intent.ACTION_VIEW, candidate.uri).apply {
                if (candidate.browsable) {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                setPackage(packageName)
            }
        }
        val outcome = launchFirstResolvableCandidate(
            candidates = intents,
            canResolve = { canResolve(packageManager, it) },
            launch = { activity.applicationContext.startActivity(it) }
        )
        if (outcome == CandidateLaunchOutcome.STARTED) {
            return OpenResult(
                success = true,
                usedFallback = false,
                failureReason = null,
                routeNote = "已通过目标 App 发起打开",
                openedPackageName = packageName
            )
        }
        return OpenResult(false, false, RouteFailureReason.targetAppCandidateFailure(outcome))
    }

    private fun intentCandidates(url: String, target: LinkTarget): List<IntentCandidate> {
        val candidates = mutableListOf<IntentCandidate>()
        if (target == LinkTarget.TAOBAO || target == LinkTarget.TAOBAO_PAYMENT) {
            TaobaoDeepLinkBuilder.build(url)?.let {
                candidates += IntentCandidate("淘宝短链 scheme", Uri.parse(it), browsable = false)
            }
        }
        if (target == LinkTarget.PINDUODUO) {
            pinduoduoGoodsUri(url)?.let {
                candidates += IntentCandidate("拼多多商品 scheme", it, browsable = false)
            }
        }
        candidates += IntentCandidate("原始网页链接", Uri.parse(url), browsable = true)
        return candidates
    }

    private fun pinduoduoGoodsUri(url: String): Uri? {
        val source = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        val goodsId = source.getQueryParameter("goods_id")?.takeIf { it.isNotBlank() } ?: return null
        return Uri.parse("pinduoduo://com.xunmeng.pinduoduo/goods.html?goods_id=$goodsId")
    }

    @Suppress("DEPRECATION")
    private fun isPackageInstalled(packageManager: PackageManager, packageName: String): Boolean {
        return runCatching {
            packageManager.getPackageInfo(packageName, 0)
        }.isSuccess
    }

    @Suppress("DEPRECATION")
    private fun canResolve(packageManager: PackageManager, intent: Intent): Boolean {
        return packageManager.queryIntentActivities(intent, 0).isNotEmpty()
    }

    private data class IntentCandidate(
        val label: String,
        val uri: Uri,
        val browsable: Boolean
    )
}

internal enum class CandidateLaunchOutcome {
    STARTED,
    NO_RESOLVABLE_ACTIVITY,
    ACTIVITY_NOT_FOUND,
    SECURITY_REJECTED
}

/** Executes candidates without carrying any exception detail into route results. */
internal fun <T> launchFirstResolvableCandidate(
    candidates: List<T>,
    canResolve: (T) -> Boolean,
    launch: (T) -> Unit
): CandidateLaunchOutcome {
    var activityNotFound = false
    var securityRejected = false

    candidates.forEach { candidate ->
        val resolvable = try {
            canResolve(candidate)
        } catch (_: ActivityNotFoundException) {
            activityNotFound = true
            false
        } catch (_: SecurityException) {
            securityRejected = true
            false
        }
        if (!resolvable) return@forEach

        try {
            launch(candidate)
            return CandidateLaunchOutcome.STARTED
        } catch (_: ActivityNotFoundException) {
            activityNotFound = true
        } catch (_: SecurityException) {
            securityRejected = true
        }
    }

    return when {
        securityRejected -> CandidateLaunchOutcome.SECURITY_REJECTED
        activityNotFound -> CandidateLaunchOutcome.ACTIVITY_NOT_FOUND
        else -> CandidateLaunchOutcome.NO_RESOLVABLE_ACTIVITY
    }
}
