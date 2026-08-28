package com.iratrack.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ProviderId(val label: String) {
    OPENAI("OpenAI"),
    ANTHROPIC("Anthropic"),
    GEMINI("Google Gemini"),
    GROQ("Groq"),
    DEEPSEEK("DeepSeek"),
    XAI("xAI / Grok"),
    RUNWAY("Runway")
}

enum class CostStatus {
    REPORTED,
    ESTIMATED,
    USAGE_ONLY,
    CREDIT_BASED,
    UNAVAILABLE
}

enum class UnitKind {
    TOKENS,
    REQUESTS,
    CREDITS,
    SECONDS,
    IMAGES,
    CHARACTERS,
    OTHER
}

@Entity(
    tableName = "usage_records",
    // sourceId is how each adapter names a specific provider-reported bucket/line item
    // (e.g. "openai-cost-<start_time>-<index>"). Making it a unique index is what lets
    // OnConflictStrategy.IGNORE on insertAll() actually deduplicate re-synchronized
    // records instead of just relying on the autoincrement id, which is always unique
    // and would otherwise let every re-sync duplicate the whole history. Rows with a
    // null sourceId (there shouldn't be any from a real adapter) are never deduplicated,
    // since SQLite treats every NULL in a unique index as distinct.
    indices = [Index(value = ["sourceId"], unique = true)]
)
data class UsageRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val provider: String,
    val timestamp: Long,
    val costUsd: Double?,
    val inputUnits: Double?,
    val outputUnits: Double?,
    val totalUnits: Double?,
    val unitKind: UnitKind,
    val unitLabel: String,
    val requests: Long?,
    val model: String?,
    val status: CostStatus,
    val sourceId: String? = null
)

@Entity(tableName = "provider_state")
data class ProviderState(
    @PrimaryKey val provider: String,
    val enabled: Boolean = false,
    val lastSync: Long? = null,
    val lastStatus: String = "Never synchronized",
    val success: Boolean = false
)

data class ProviderSummary(
    val provider: ProviderId,
    val cost: Double,
    val requests: Long,
    val units: Double,
    val unitLabel: String,
    val status: CostStatus
)

data class SyncResult(
    val records: List<UsageRecord>,
    val statusMessage: String,
    val success: Boolean
)

data class Capability(
    val provider: ProviderId,
    val usage: Boolean,
    val billing: String,
    val estimation: Boolean,
    val models: Boolean,
    val notes: String
)
