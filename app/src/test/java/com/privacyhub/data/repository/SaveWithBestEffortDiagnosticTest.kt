package com.privacyhub.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals

class SaveWithBestEffortDiagnosticTest {
    @Test
    fun diagnosticFailureDoesNotChangeCompletedSaveResult() {
        val saved = saveWithBestEffortDiagnostic(
            save = { 42L },
            recordSuccess = { error("diagnostic unavailable") }
        )

        assertEquals(42L, saved)
    }
}
