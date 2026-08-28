package com.iratrack.app.providers

import com.iratrack.app.data.CostStatus
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class XaiAdapterTest {

    private lateinit var server: MockWebServer

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stop() {
        runCatching { server.shutdown() }
    }

    private fun adapter() = XaiAdapter(baseUrl = server.url("/").toString().trimEnd('/'))

    private val validationBody = """
        {"apiKeyId":"key-1","teamId":"team-123","scope":"SCOPE_TEAM","scopeId":"team-123","name":"test key"}
    """.trimIndent()

    private val usageBody = """
        {
          "timeSeries": [
            {"groupLabels":["grok-4-0709"],"dataPoints":[{"timestamp":"2026-08-01T00:00:00Z","values":[1.5]}]}
          ],
          "limitReached": false
        }
    """.trimIndent()

    @Test
    fun successful_sync_resolves_team_then_returns_real_reported_cost() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(validationBody))
        server.enqueue(MockResponse().setResponseCode(200).setBody(usageBody))

        val result = adapter().sync("mgmt-key")

        assertTrue(result.success)
        assertEquals(1, result.records.size)
        assertEquals(CostStatus.REPORTED, result.records.first().status)
        assertEquals(1.5, result.records.first().costUsd!!, 0.0001)

        // First call is validation, second is the billing usage call, and both must
        // carry the management key as a Bearer token.
        val first = server.takeRequest()
        val second = server.takeRequest()
        assertTrue(first.path!!.contains("management-keys/validation"))
        assertTrue(second.path!!.contains("team-123"))
        assertEquals("Bearer mgmt-key", first.getHeader("Authorization"))
        assertEquals("Bearer mgmt-key", second.getHeader("Authorization"))
    }

    @Test
    fun invalid_credential_is_reported_as_such_and_never_reaches_the_usage_call() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))

        val result = adapter().sync("bad-key")

        assertFalse(result.success)
        assertTrue(result.statusMessage.contains("invalid", ignoreCase = true))
        assertEquals(1, server.requestCount) // must short-circuit, not call the usage endpoint
    }

    @Test
    fun missing_permission_on_the_management_key_is_reported_distinctly_from_invalid_credential() = runTest {
        server.enqueue(MockResponse().setResponseCode(403))

        val result = adapter().sync("under-scoped-key")

        assertFalse(result.success)
        assertTrue(result.statusMessage.contains("permission", ignoreCase = true) || result.statusMessage.contains("denied", ignoreCase = true))
    }

    @Test
    fun missing_team_configuration_is_reported_when_validation_succeeds_without_a_team_id() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"apiKeyId":"key-1","scope":"SCOPE_UNSPECIFIED","name":"orphan key"}""")
        )

        val result = adapter().sync("orphan-key")

        assertFalse(result.success)
        assertTrue(result.statusMessage.contains("team", ignoreCase = true))
    }

    @Test
    fun api_failure_on_the_usage_endpoint_still_reports_the_earlier_successful_validation() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(validationBody))
        server.enqueue(MockResponse().setResponseCode(500))

        val result = adapter().sync("mgmt-key")

        assertFalse(result.success)
        assertTrue(result.records.isEmpty())
        assertTrue(result.statusMessage.contains("temporarily unavailable", ignoreCase = true))
    }

    @Test
    fun network_failure_is_reported_distinctly_and_does_not_crash() = runTest {
        server.shutdown()

        val result = adapter().sync("mgmt-key")

        assertFalse(result.success)
        assertTrue(result.records.isEmpty())
        assertTrue(result.statusMessage.startsWith("xAI:"))
    }
}
