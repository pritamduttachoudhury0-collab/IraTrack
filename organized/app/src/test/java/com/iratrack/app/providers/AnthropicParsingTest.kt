package com.iratrack.app.providers

import com.iratrack.app.data.CostStatus
import com.iratrack.app.data.UnitKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnthropicParsingTest {

    // Anthropic documents "amount" as a decimal string in minor currency units:
    // "123.45" in "USD" represents $1.23. Getting this wrong would silently
    // overstate or understate every reported dollar figure by 100x.
    private val costsFixture = """
        {
          "data": [
            {
              "starting_at": "2026-08-01T00:00:00Z",
              "ending_at": "2026-08-02T00:00:00Z",
              "results": [
                { "amount": "123.45", "currency": "USD", "description": "claude-sonnet-4-6 tokens" },
                { "amount": "5000", "currency": "USD", "description": "claude-opus-4-8 tokens" }
              ]
            }
          ]
        }
    """.trimIndent()

    private val usageFixture = """
        {
          "data": [
            {
              "starting_at": "2026-08-01T00:00:00Z",
              "ending_at": "2026-08-02T00:00:00Z",
              "results": [
                { "model": "claude-sonnet-4-6", "input_tokens": 5000, "output_tokens": 1200, "request_count": 12 }
              ]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun costs_converts_cents_string_to_dollars() {
        val records = AnthropicParsing.costs(costsFixture, "ANTHROPIC")

        assertEquals(2, records.size)
        assertEquals(1.2345, records[0].costUsd!!, 0.0001)
        assertEquals(50.0, records[1].costUsd!!, 0.0001)
        assertEquals(CostStatus.REPORTED, records[0].status)
        assertEquals("USD", records[0].unitLabel)
        assertEquals("claude-sonnet-4-6 tokens", records[0].model)
    }

    @Test
    fun costs_skips_results_with_unparseable_amount() {
        val bad = """{"data":[{"starting_at":"2026-08-01T00:00:00Z","results":[{"amount":"not-a-number"}]}]}"""
        assertTrue(AnthropicParsing.costs(bad, "ANTHROPIC").isEmpty())
    }

    @Test
    fun usage_parses_tokens_as_usage_only() {
        val records = AnthropicParsing.usage(usageFixture, "ANTHROPIC")

        assertEquals(1, records.size)
        val record = records[0]
        assertEquals(CostStatus.USAGE_ONLY, record.status)
        assertNull(record.costUsd)
        assertEquals(UnitKind.TOKENS, record.unitKind)
        assertEquals(5000.0, record.inputUnits!!, 0.0001)
        assertEquals(1200.0, record.outputUnits!!, 0.0001)
        assertEquals(12L, record.requests)
        assertEquals("claude-sonnet-4-6", record.model)
    }

    @Test
    fun usage_falls_back_to_num_requests_when_request_count_absent() {
        val fixture = """{"data":[{"starting_at":"2026-08-01T00:00:00Z","results":[{"model":"x","input_tokens":10,"num_requests":3}]}]}"""
        val records = AnthropicParsing.usage(fixture, "ANTHROPIC")
        assertEquals(3L, records[0].requests)
    }
}
