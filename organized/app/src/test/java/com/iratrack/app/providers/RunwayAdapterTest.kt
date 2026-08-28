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

class RunwayAdapterTest {

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

    private fun adapter() = RunwayAdapter(baseUrl = server.url("/").toString().trimEnd('/'))

    private val organizationBody = """{"id":"org_1","creditBalance":900,"usageTier":1}"""
    private val usageBody = """
        {"data":[{"date":"2026-08-01","model":"gen4_turbo","credits":100}]}
    """.trimIndent()

    @Test
    fun successful_sync_returns_balance_and_usage_both_credit_based() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(organizationBody))
        server.enqueue(MockResponse().setResponseCode(200).setBody(usageBody))

        val result = adapter().sync("test-key")

        assertTrue(result.success)
        assertEquals(2, result.records.size) // 1 balance snapshot + 1 usage entry
        assertTrue(result.records.all { it.status == CostStatus.CREDIT_BASED })
        assertTrue(result.records.all { it.costUsd == null })

        // First call is GET /v1/organization, second is POST /v1/organization/usage;
        // both must carry the credential as a Bearer token and the required version header.
        val first = server.takeRequest()
        val second = server.takeRequest()
        assertTrue(first.path!!.endsWith("/v1/organization"))
        assertTrue(second.path!!.endsWith("/v1/organization/usage"))
        assertEquals("Bearer test-key", first.getHeader("Authorization"))
        assertEquals("Bearer test-key", second.getHeader("Authorization"))
        assertEquals("2024-11-06", first.getHeader("X-Runway-Version"))
        assertEquals("2024-11-06", second.getHeader("X-Runway-Version"))
        assertEquals("POST", second.method)
        val requestBody = second.body.readUtf8()
        assertTrue(requestBody.contains("startDate"))
        assertTrue(requestBody.contains("beforeDate"))
        assertTrue(!requestBody.contains("endDate"))
    }

    @Test
    fun invalid_credential_on_both_calls_fails_the_whole_sync() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(401))

        val result = adapter().sync("bad-key")

        assertFalse(result.success)
        assertTrue(result.records.isEmpty())
        assertTrue(result.statusMessage.contains("invalid", ignoreCase = true))
    }

    @Test
    fun balance_failure_still_surfaces_successful_usage_data() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(200).setBody(usageBody))

        val result = adapter().sync("test-key")

        assertTrue(result.success)
        assertEquals(1, result.records.size)
        assertTrue(result.statusMessage.contains("Balance unavailable", ignoreCase = false))
    }

    @Test
    fun usage_failure_still_surfaces_successful_balance_data() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(organizationBody))
        server.enqueue(MockResponse().setResponseCode(500))

        val result = adapter().sync("test-key")

        assertTrue(result.success)
        assertEquals(1, result.records.size)
        assertEquals(CostStatus.CREDIT_BASED, result.records.first().status)
        assertTrue(result.statusMessage.contains("Usage unavailable", ignoreCase = false))
    }

    @Test
    fun network_failure_is_reported_distinctly_and_does_not_crash() = runTest {
        server.shutdown()

        val result = adapter().sync("test-key")

        assertFalse(result.success)
        assertTrue(result.records.isEmpty())
        assertTrue(result.statusMessage.startsWith("Runway"))
    }
}
