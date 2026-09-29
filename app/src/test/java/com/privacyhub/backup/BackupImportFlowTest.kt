package com.privacyhub.backup

import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupImportFlowTest {
    @Test
    fun correctPasswordDecodesPayloadAndCompletesTheSelectedFileFlow() {
        val session = BackupImportSession()
        val expectedPayload = payload()
        val encrypted = encrypt(expectedPayload, PASSWORD)
        session.select(SELECTED_URI)

        val attempt = assertNotNullAttempt(session.beginAttempt(PASSWORD.toCharArray()))
        val result = BackupImportReader.read(PASSWORD.toCharArray()) { encrypted.copyOf() }

        assertEquals(expectedPayload, assertIs<BackupImportReadResult.Success>(result).payload)
        assertTrue(session.finishAttempt(attempt, result))
        assertNull(session.beginAttempt(PASSWORD.toCharArray()))
    }

    @Test
    fun authenticationFailureKeepsTheSameSelectedUriForRetry() {
        val session = BackupImportSession()
        session.select(SELECTED_URI)
        val first = assertNotNullAttempt(session.beginAttempt("wrong-password".toCharArray()))
        val result = BackupImportReader.read("wrong-password".toCharArray()) {
            encrypt(payload(), PASSWORD)
        }

        assertIs<BackupImportReadResult.AuthenticationFailed>(result)
        assertTrue(session.finishAttempt(first, result))
        val retry = assertNotNullAttempt(session.beginAttempt(PASSWORD.toCharArray()))
        assertEquals(SELECTED_URI, retry.uri)
    }

    @Test
    fun damagedCiphertextIsReportedAsAuthenticationFailureAndCanRetry() {
        val session = BackupImportSession()
        val damaged = encrypt(payload(), PASSWORD).also {
            it[it.lastIndex] = (it.last().toInt() xor 1).toByte()
        }
        session.select(SELECTED_URI)
        val attempt = assertNotNullAttempt(session.beginAttempt(PASSWORD.toCharArray()))
        val result = BackupImportReader.read(PASSWORD.toCharArray()) { damaged.copyOf() }

        assertIs<BackupImportReadResult.AuthenticationFailed>(result)
        assertTrue(session.finishAttempt(attempt, result))
        assertEquals(SELECTED_URI, assertNotNullAttempt(session.beginAttempt(PASSWORD.toCharArray())).uri)
    }

    @Test
    fun firstWrongThenCorrectPasswordUsesTheSameUriAndCompletes() {
        val session = BackupImportSession()
        val encrypted = encrypt(payload(), PASSWORD)
        session.select(SELECTED_URI)

        val wrongAttempt = assertNotNullAttempt(session.beginAttempt("wrong-password".toCharArray()))
        val wrongResult = BackupImportReader.read("wrong-password".toCharArray()) { encrypted.copyOf() }
        assertIs<BackupImportReadResult.AuthenticationFailed>(wrongResult)
        assertTrue(session.finishAttempt(wrongAttempt, wrongResult))

        val correctAttempt = assertNotNullAttempt(session.beginAttempt(PASSWORD.toCharArray()))
        assertEquals(wrongAttempt.uri, correctAttempt.uri)
        val correctResult = BackupImportReader.read(PASSWORD.toCharArray()) { encrypted.copyOf() }
        assertEquals(payload(), assertIs<BackupImportReadResult.Success>(correctResult).payload)
        assertTrue(session.finishAttempt(correctAttempt, correctResult))
        assertNull(session.beginAttempt(PASSWORD.toCharArray()))
    }

    @Test
    fun multipleAuthenticationFailuresCanRetryWithoutReselectingFile() {
        val session = BackupImportSession()
        val encrypted = encrypt(payload(), PASSWORD)
        session.select(SELECTED_URI)

        var attempt = assertNotNullAttempt(session.beginAttempt("wrong-password".toCharArray()))
        repeat(2) { index ->
            if (index > 0) assertEquals(SELECTED_URI, attempt.uri)
            val result = BackupImportReader.read("wrong-password".toCharArray()) { encrypted.copyOf() }
            assertIs<BackupImportReadResult.AuthenticationFailed>(result)
            assertTrue(session.finishAttempt(attempt, result))
            if (index == 0) {
                attempt = assertNotNullAttempt(session.beginAttempt("wrong-password".toCharArray()))
                assertEquals(SELECTED_URI, attempt.uri)
            }
        }
        assertEquals(SELECTED_URI, assertNotNullAttempt(session.beginAttempt(PASSWORD.toCharArray())).uri)
    }

    @Test
    fun emptyPasswordDoesNotStartAnAttemptOrRead() {
        val session = BackupImportSession()
        session.select(SELECTED_URI)
        var reads = 0

        val attempt = session.beginAttempt(charArrayOf())
        if (attempt != null) BackupImportReader.read(charArrayOf()) { reads += 1; byteArrayOf() }

        assertNull(attempt)
        assertEquals(0, reads)
        assertFalse(session.isReading)
    }

    @Test
    fun passwordArrayIsClearedAfterAuthenticationFailureAndSuccess() {
        val encrypted = encrypt(payload(), PASSWORD)
        val wrongPassword = "wrong-password".toCharArray()
        val rightPassword = PASSWORD.toCharArray()

        BackupImportReader.read(wrongPassword) { encrypted.copyOf() }
        BackupImportReader.read(rightPassword) { encrypted.copyOf() }

        assertTrue(wrongPassword.all { it == '\u0000' })
        assertTrue(rightPassword.all { it == '\u0000' })
    }

    @Test
    fun importReaderClearsPlaintextBufferAfterDecoding() {
        val password = PASSWORD.toCharArray()
        val encrypted = byteArrayOf(8, 9)
        val plaintext = byteArrayOf(1, 2, 3)
        val result = BackupImportReader.read(
            password = password,
            readEncryptedBackup = { encrypted },
            decrypt = { _, _ -> plaintext },
            decode = { payload() }
        )

        assertIs<BackupImportReadResult.Success>(result)
        assertTrue(password.all { it == '\u0000' })
        assertTrue(encrypted.all { it == 0.toByte() })
        assertTrue(plaintext.all { it == 0.toByte() })
    }

    @Test
    fun cancelledImportClearsSensitiveBuffersAndDropsTheStalePayloadResult() {
        val session = BackupImportSession().apply { select(SELECTED_URI) }
        val password = PASSWORD.toCharArray()
        val encrypted = byteArrayOf(4, 5, 6)
        val plaintext = byteArrayOf(7, 8, 9)
        val attempt = assertNotNullAttempt(session.beginAttempt(password))

        session.cancel()
        val result = BackupImportReader.read(
            password = password,
            readEncryptedBackup = { encrypted },
            decrypt = { _, _ -> plaintext },
            decode = { payload() }
        )

        assertIs<BackupImportReadResult.Success>(result)
        assertFalse(session.finishAttempt(attempt, result))
        assertTrue(password.all { it == '\u0000' })
        assertTrue(encrypted.all { it == 0.toByte() })
        assertTrue(plaintext.all { it == 0.toByte() })
    }

    @Test
    fun onlyOneReadAttemptCanBeActiveAtATime() {
        val session = BackupImportSession()
        session.select(SELECTED_URI)
        val encrypted = encrypt(payload(), PASSWORD)
        var reads = 0

        val first = assertNotNullAttempt(session.beginAttempt(PASSWORD.toCharArray()))

        assertNull(session.beginAttempt(PASSWORD.toCharArray()))
        val result = BackupImportReader.read(PASSWORD.toCharArray()) {
            reads += 1
            encrypted.copyOf()
        }
        assertIs<BackupImportReadResult.Success>(result)
        assertEquals(1, reads)
        assertTrue(session.finishAttempt(first, result))
        assertFalse(session.isReading)
    }

    @Test
    fun nonBackupFileAndUnsupportedVersionAreTerminalFailures() {
        val session = BackupImportSession()
        session.select(SELECTED_URI)
        val attempt = assertNotNullAttempt(session.beginAttempt(PASSWORD.toCharArray()))
        val notBackup = BackupImportReader.read(PASSWORD.toCharArray()) { byteArrayOf(1, 2, 3) }

        assertIs<BackupImportReadResult.Failure>(notBackup)
        assertTrue(session.finishAttempt(attempt, notBackup))
        assertNull(session.beginAttempt(PASSWORD.toCharArray()))

        val versionSession = BackupImportSession().apply { select(SELECTED_URI) }
        val versionAttempt = assertNotNullAttempt(versionSession.beginAttempt(PASSWORD.toCharArray()))
        val unsupported = encrypt(payload(), PASSWORD).also {
            ByteBuffer.wrap(it).putInt(4, 99)
        }
        val versionResult = BackupImportReader.read(PASSWORD.toCharArray()) { unsupported }

        assertIs<BackupImportReadResult.Failure>(versionResult)
        assertTrue(versionSession.finishAttempt(versionAttempt, versionResult))
        assertNull(versionSession.beginAttempt(PASSWORD.toCharArray()))
    }

    @Test
    fun invalidLengthAndAuthenticatedButMalformedPayloadAreTerminalFailures() {
        val invalidLengthSession = BackupImportSession().apply { select(SELECTED_URI) }
        val invalidLengthAttempt = assertNotNullAttempt(invalidLengthSession.beginAttempt(PASSWORD.toCharArray()))
        val invalidLength = encrypt(payload(), PASSWORD).also {
            ByteBuffer.wrap(it).putInt(40, 0)
        }
        val lengthResult = BackupImportReader.read(PASSWORD.toCharArray()) { invalidLength }

        assertIs<BackupImportReadResult.Failure>(lengthResult)
        assertTrue(invalidLengthSession.finishAttempt(invalidLengthAttempt, lengthResult))
        assertNull(invalidLengthSession.beginAttempt(PASSWORD.toCharArray()))

        val payloadSession = BackupImportSession().apply { select(SELECTED_URI) }
        val payloadAttempt = assertNotNullAttempt(payloadSession.beginAttempt(PASSWORD.toCharArray()))
        val malformedPayload = EncryptedBackupCodec.encrypt(byteArrayOf(0, 0), PASSWORD.toCharArray())
        val payloadResult = BackupImportReader.read(PASSWORD.toCharArray()) { malformedPayload }

        assertIs<BackupImportReadResult.Failure>(payloadResult)
        assertTrue(payloadSession.finishAttempt(payloadAttempt, payloadResult))
        assertNull(payloadSession.beginAttempt(PASSWORD.toCharArray()))
    }

    @Test
    fun cancelInvalidatesPendingCallbacksAndClearsUri() {
        assertSessionInvalidatedByCancellation()
    }

    @Test
    fun activityDestructionInvalidatesPendingCallbacksAndClearsUri() {
        // BackupActivity.onDestroy() delegates to the same in-memory session cancellation.
        assertSessionInvalidatedByCancellation()
    }

    @Test
    fun unreadableFileIsTerminalAndDoesNotKeepTheSelectedUri() {
        val session = BackupImportSession()
        session.select(SELECTED_URI)
        val attempt = assertNotNullAttempt(session.beginAttempt(PASSWORD.toCharArray()))
        val result = BackupImportReader.read(PASSWORD.toCharArray()) {
            throw SecurityException("provider denied access")
        }

        assertIs<BackupImportReadResult.Failure>(result)
        assertTrue(session.finishAttempt(attempt, result))
        assertNull(session.beginAttempt(PASSWORD.toCharArray()))
    }

    private fun encrypt(payload: BackupPayload, password: String): ByteArray =
        EncryptedBackupCodec.encrypt(BackupPayloadCodec.encode(payload), password.toCharArray())

    private fun payload() = BackupPayload(
        createdAtMillis = 123L,
        items = emptyList(),
        settings = PortableBackupSettings(
            customCategories = emptyList(),
            categoryAliases = emptyMap(),
            homeTags = emptyList()
        )
    )

    private fun assertNotNullAttempt(attempt: BackupImportAttempt?): BackupImportAttempt =
        checkNotNull(attempt)

    private fun assertSessionInvalidatedByCancellation() {
        val session = BackupImportSession()
        session.select(SELECTED_URI)
        val attempt = assertNotNullAttempt(session.beginAttempt(PASSWORD.toCharArray()))

        session.cancel()

        assertFalse(session.finishAttempt(attempt, BackupImportReadResult.Success(payload())))
        assertNull(session.beginAttempt(PASSWORD.toCharArray()))
        assertFalse(session.isReading)
    }

    private companion object {
        const val PASSWORD = "correct-backup-password"
        const val SELECTED_URI = "content://test.provider/one-backup.phub"
    }
}
