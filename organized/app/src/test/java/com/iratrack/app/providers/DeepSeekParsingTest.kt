package com.iratrack.app.providers

import com.iratrack.app.data.CostStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepSeekParsingTest {

    // Shape taken from DeepSeek's published GET /user/balance example response.
    private val fixture = """
        {
          "is_available": true,
          "balance_infos": [
            { "currency": "CNY", "total_balance": "110.00", "granted_balance": "10.00", "topped_up_balance": "100.00" }
          ]
        }
    """.trimIndent()

    @Test
    fun balance_is_recorded_as_a_snapshot_not_a_cost() {
        val records = DeepSeekParsing.balance(fixture, "DEEPSEEK", 5000L)

        assertEquals(1, records.size)
        val record = records[0]
        assertNull("A balance snapshot must never be reported as a cost", record.costUsd)
        assertEquals(CostStatus.UNAVAILABLE, record.status)
        assertEquals(110.0, record.totalUnits!!, 0.0001)
        assertEquals("CNY balance", record.unitLabel)
        assertEquals(5000L, record.timestamp)
    }

    @Test
    fun balance_handles_multiple_currencies_as_separate_records() {
        val multi = """
            {
              "is_available": true,
              "balance_infos": [
                { "currency": "USD", "total_balance": "12.34", "granted_balance": "0", "topped_up_balance": "12.34" },
                { "currency": "CNY", "total_balance": "0", "granted_balance": "0", "topped_up_balance": "0" }
              ]
            }
        """.trimIndent()

        val records = DeepSeekParsing.balance(multi, "DEEPSEEK", 1L)
        assertEquals(2, records.size)
        assertTrue(records.any { it.unitLabel == "USD balance" && it.totalUnits == 12.34 })
    }

    @Test
    fun balance_skips_entries_with_unparseable_total() {
        val bad = """{"is_available":false,"balance_infos":[{"currency":"USD","total_balance":"n/a"}]}"""
        assertTrue(DeepSeekParsing.balance(bad, "DEEPSEEK", 1L).isEmpty())
    }
}
