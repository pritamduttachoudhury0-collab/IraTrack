package com.iratrack.app.analytics

import com.iratrack.app.data.CostStatus
import com.iratrack.app.data.UsageRecord

data class Anomaly(
    val provider: String,
    val percentageAboveBaseline: Int,
    val recentCost: Double,
    val baselineCost: Double
)

object LocalAnalytics {
    fun anomalies(records: List<UsageRecord>): List<Anomaly> {
        val now = System.currentTimeMillis()
        val recentStart = now - 86_400_000L
        val baselineStart = now - 7L * 86_400_000L

        return records.groupBy { it.provider }.mapNotNull { (provider, rows) ->
            val recent = rows
                .filter { it.timestamp >= recentStart && it.costUsd != null }
                .sumOf { it.costUsd ?: 0.0 }

            val baselineRows = rows.filter {
                it.timestamp in baselineStart until recentStart && it.costUsd != null
            }

            if (baselineRows.isEmpty()) return@mapNotNull null

            val baseline = baselineRows.sumOf { it.costUsd ?: 0.0 } / 6.0
            if (baseline <= 0.0 || recent <= baseline * 2.0) return@mapNotNull null

            Anomaly(
                provider,
                (((recent / baseline) - 1.0) * 100.0).toInt(),
                recent,
                baseline
            )
        }
    }

    fun total(records: List<UsageRecord>, since: Long? = null): Double =
        records.asSequence()
            .filter { since == null || it.timestamp >= since }
            .sumOf { it.costUsd ?: 0.0 }

    fun tokens(records: List<UsageRecord>, since: Long? = null): Double =
        records.asSequence()
            .filter { since == null || it.timestamp >= since }
            .sumOf { it.totalUnits ?: 0.0 }

    fun requests(records: List<UsageRecord>, since: Long? = null): Long =
        records.asSequence()
            .filter { since == null || it.timestamp >= since }
            .sumOf { it.requests ?: 0L }

    fun reportedCost(records: List<UsageRecord>): Double =
        records.filter { it.status == CostStatus.REPORTED }.sumOf { it.costUsd ?: 0.0 }

    fun estimatedCost(records: List<UsageRecord>): Double =
        records.filter { it.status == CostStatus.ESTIMATED }.sumOf { it.costUsd ?: 0.0 }
}
