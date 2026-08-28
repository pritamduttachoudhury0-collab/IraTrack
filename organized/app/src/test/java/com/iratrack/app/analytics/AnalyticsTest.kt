package com.iratrack.app.analytics

import com.iratrack.app.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsTest {
    @Test
    fun totals_ignore_usage_only_cost_nulls() {
        val rows = listOf(
            UsageRecord(
                provider = "OPENAI",
                timestamp = 1000,
                costUsd = 2.5,
                inputUnits = null,
                outputUnits = null,
                totalUnits = null,
                unitKind = UnitKind.OTHER,
                unitLabel = "USD",
                requests = null,
                model = null,
                status = CostStatus.REPORTED
            ),
            UsageRecord(
                provider = "OPENAI",
                timestamp = 1000,
                costUsd = null,
                inputUnits = 100.0,
                outputUnits = 20.0,
                totalUnits = 120.0,
                unitKind = UnitKind.TOKENS,
                unitLabel = "tokens",
                requests = 1,
                model = "test",
                status = CostStatus.USAGE_ONLY
            )
        )

        assertEquals(2.5, LocalAnalytics.total(rows), 0.0001)
        assertEquals(120.0, LocalAnalytics.tokens(rows), 0.0001)
        assertEquals(1L, LocalAnalytics.requests(rows))
    }

    private fun record(provider: String, daysAgo: Int, cost: Double): UsageRecord {
        val now = System.currentTimeMillis()
        return UsageRecord(
            provider = provider,
            timestamp = now - daysAgo * 86_400_000L,
            costUsd = cost,
            inputUnits = null,
            outputUnits = null,
            totalUnits = null,
            unitKind = UnitKind.OTHER,
            unitLabel = "USD",
            requests = null,
            model = null,
            status = CostStatus.REPORTED
        )
    }

    @Test
    fun anomalies_flag_a_provider_whose_recent_spend_far_exceeds_its_baseline() {
        // Baseline: ~$1/day for 6 days (day 2..7 ago). Recent (last 24h): $10 -> a real spike.
        val rows = (2..7).map { record("OPENAI", it, 1.0) } + record("OPENAI", 0, 10.0)

        val anomalies = LocalAnalytics.anomalies(rows)

        assertEquals(1, anomalies.size)
        assertEquals("OPENAI", anomalies[0].provider)
        assertTrue(anomalies[0].percentageAboveBaseline > 100)
    }

    @Test
    fun anomalies_do_not_flag_normal_variation() {
        // Recent spend only slightly above baseline (not more than double) is not an anomaly.
        val rows = (2..7).map { record("OPENAI", it, 1.0) } + record("OPENAI", 0, 1.2)

        assertTrue(LocalAnalytics.anomalies(rows).isEmpty())
    }

    @Test
    fun anomalies_require_a_baseline_before_flagging_anything() {
        // No history older than 24h -> nothing to compare against, so no anomaly is claimed.
        val rows = listOf(record("OPENAI", 0, 500.0))

        assertTrue(LocalAnalytics.anomalies(rows).isEmpty())
    }

    @Test
    fun anomalies_ignore_records_with_no_cost_status() {
        // Usage-only rows (costUsd == null) must never be treated as a cost spike.
        val now = System.currentTimeMillis()
        val rows = (2..7).map { record("OPENAI", it, 1.0) } + UsageRecord(
            provider = "OPENAI",
            timestamp = now,
            costUsd = null,
            inputUnits = 999_999.0,
            outputUnits = null,
            totalUnits = 999_999.0,
            unitKind = UnitKind.TOKENS,
            unitLabel = "tokens",
            requests = null,
            model = null,
            status = CostStatus.USAGE_ONLY
        )

        assertTrue(LocalAnalytics.anomalies(rows).isEmpty())
    }

    @Test
    fun reportedCost_and_estimatedCost_never_mix_statuses() {
        val rows = listOf(
            record("OPENAI", 0, 10.0), // REPORTED
            UsageRecord(
                provider = "OPENAI",
                timestamp = System.currentTimeMillis(),
                costUsd = 3.0,
                inputUnits = null,
                outputUnits = null,
                totalUnits = null,
                unitKind = UnitKind.OTHER,
                unitLabel = "USD",
                requests = null,
                model = null,
                status = CostStatus.ESTIMATED
            )
        )

        assertEquals(10.0, LocalAnalytics.reportedCost(rows), 0.0001)
        assertEquals(3.0, LocalAnalytics.estimatedCost(rows), 0.0001)
    }
}
