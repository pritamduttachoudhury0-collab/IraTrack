package com.iratrack.app.providers

import com.iratrack.app.data.CostStatus
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeepSeekAdapterTest {

    private lateinit var server: MockWebServer

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stop() {
        // A test may already have shut the server down itself to simulate a
        // network-down scenario; shutting down twice throws, so ignore that case.
        runCatching { server.shutdown() }
    }

    private fun adapter() = DeepSeekAdapter(baseUrl = server.url("/").toString().trimEnd('/'))

    @Test
    fun successful_sync_returns_a_balance_snapshot_not_a_cost() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"is_available":true,"balance_infos":[{"currency":"USD","total_balance":"12.34","granted_balance":"0","topped_up_balance":"12.34"}]}"""
            )
        )

        val result = adapter().sync("test-key")

        assertTrue(result.success)
        assertEquals(1, result.records.size)
        val record = result.records.first()
        assertNull("DeepSeek must never report a balance as a cost figure", record.costUsd)
        assertEquals(CostStatus.UNAVAILABLE, record.status)
        assertEquals("Account balance", record.model)

        // The request that actually went out must carry the credential as a Bearer
        // token and never in the URL/query string.
        val recorded = server.takeRequest()
        assertEquals("Bearer test-key", recorded.getHeader("Authorization"))
        assertFalse(recorded.path!!.contains("test-key"))
    }

    @Test
    fun failed_authentication_is_reported_as_invalid_credential_not_a_generic_failure() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"invalid api key"}"""))

        val result = adapter().sync("bad-key")

        assertFalse(result.success)
        assertTrue(result.records.isEmpty())
        assertTrue(result.statusMessage.contains("invalid", ignoreCase = true))
        assertFalse(
            "Must not fall back to a generic message the user can't act on",
            result.statusMessage.contains("Something went wrong", ignoreCase = true)
        )
    }

    @Test
    fun api_failure_reports_the_provider_as_temporarily_unavailable() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))

        val result = adapter().sync("test-key")

        assertFalse(result.success)
        assertTrue(result.statusMessage.contains("temporarily unavailable", ignoreCase = true))
    }

    @Test
    fun network_failure_is_reported_distinctly_and_does_not_crash() = runTest {
        server.shutdown() // nothing is listening anymore

        val result = adapter().sync("test-key")

        assertFalse(result.success)
        assertTrue(result.records.isEmpty())
        assertTrue(result.statusMessage.startsWith("DeepSeek:"))
    }

    @Test
    fun no_historical_cost_claim_is_ever_made_when_balance_is_the_only_data() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"is_available":true,"balance_infos":[{"currency":"USD","total_balance":"5.00"}]}"""
            )
        )

        val result = adapter().sync("test-key")

        assertTrue(result.statusMessage.contains("no historical cost/usage API", ignoreCase = true))
        assertTrue(result.records.all { it.status != CostStatus.REPORTED })
    }
}
