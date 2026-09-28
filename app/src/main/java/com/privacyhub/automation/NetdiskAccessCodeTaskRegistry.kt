package com.privacyhub.automation

import com.privacyhub.domain.model.LinkTarget

data class PendingNetdiskAccessCodeTask(
    val id: Long,
    val accessCode: String,
    val target: LinkTarget,
    val targetHost: String,
    val expectedPackageName: String,
    val fallbackBrowserPackageName: String?,
    val expiresAtMillis: Long
)

class NetdiskAccessCodeTaskRegistry(
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS
) {
    private var nextId = 1L
    private var pending: PendingNetdiskAccessCodeTask? = null

    @Synchronized
    fun start(
        accessCode: String,
        target: LinkTarget,
        targetHost: String,
        expectedPackageName: String,
        fallbackBrowserPackageName: String? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): PendingNetdiskAccessCodeTask {
        val normalizedTargetHost = NetdiskHostPolicy.normalizeSupportedHost(target, targetHost)
            ?: throw IllegalArgumentException("Unsupported netdisk target host")
        val task = PendingNetdiskAccessCodeTask(
            id = nextId++,
            accessCode = accessCode,
            target = target,
            targetHost = normalizedTargetHost,
            expectedPackageName = expectedPackageName,
            fallbackBrowserPackageName = fallbackBrowserPackageName,
            expiresAtMillis = nowMillis + timeoutMillis
        )
        pending = task
        return task
    }

    @Synchronized
    fun match(
        packageName: String,
        currentHost: String?,
        nowMillis: Long = System.currentTimeMillis()
    ): PendingNetdiskAccessCodeTask? {
        val task = pending ?: return null
        if (nowMillis >= task.expiresAtMillis) {
            return null
        }
        if (task.expectedPackageName != packageName && task.fallbackBrowserPackageName != packageName) {
            return null
        }
        if (currentHost == null) return null
        if (currentHost != task.targetHost) {
            pending = null
            return null
        }
        return task
    }

    @Synchronized
    fun complete(id: Long): PendingNetdiskAccessCodeTask? {
        val task = pending?.takeIf { it.id == id } ?: return null
        pending = null
        return task
    }

    @Synchronized
    fun updateExpectedPackage(id: Long, packageName: String): PendingNetdiskAccessCodeTask? {
        val task = pending?.takeIf { it.id == id } ?: return null
        // A target app may hand the same deep link to the pre-resolved browser after accepting it.
        // Keep that single browser allow-list entry for this short-lived task.
        val updated = task.copy(expectedPackageName = packageName)
        pending = updated
        return updated
    }

    @Synchronized
    fun cancel(id: Long): PendingNetdiskAccessCodeTask? = complete(id)

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 60_000L
    }
}
