package com.iratrack.app.providers

import com.iratrack.app.data.CostStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XaiParsingTest {

    // Shape taken from xAI's published /auth/management-keys/validation example response.
    private val validationFixture = """
        {
          "apiKeyId": "b86ba29d-9f47-4b3a-a6ae-e69432d5f0dc",
          "teamId": "65c1e471-205f-4566-9c5a-07198badf4ce",
          "scope": "SCOPE_TEAM",
          "scopeId": "65c1e471-205f-4566-9c5a-07198badf4ce",
          "name": "test key"
        }
    """.trimIndent()

    // Shape taken from xAI's published /v1/billing/teams/{team_id}/usage example response.
    private val usageFixture = """
        {
          "timeSeries": [
            {
              "group": ["Chat grok-4-0709"],
              "groupLabels": ["Chat grok-4-0709"],
              "dataPoints": [
                { "timestamp": "2025-10-01T00:00:00Z", "values": [0.75973725] },
                { "timestamp": "2025-10-02T00:00:00Z", "values": [0] }
              ]
            },
            {
              "group": ["grok-2-image-1212"],
              "groupLabels": ["grok-2-image-1212"],
              "dataPoints": [
                { "timestamp": "2025-10-03T00:00:00Z", "values": [0.14] }
              ]
            }
          ],
          "limitReached": false
        }
    """.trimIndent()

    @Test
    fun teamId_is_read_from_key_validation_response() {
        assertEquals("65c1e471-205f-4566-9c5a-07198badf4ce", XaiParsing.teamId(validationFixture))
    }

    @Test
    fun teamId_is_null_when_absent() {
        assertNull(XaiParsing.teamId("""{"apiKeyId":"x"}"""))
    }

    @Test
    fun usage_parses_every_group_and_data_point_as_a_reported_cost() {
        val records = XaiParsing.usage(usageFixture, "XAI")

        // 2 points in the first series + 1 in the second = 3, including the $0 day
        // (a provider-confirmed zero-spend day is real data, not a gap to be dropped).
        assertEquals(3, records.size)
        assertTrue(records.all { it.status == CostStatus.REPORTED })
        assertTrue(records.all { it.unitLabel == "USD" })

        val first = records.first { it.model == "Chat grok-4-0709" && it.costUsd == 0.75973725 }
        assertEquals(CostStatus.REPORTED, first.status)

        val zeroDay = records.first { it.model == "Chat grok-4-0709" && it.costUsd == 0.0 }
        assertEquals(0.0, zeroDay.costUsd!!, 0.0)

        val imageDay = records.first { it.model == "grok-2-image-1212" }
        assertEquals(0.14, imageDay.costUsd!!, 0.0001)
    }

    @Test
    fun usage_handles_empty_time_series_without_throwing() {
        assertTrue(XaiParsing.usage("""{"timeSeries":[],"limitReached":false}""", "XAI").isEmpty())
    }
}
