package com.privacyhub.automation

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.privacyhub.data.repository.TestReportRepositoryImpl
import com.privacyhub.domain.model.EntryPoint
import com.privacyhub.domain.model.LinkTarget
import com.privacyhub.util.AppExecutors

object NetdiskAccessCodeTaskController {
    private val registry = NetdiskAccessCodeTaskRegistry()
    private val handler = Handler(Looper.getMainLooper())

    fun start(
        context: Context,
        accessCode: String,
        target: LinkTarget,
        targetHost: String,
        expectedPackageName: String,
        fallbackBrowserPackageName: String?
    ): PendingNetdiskAccessCodeTask {
        val appContext = context.applicationContext
        val task = registry.start(accessCode, target, targetHost, expectedPackageName, fallbackBrowserPackageName)
        handler.postDelayed({ expire(appContext, task.id) }, NetdiskAccessCodeTaskRegistry.DEFAULT_TIMEOUT_MILLIS)
        return task
    }

    fun updateExpectedPackage(id: Long, packageName: String) {
        registry.updateExpectedPackage(id, packageName)
    }

    fun matching(packageName: String, currentHost: String?): PendingNetdiskAccessCodeTask? =
        registry.match(packageName, currentHost)

    fun complete(context: Context, task: PendingNetdiskAccessCodeTask) {
        if (registry.complete(task.id) == null) return
        record(context.applicationContext, task.target, success = true, failureReason = null, note = "已自动填入提取码，未点击确认")
        AppExecutors.executeMain {
            Toast.makeText(context.applicationContext, "已填入提取码，请手动确认", Toast.LENGTH_SHORT).show()
        }
    }

    fun cancel(id: Long) {
        registry.cancel(id)
    }

    private fun expire(context: Context, id: Long) {
        val task = registry.complete(id) ?: return
        record(
            context,
            task.target,
            success = false,
            failureReason = "未找到明确标注的提取码输入框或任务已超时",
            note = "自动填码未执行；未记录链接或提取码"
        )
        Toast.makeText(context, "未找到提取码输入框，可手动输入", Toast.LENGTH_SHORT).show()
    }

    private fun record(
        context: Context,
        target: LinkTarget,
        success: Boolean,
        failureReason: String?,
        note: String
    ) {
        AppExecutors.executeDisk {
            TestReportRepositoryImpl(context).record(
                entryPoint = EntryPoint.AUTOMATION,
                success = success,
                failureReason = failureReason,
                target = target,
                note = note
            )
        }
    }
}
