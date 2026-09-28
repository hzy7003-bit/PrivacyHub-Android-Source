package com.privacyhub.router

import android.content.ActivityNotFoundException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RouteDiagnosticPrivacyTest {
    @Test
    fun taobaoSearchIntentExceptionMessagesNeverEscapeAsRouteReasons() {
        val sourceToken = "SECRET_TAOBAO_TOKEN_123456"
        val searchIntentUri = "https://s.m.taobao.com/search.htm?q=$sourceToken"

        val missingActivity = launchFailure(searchIntentUri) {
            ActivityNotFoundException("Unable to resolve Intent { dat=$searchIntentUri }")
        }
        val rejectedBySystem = launchFailure(searchIntentUri) {
            SecurityException("Permission denied for Intent { dat=$searchIntentUri }")
        }

        assertEquals(CandidateLaunchOutcome.ACTIVITY_NOT_FOUND, missingActivity.first)
        assertEquals(CandidateLaunchOutcome.SECURITY_REJECTED, rejectedBySystem.first)
        assertSafe(missingActivity.second, sourceToken, "s.m.taobao.com", "search.htm?q=")
        assertSafe(rejectedBySystem.second, sourceToken, "s.m.taobao.com", "search.htm?q=")
    }

    @Test
    fun ordinaryUrlTargetLaunchFailuresDiscardIntentData() {
        val url = "https://private.example/path?token=SECRET_TOKEN_ABC"
        listOf(
            ActivityNotFoundException("Unable to resolve Intent { dat=$url }"),
            SecurityException("Not allowed to start Intent { dat=$url }")
        ).forEach { error ->
            val outcome = launchFailure(url) { error }.first
            val reason = RouteFailureReason.targetAppCandidateFailure(outcome)
            assertSafe(reason, "private.example", "SECRET_TOKEN_ABC", "/path?token=")
        }
    }

    @Test
    fun browserFallbackFailuresUseFixedMessagesInsteadOfExceptionText() {
        val url = "https://private.example/path?token=SECRET_TOKEN_ABC"
        val activityMissing = RouteFailureReason.browserStartFailure(
            ActivityNotFoundException("No browser for Intent { dat=$url }")
        )
        val systemRejected = RouteFailureReason.browserStartFailure(
            SecurityException("Blocked Intent { dat=$url }")
        )

        assertEquals(RouteFailureReason.BROWSER_START_FAILED, activityMissing)
        assertEquals(RouteFailureReason.BROWSER_START_REJECTED, systemRejected)
        assertSafe(activityMissing, "private.example", "SECRET_TOKEN_ABC", "/path?token=")
        assertSafe(systemRejected, "private.example", "SECRET_TOKEN_ABC", "/path?token=")
    }

    @Test
    fun fallbackOutcomeDoesNotConcatenateEitherFailureReason() {
        val url = "https://private.example/path?token=SECRET_TOKEN_ABC"
        val result = AppIntentRouter.afterBrowserFallback(
            OpenResult(
                success = false,
                usedFallback = true,
                failureReason = "ActivityNotFoundException Intent data=$url"
            )
        )

        assertFalse(result.success)
        assertEquals(RouteFailureReason.TARGET_AND_BROWSER_FAILED, result.failureReason)
        assertNull(result.routeNote)
        assertSafe(result.failureReason.orEmpty(), "private.example", "SECRET_TOKEN_ABC", "Intent data=")

        val browserSucceeded = AppIntentRouter.afterBrowserFallback(
            OpenResult(
                success = true,
                usedFallback = true,
                failureReason = null,
                routeNote = "unexpected dynamic text $url"
            )
        )
        assertTrue(browserSucceeded.success)
        assertEquals(RouteFailureReason.BROWSER_FALLBACK_USED, browserSucceeded.routeNote)
        assertSafe(browserSucceeded.routeNote.orEmpty(), "private.example", "SECRET_TOKEN_ABC")
    }

    private fun launchFailure(
        candidate: String,
        exception: () -> Exception
    ): Pair<CandidateLaunchOutcome, String> {
        val outcome = launchFirstResolvableCandidate(
            candidates = listOf(candidate),
            canResolve = { true },
            launch = { throw exception() }
        )
        val reason = RouteFailureReason.taobaoSearchFailure(outcome)
        return outcome to reason
    }

    private fun assertSafe(value: String, vararg forbidden: String) {
        forbidden.forEach { assertFalse(value.contains(it)) }
    }
}
