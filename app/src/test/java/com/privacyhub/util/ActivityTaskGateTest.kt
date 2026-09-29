package com.privacyhub.util

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActivityTaskGateTest {
    @Test
    fun newerVaultLoadDiscardsOlderResult() {
        val gate = GenerationTaskGate()
        val firstLoad = gate.next()
        val secondLoad = gate.next()

        assertFalse(gate.isCurrent(firstLoad))
        assertTrue(gate.isCurrent(secondLoad))
    }

    @Test
    fun fastSearchQueryChangeDiscardsPreviousQueryResult() {
        val gate = GenerationTaskGate()
        val firstQuery = "vault"
        val firstRequest = gate.next()
        val latestQuery = "vault item"
        val latestRequest = gate.next()

        assertFalse(firstQuery == latestQuery)
        assertFalse(gate.isCurrent(firstRequest))
        assertTrue(gate.isCurrent(latestRequest))
    }

    @Test
    fun destroyingOwnerClosesGateAndDiscardsPendingCallback() {
        val gate = GenerationTaskGate()
        val request = gate.next()

        gate.close()

        assertFalse(gate.isCurrent(request))
        assertFalse(gate.isCurrent(gate.next()))
    }

    @Test
    fun duplicateSaveCanOnlyAcquireSingleFlightGateOnce() {
        val gate = SingleFlightGate()

        assertTrue(gate.tryAcquire())
        assertFalse(gate.tryAcquire())
        gate.release()
        assertTrue(gate.tryAcquire())
    }
}
