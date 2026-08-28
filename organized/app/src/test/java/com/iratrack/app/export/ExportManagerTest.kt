package com.iratrack.app.export

import com.iratrack.app.data.CostStatus
import com.iratrack.app.data.UnitKind
import com.iratrack.app.data.UsageRecord
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportManagerTest {

    private val sample = listOf(
        UsageRecord(
            provider = "OPENAI",
            timestamp = 1_000L,
            costUsd = 12.5,
            inputUnits = 100.0,
            outputUnits = 20.0,
            totalUnits = 120.0,
            unitKind = UnitKind.TOKENS,
            unitLabel = "tokens",
            requests = 3,
            model = "gpt-4o, \"fast\"",
            status = CostStatus.REPORTED,
            sourceId = "openai-cost-1000-0"
        )
    )

    @Test
    fun csv_has_header_and_one_row_per_record() {
        val lines = ExportManager.csvText(sample).trim().lines()
        assertEquals(2, lines.size)
        assertTrue(lines[0].startsWith("timestamp,provider,cost_usd"))
        assertTrue(lines[1].contains("OPENAI"))
    }

    @Test
    fun csv_escapes_quotes_and_commas_in_free_text_fields() {
        val csv = ExportManager.csvText(sample)
        // model contains a comma and a quote; must be wrapped and the inner quote doubled.
        assertTrue(csv.contains("\"gpt-4o, \"\"fast\"\"\""))
    }

    @Test
    fun csv_never_contains_credential_looking_fields() {
        val csv = ExportManager.csvText(sample)
        listOf("apiKey", "api_key", "credential", "Authorization", "x-api-key").forEach {
            assertFalse("CSV export must never contain $it", csv.contains(it, ignoreCase = true))
        }
    }

    @Test
    fun json_round_trips_the_record_fields() {
        val json = JSONArray(ExportManager.jsonText(sample))
        assertEquals(1, json.length())
        val obj = json.getJSONObject(0)
        assertEquals("OPENAI", obj.getString("provider"))
        assertEquals(12.5, obj.getDouble("costUsd"), 0.0001)
        assertEquals("REPORTED", obj.getString("costStatus"))
        assertEquals("TOKENS", obj.getString("unitKind"))
    }

    @Test
    fun json_represents_missing_optional_fields_as_null_not_omitted_or_zero() {
        val usageOnly = listOf(
            UsageRecord(
                provider = "ANTHROPIC",
                timestamp = 1L,
                costUsd = null,
                inputUnits = null,
                outputUnits = null,
                totalUnits = null,
                unitKind = UnitKind.OTHER,
                unitLabel = "USD",
                requests = null,
                model = null,
                status = CostStatus.UNAVAILABLE
            )
        )
        val obj = JSONArray(ExportManager.jsonText(usageOnly)).getJSONObject(0)
        assertTrue(obj.isNull("costUsd"))
        assertTrue(obj.isNull("model"))
    }

    @Test
    fun json_never_contains_credential_looking_fields() {
        val json = ExportManager.jsonText(sample)
        listOf("apiKey", "api_key", "credential", "Authorization", "x-api-key").forEach {
            assertFalse("JSON export must never contain $it", json.contains(it, ignoreCase = true))
        }
    }
}
