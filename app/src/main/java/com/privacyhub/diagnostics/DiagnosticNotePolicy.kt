package com.privacyhub.diagnostics

import com.privacyhub.domain.model.EntryPoint
import com.privacyhub.router.RouteFailureReason

/** Fixed, non-reversible descriptions for clipboard-driven diagnostic events. */
internal object DiagnosticNotePolicy {
    const val GENERIC_FAILURE_REASON = "操作失败，详细原因已脱敏"
    const val GENERIC_NOTE = "本地操作记录已脱敏"
    const val HISTORICAL_FAILURE_REASON = "历史失败原因已脱敏"

    const val NOTIFICATION_SAVE_SUCCEEDED = "通知栏保存成功"
    const val TILE_SAVE_SUCCEEDED = "快捷磁贴保存成功"
    const val NOTIFICATION_SAVE_AND_OPEN_SUCCEEDED = "通知栏保存并跳转成功"
    const val NOTIFICATION_SAVED_OPEN_FAILED = "通知栏已保存，跳转失败"
    const val NOTIFICATION_OPEN_FALLBACK_SUCCEEDED = "通知栏保存成功，已使用浏览器兜底"
    const val NOTIFICATION_OPEN_SUCCEEDED = "通知栏保存成功，已发起打开"
    const val NOTIFICATION_OPEN_FAILED = "通知栏保存成功，但未能发起打开"

    private val safeNotes = setOf(
        GENERIC_NOTE,
        "诊断记录已脱敏",
        NOTIFICATION_SAVE_SUCCEEDED,
        TILE_SAVE_SUCCEEDED,
        NOTIFICATION_SAVE_AND_OPEN_SUCCEEDED,
        NOTIFICATION_SAVED_OPEN_FAILED,
        NOTIFICATION_OPEN_FALLBACK_SUCCEEDED,
        NOTIFICATION_OPEN_SUCCEEDED,
        NOTIFICATION_OPEN_FAILED,
        "内容保存成功",
        "文本入口接收失败",
        "文本已保存到本地安全箱",
        "通知栏打开链接按钮读取剪切板失败",
        "常驻通知点击启动独立透明任务，获得焦点后读取失败",
        "磁贴直接读取失败后启动透明前台入口，获得焦点后仍读取失败",
        "磁贴直接读取失败后，透明前台入口启动失败",
        "磁贴点击后直接读取剪切板；直接读取失败，准备启动透明前台入口补读",
        "磁贴点击后先解锁再读取剪切板；直接读取失败，准备启动透明前台入口补读",
        "已自动填入提取码，未点击确认",
        "自动填码未执行；未记录链接或提取码",
        "用户拒绝通知权限",
        "用户需要先允许通知权限",
        "已请求显示常驻通知栏保存按钮",
        "用户手动关闭常驻通知栏保存按钮",
        "需要用户手动编辑控制中心添加磁贴",
        "控制中心按钮已添加",
        "控制中心按钮已经存在",
        "系统没有添加控制中心按钮",
        "请保持应用在前台后再试",
        "从列表页打开已保存链接",
        "从安全箱详情打开已保存链接",
        "已启动目标 App；口令类文本没有自动复制到剪切板",
        "已通过目标 App 发起打开",
        "已通过淘宝搜索入口发起打开；没有写入系统剪切板",
        RouteFailureReason.BROWSER_FALLBACK_USED,
        "自动填码未执行；目标网页身份无法确认",
        "自动填码未执行；未能解析浏览器",
        "自动填码未执行；浏览器分享页未打开"
    )

    private val safeFailureReasons = setOf(
        "系统 ClipboardManager 不可用",
        "系统剪切板为空",
        "系统返回 primaryClip=null",
        "剪切板没有可读取的 item",
        "剪切板文本为空",
        "未收到可处理的文本",
        "未发现标准自动填充字段",
        "未找到明确标注的提取码输入框或任务已超时",
        "Android 13+ 通知权限未允许",
        "POST_NOTIFICATIONS permission denied",
        "POST_NOTIFICATIONS permission missing",
        "requestAddTileService requires Android 13+",
        "StatusBarManager unavailable",
        "tile not added",
        "app not in foreground",
        "系统拒绝添加控制中心按钮",
        "无法安全确认目标网页",
        "未找到可用浏览器",
        RouteFailureReason.NO_WEB_LINK,
        RouteFailureReason.NO_TARGET_APP,
        RouteFailureReason.TARGET_PACKAGE_UNAVAILABLE,
        RouteFailureReason.TARGET_APP_NOT_INSTALLED,
        RouteFailureReason.TARGET_APP_NO_HANDLER,
        RouteFailureReason.TARGET_APP_START_FAILED,
        RouteFailureReason.TARGET_APP_START_REJECTED,
        RouteFailureReason.TAOBAO_APP_NOT_INSTALLED,
        RouteFailureReason.TAOBAO_SEARCH_UNAVAILABLE,
        RouteFailureReason.TAOBAO_START_REJECTED,
        RouteFailureReason.BROWSER_UNAVAILABLE,
        RouteFailureReason.BROWSER_START_FAILED,
        RouteFailureReason.BROWSER_START_REJECTED,
        RouteFailureReason.TARGET_AND_BROWSER_FAILED
    )

    fun saveSucceeded(entryPoint: EntryPoint): String = when (entryPoint) {
        EntryPoint.NOTIFICATION -> NOTIFICATION_SAVE_SUCCEEDED
        EntryPoint.TILE -> TILE_SAVE_SUCCEEDED
        else -> "内容保存成功"
    }

    /** Exact allowlist only: unknown/dynamic input is never partially retained. */
    fun safeNote(note: String?): String = note?.takeIf(safeNotes::contains) ?: GENERIC_NOTE

    /** Null remains null; every unknown failure string is replaced wholesale. */
    fun safeFailureReason(reason: String?): String? = reason?.let {
        it.takeIf(safeFailureReasons::contains) ?: GENERIC_FAILURE_REASON
    }
}
