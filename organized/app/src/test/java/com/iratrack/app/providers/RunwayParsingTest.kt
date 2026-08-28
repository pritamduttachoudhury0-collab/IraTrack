package com.iratrack.app.providers

import com.iratrack.app.data.CostStatus
import com.iratrack.app.data.UnitKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunwayParsingTest {

    // Shape confirmed directly from Runway's own published setup snippets
    // (org.creditBalance), which is what the setup guide/skill examples read.
    private val organizationFixture = """
        {
          "id": "org_123",
          "name": "My Organization",
          "creditBalance": 4321,
          "usageTier": 2
        }
    """.trimIndent()

    // POST /v1/organization/usage's exact response shape is not published with
    // an example body -- this fixture uses the field names RunwayParsing tries
    // first ("data" + "model"/"date"/"credits").
    private val usageFixture = """
        {
          "data": [
            { "date": "2026-08-01", "model": "gen4_turbo", "credits": 120 },
            { "date": "2026-08-01", "model": "gen4_5", "credits": 60 },
            { "date": "2026-08-02", "model": "gen4_turbo", "credits": 0 }
          ]
        }
    """.trimIndent()

    @Test
    fun organization_reads_credit_balance_as_credit_based_not_a_cost() {
        val record = RunwayParsing.organization(organizationFixture, "RUNWAY", 1_000L)

        assertTrue(record != null)
        assertNull("Runway credits must never be reported as a USD cost", record!!.costUsd)
        assertEquals(CostStatus.CREDIT_BASED, record.status)
        assertEquals(UnitKind.CREDITS, record.unitKind)
        assertEquals(4321.0, record.totalUnits!!, 0.0)
        assertEquals("Account credit balance", record.model)
    }

    @Test
    fun organization_returns_null_when_no_recognizable_balance_field_is_present() {
        assertNull(RunwayParsing.organization("""{"id":"org_123"}""", "RUNWAY", 1_000L))
    }

    @Test
    fun usage_parses_every_entry_including_zero_credit_days() {
        val records = RunwayParsing.usage(usageFixture, "RUNWAY")

        // A $0/0-credit day is real information (no usage that day), not a gap.
        assertEquals(3, records.size)
        assertTrue(records.all { it.status == CostStatus.CREDIT_BASED })
        assertTrue(records.all { it.unitKind == UnitKind.CREDITS })
        assertTrue(records.all { it.costUsd == null })

        val turboDay1 = records.first { it.model == "gen4_turbo" && it.totalUnits == 120.0 }
        assertEquals(CostStatus.CREDIT_BASED, turboDay1.status)

        val zeroDay = records.first { it.model == "gen4_turbo" && it.totalUnits == 0.0 }
        assertEquals(0.0, zeroDay.totalUnits!!, 0.0)
    }


    @Test
    fun usage_parses_the_current_documented_results_usedCredits_shape() {
        val body = """
            {
              "results": [
                {
                  "date": "2026-08-01",
                  "usedCredits": [
                    {"model": "gen4.5", "amount": 25},
                    {"model": "gen4_turbo", "amount": 50}
                  ]
                }
              ],
              "models": ["gen4.5", "gen4_turbo"]
            }
        """.trimIndent()

        val records = RunwayParsing.usage(body, "RUNWAY")

        assertEquals(2, records.size)
        assertEquals("gen4.5", records[0].model)
        assertEquals(25.0, records[0].totalUnits!!, 0.0)
        assertEquals("gen4_turbo", records[1].model)
        assertEquals(50.0, records[1].totalUnits!!, 0.0)
        assertTrue(records.all { it.status == CostStatus.CREDIT_BASED })
    }

    @Test
    fun usage_handles_empty_data_without_throwing() {
        assertTrue(RunwayParsing.usage("""{"data":[]}""", "RUNWAY").isEmpty())
    }

    @Test
    fun usage_skips_entries_with_no_recognizable_credit_field_rather_than_guessing() {
        val body = """{"data":[{"date":"2026-08-01","model":"gen4_turbo"}]}"""
        assertTrue(RunwayParsing.usage(body, "RUNWAY").isEmpty())
    }

    @Test
    fun usage_falls_back_across_alternate_field_name_variants() {
        val body = """
            {
              "usage": [
                { "day": "2026-08-05", "modelId": "gen4_5", "creditsUsed": 42 }
              ]
            }
        """.trimIndent()
        val records = RunwayParsing.usage(body, "RUNWAY")

        assertEquals(1, records.size)
        assertEquals("gen4_5", records.first().model)
        assertEquals(42.0, records.first().totalUnits!!, 0.0)
    }
}
