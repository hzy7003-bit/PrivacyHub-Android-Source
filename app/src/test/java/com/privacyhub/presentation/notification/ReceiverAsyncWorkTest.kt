package com.privacyhub.presentation.notification

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReceiverAsyncWorkTest {
    @Test
    fun receiverWorkIsQueuedAndPendingResultFinishesAfterBestEffortWork() {
        val queued = mutableListOf<() -> Unit>()
        var recorded = false
        var finishCount = 0

        ReceiverAsyncWork.dispatch(
            schedule = queued::add,
            work = { recorded = true },
            finish = { finishCount += 1 }
        )

        assertFalse(recorded, "onReceive must return before diagnostic work runs")
        assertEquals(0, finishCount)
        queued.single().invoke()
        assertTrue(recorded)
        assertEquals(1, finishCount)
    }

    @Test
    fun schedulingFailureStillFinishesPendingResultOnce() {
        var finishCount = 0

        ReceiverAsyncWork.dispatch(
            schedule = { throw IllegalStateException("executor unavailable") },
            work = { error("work must not run") },
            finish = { finishCount += 1 }
        )

        assertEquals(1, finishCount)
    }

    @Test
    fun diagnosticFailureStillFinishesPendingResult() {
        val queued = mutableListOf<() -> Unit>()
        var finishCount = 0

        ReceiverAsyncWork.dispatch(
            schedule = queued::add,
            work = { error("database unavailable") },
            finish = { finishCount += 1 }
        )
        queued.single().invoke()

        assertEquals(1, finishCount)
    }
}
