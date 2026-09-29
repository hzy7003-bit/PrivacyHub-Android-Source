package com.privacyhub.backup

internal sealed interface BackupImportReadResult {
    data class Success(val payload: BackupPayload) : BackupImportReadResult
    object AuthenticationFailed : BackupImportReadResult
    data class Failure(val message: String) : BackupImportReadResult
}

internal class BackupImportAttempt internal constructor(
    val uri: String,
    internal val sessionId: Long,
    internal val attemptId: Long
)

/** Holds the selected SAF URI only for the lifetime of the current in-memory restore flow. */
internal class BackupImportSession {
    private var sessionId = 0L
    private var nextAttemptId = 0L
    private var selectedUri: String? = null
    private var activeAttemptId: Long? = null

    val isReading: Boolean
        get() = activeAttemptId != null

    fun select(uri: String) {
        sessionId += 1
        selectedUri = uri
        activeAttemptId = null
    }

    fun beginAttempt(password: CharArray): BackupImportAttempt? {
        if (password.isEmpty() || activeAttemptId != null) return null
        val uri = selectedUri ?: return null
        val attemptId = ++nextAttemptId
        activeAttemptId = attemptId
        return BackupImportAttempt(uri, sessionId, attemptId)
    }

    /** Keeps the selected URI only for authentication failures, where retrying can help. */
    fun finishAttempt(
        attempt: BackupImportAttempt,
        result: BackupImportReadResult
    ): Boolean {
        if (attempt.sessionId != sessionId ||
            attempt.attemptId != activeAttemptId ||
            attempt.uri != selectedUri
        ) {
            return false
        }
        activeAttemptId = null
        if (result !is BackupImportReadResult.AuthenticationFailed) {
            selectedUri = null
        }
        return true
    }

    fun cancel() {
        sessionId += 1
        selectedUri = null
        activeAttemptId = null
    }
}

/** Reads/decrypts/parses off the UI thread; always clears password and temporary byte arrays. */
internal object BackupImportReader {
    fun read(
        password: CharArray,
        decrypt: (ByteArray, CharArray) -> ByteArray = EncryptedBackupCodec::decrypt,
        decode: (ByteArray) -> BackupPayload = BackupPayloadCodec::decode,
        readEncryptedBackup: () -> ByteArray
    ): BackupImportReadResult {
        var encryptedBytes: ByteArray? = null
        var plainBytes: ByteArray? = null
        return try {
            val encrypted = readEncryptedBackup()
            encryptedBytes = encrypted
            val plain = decrypt(encrypted, password)
            plainBytes = plain
            BackupImportReadResult.Success(decode(plain))
        } catch (_: BackupAuthenticationException) {
            BackupImportReadResult.AuthenticationFailed
        } catch (error: Exception) {
            BackupImportReadResult.Failure(error.message ?: "无法读取备份。")
        } finally {
            password.fill('\u0000')
            encryptedBytes?.fill(0)
            plainBytes?.fill(0)
        }
    }
}
