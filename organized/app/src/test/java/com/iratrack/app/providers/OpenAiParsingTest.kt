package com.iratrack.app.providers

import com.iratrack.app.data.CostStatus
import com.iratrack.app.data.UnitKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiParsingTest {

    // Shape taken from OpenAI's published /v1/organization/costs example response.
    private val costsFixture = """
        {
          "object": "page",
          "data": [
            {
              "object": "bucket",
              "start_time": 1730419200,
              "end_time": 1730505600,
              "results": [
                { "object": "organization.costs.result", "amount": { "value": 0.06, "currency": "usd" }, "line_item": "gpt-4o-2024-08-06" },
                { "object": "organization.costs.result", "amount": { "value": 1.23, "currency": "usd" }, "line_item": "gpt-4o-mini" }
              ]
            }
          ]
        }
    """.trimIndent()

    private val usageFixture = """
        {
          "object": "page",
          "data": [
            {
              "object": "bucket",
              "start_time": 1730419200,
              "end_time": 1730505600,
              "results": [
                { "model": "gpt-4o-2024-08-06", "input_tokens": 1000, "output_tokens": 250, "num_model_requests": 4 }
              ]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun costs_parses_each_result_as_a_reported_record() {
        val records = OpenAiParsing.costs(costsFixture, "OPENAI")

        assertEquals(2, records.size)
        val first = records[0]
        assertEquals("OPENAI", first.provider)
        assertEquals(0.06, first.costUsd!!, 0.0001)
        assertEquals("gpt-4o-2024-08-06", first.model)
        assertEquals(CostStatus.REPORTED, first.status)
        assertEquals(1730419200_000L, first.timestamp)
        assertEquals("openai-cost-1730419200-0", first.sourceId)
        assertTrue(records[1].sourceId != first.sourceId)
    }

    @Test
    fun costs_never_marks_a_record_reported_without_a_real_amount() {
        val missingAmount = """{"data":[{"start_time":1,"results":[{"line_item":"x"}]}]}"""
        val records = OpenAiParsing.costs(missingAmount, "OPENAI")
        assertTrue(records.isEmpty())
    }

    @Test
    fun usage_parses_tokens_as_usage_only_never_reported() {
        val records = OpenAiParsing.usage(usageFixture, "OPENAI")

        assertEquals(1, records.size)
        val record = records[0]
        assertEquals(CostStatus.USAGE_ONLY, record.status)
        assertNull(record.costUsd)
        assertEquals(UnitKind.TOKENS, record.unitKind)
        assertEquals(1000.0, record.inputUnits!!, 0.0001)
        assertEquals(250.0, record.outputUnits!!, 0.0001)
        assertEquals(1250.0, record.totalUnits!!, 0.0001)
        assertEquals(4L, record.requests)
    }

    @Test
    fun costs_handles_empty_data_array_without_throwing() {
        assertTrue(OpenAiParsing.costs("""{"object":"page","data":[]}""", "OPENAI").isEmpty())
    }

    @Test
    fun usage_handles_malformed_bucket_without_throwing() {
        val malformed = """{"data":[{"start_time":1,"results":[{"model":"x"}]}]}"""
        assertTrue(OpenAiParsing.usage(malformed, "OPENAI").isEmpty())
    }
}
