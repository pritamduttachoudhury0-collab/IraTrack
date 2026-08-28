package com.iratrack.app.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncPolicyTest {
    @Test fun successfulProviderDoesNotHideATransientFailure() {
        assertEquals(SyncPolicy.Decision.RETRY, SyncPolicy.classify(1, listOf("HTTP 500")))
    }

    @Test fun successfulProviderCanHideAPermanentFailure() {
        assertEquals(SyncPolicy.Decision.SUCCESS, SyncPolicy.classify(1, listOf("invalid API key")))
    }

    @Test fun transientFailureIsRetryable() {
        assertEquals(SyncPolicy.Decision.RETRY, SyncPolicy.classify(0, listOf("temporarily unavailable")))
        assertEquals(SyncPolicy.Decision.RETRY, SyncPolicy.classify(0, listOf("HTTP 503")))
        assertEquals(SyncPolicy.Decision.RETRY, SyncPolicy.classify(0, listOf("rate-limited")))
        assertEquals(SyncPolicy.Decision.RETRY, SyncPolicy.classify(0, listOf("network error")))
    }

    @Test fun permanentFailureIsNotRetried() {
        assertEquals(SyncPolicy.Decision.FAILURE, SyncPolicy.classify(0, listOf("invalid API key")))
        assertEquals(SyncPolicy.Decision.FAILURE, SyncPolicy.classify(0, listOf("permission denied")))
    }

    @Test fun noCredentialsMeansNothingToDo() {
        assertEquals(SyncPolicy.Decision.SUCCESS, SyncPolicy.classify(0, emptyList()))
    }
}
