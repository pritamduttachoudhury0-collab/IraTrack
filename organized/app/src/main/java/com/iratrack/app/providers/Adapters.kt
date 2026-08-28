package com.iratrack.app.providers

import com.iratrack.app.data.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.temporal.ChronoUnit

private val http = OkHttpClient()

private fun last30DaysStart(): Long =
    Instant.now().minus(30, ChronoUnit.DAYS).epochSecond

internal fun jsonDouble(obj: JSONObject?, key: String): Double? {
    if (obj == null || !obj.has(key) || obj.isNull(key)) return null
    val raw = obj.get(key)
    return when (raw) {
        is Number -> raw.toDouble()
        is String -> raw.toDoubleOrNull()
        else -> null
    }
}

internal fun jsonLong(obj: JSONObject?, key: String): Long? {
    if (obj == null || !obj.has(key) || obj.isNull(key)) return null
    val raw = obj.get(key)
    return when (raw) {
        is Number -> raw.toLong()
        is String -> raw.toLongOrNull()
        else -> null
    }
}

internal fun parseUnix(value: Any?): Long =
    when (value) {
        is Number -> value.toLong() * if (value.toLong() < 10_000_000_000L) 1000 else 1
        is String -> runCatching { Instant.parse(value).toEpochMilli() }.getOrElse { value.toLongOrNull()?.times(1000) ?: System.currentTimeMillis() }
        else -> System.currentTimeMillis()
    }

/**
 * Pure, network-free parsing for the OpenAI Organization Costs and Completions
 * Usage responses. Split out from [OpenAiAdapter] so the actual response-shape
 * handling can be unit tested with fixture JSON instead of only being exercised
 * by a live HTTP call.
 */
internal object OpenAiParsing {
    fun costs(body: String, providerName: String): List<UsageRecord> {
        val json = JSONObject(body)
        val buckets = json.optJSONArray("data") ?: JSONArray()
        val records = mutableListOf<UsageRecord>()

        for (i in 0 until buckets.length()) {
            val bucket = buckets.optJSONObject(i) ?: continue
            val results = bucket.optJSONArray("results") ?: continue
            for (j in 0 until results.length()) {
                val item = results.optJSONObject(j) ?: continue
                val amount = item.optJSONObject("amount")
                val cost = jsonDouble(amount, "value") ?: continue
                records += UsageRecord(
                    provider = providerName,
                    timestamp = parseUnix(bucket.opt("start_time")),
                    costUsd = cost,
                    inputUnits = null,
                    outputUnits = null,
                    totalUnits = null,
                    unitKind = UnitKind.OTHER,
                    unitLabel = "USD",
                    requests = null,
                    model = item.optString("line_item").takeIf { it.isNotBlank() },
                    status = CostStatus.REPORTED,
                    sourceId = "openai-cost-${bucket.opt("start_time")}-${item.optString("line_item").ifBlank { j.toString() }}"
                )
            }
        }
        return records
    }

    fun usage(body: String, providerName: String): List<UsageRecord> {
        val json = JSONObject(body)
        val buckets = json.optJSONArray("data") ?: JSONArray()
        val records = mutableListOf<UsageRecord>()

        for (i in 0 until buckets.length()) {
            val bucket = buckets.optJSONObject(i) ?: continue
            val results = bucket.optJSONArray("results") ?: continue

            for (j in 0 until results.length()) {
                val item = results.optJSONObject(j) ?: continue
                val input = jsonLong(item, "input_tokens")?.toDouble()
                val output = jsonLong(item, "output_tokens")?.toDouble()
                val total = (input ?: 0.0) + (output ?: 0.0)
                val requests = jsonLong(item, "num_model_requests")

                if (input != null || output != null || requests != null) {
                    records += UsageRecord(
                        provider = providerName,
                        timestamp = parseUnix(bucket.opt("start_time")),
                        costUsd = null,
                        inputUnits = input,
                        outputUnits = output,
                        totalUnits = total.takeIf { it > 0 },
                        unitKind = UnitKind.TOKENS,
                        unitLabel = "tokens",
                        requests = requests,
                        model = item.optString("model").takeIf { it.isNotBlank() },
                        status = CostStatus.USAGE_ONLY,
                        sourceId = "openai-usage-${bucket.opt("start_time")}-${item.optString("model").ifBlank { j.toString() }}"
                    )
                }
            }
        }
        return records
    }
}

/**
 * OpenAI Organization Costs + Completions Usage.
 *
 * These are organization/admin endpoints, so the stored credential must have
 * the appropriate read permissions. We do not fall back to undocumented
 * dashboard endpoints.
 */
class OpenAiAdapter : ProviderAdapter {
    override val provider = ProviderId.OPENAI

    override suspend fun sync(apiCredential: String): SyncResult {
        val start = last30DaysStart()
        val end = Instant.now().epochSecond

        val costsResult = fetchCosts(apiCredential, start, end)
        val usageResult = fetchCompletionsUsage(apiCredential, start, end)

        if (!costsResult.success && !usageResult.success) {
            return SyncResult(
                emptyList(),
                "OpenAI sync failed. Costs: ${costsResult.statusMessage} Usage: ${usageResult.statusMessage}",
                false
            )
        }

        val records = costsResult.records + usageResult.records
        val status = buildString {
            append("OpenAI synchronized ")
            append(costsResult.records.size)
            append(" cost buckets and ")
            append(usageResult.records.size)
            append(" usage buckets.")
            if (!costsResult.success) append(" Cost data unavailable: ${costsResult.statusMessage}")
            if (!usageResult.success) append(" Usage data unavailable: ${usageResult.statusMessage}")
        }

        return SyncResult(records, status, costsResult.success || usageResult.success)
    }

    private fun fetchCosts(key: String, start: Long, end: Long): SyncResult {
        return runCatching {
            val url = "https://api.openai.com/v1/organization/costs".toHttpUrl().newBuilder()
                .addQueryParameter("start_time", start.toString())
                .addQueryParameter("end_time", end.toString())
                .addQueryParameter("bucket_width", "1d")
                .addQueryParameter("limit", "180")
                .addQueryParameter("group_by", "line_item")
                .build()

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $key")
                .header("Accept", "application/json")
                .build()

            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return SyncResult(emptyList(), "HTTP ${response.code}", false)
                }
                val body = response.body?.string().orEmpty()
                SyncResult(OpenAiParsing.costs(body, provider.name), "OK", true)
            }
        }.getOrElse {
            SyncResult(emptyList(), it.message ?: "Unknown error", false)
        }
    }

    private fun fetchCompletionsUsage(key: String, start: Long, end: Long): SyncResult {
        return runCatching {
            val url = "https://api.openai.com/v1/organization/usage/completions".toHttpUrl().newBuilder()
                .addQueryParameter("start_time", start.toString())
                .addQueryParameter("end_time", end.toString())
                .addQueryParameter("bucket_width", "1d")
                .addQueryParameter("limit", "180")
                .addQueryParameter("group_by", "model")
                .build()

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $key")
                .header("Accept", "application/json")
                .build()

            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return SyncResult(emptyList(), "HTTP ${response.code}", false)
                }
                val body = response.body?.string().orEmpty()
                SyncResult(OpenAiParsing.usage(body, provider.name), "OK", true)
            }
        }.getOrElse {
            SyncResult(emptyList(), it.message ?: "Unknown error", false)
        }
    }
}

/**
 * Pure, network-free parsing for the Anthropic Admin cost_report and
 * usage_report/messages responses. Split out from [AnthropicAdapter] for the
 * same reason as [OpenAiParsing].
 */
internal object AnthropicParsing {
    /** Anthropic documents cost `amount` as a decimal string in minor currency units (cents). */
    fun costs(body: String, providerName: String): List<UsageRecord> {
        val json = JSONObject(body)
        val buckets = json.optJSONArray("data") ?: JSONArray()
        val records = mutableListOf<UsageRecord>()

        for (i in 0 until buckets.length()) {
            val bucket = buckets.optJSONObject(i) ?: continue
            val results = bucket.optJSONArray("results") ?: continue

            for (j in 0 until results.length()) {
                val item = results.optJSONObject(j) ?: continue
                val rawAmount = item.optString("amount", "")
                val costUsd = rawAmount.toDoubleOrNull()?.div(100.0) ?: continue

                records += UsageRecord(
                    provider = providerName,
                    timestamp = parseUnix(bucket.opt("starting_at")),
                    costUsd = costUsd,
                    inputUnits = null,
                    outputUnits = null,
                    totalUnits = null,
                    unitKind = UnitKind.OTHER,
                    unitLabel = item.optString("currency", "USD"),
                    requests = null,
                    model = item.optString("description").takeIf { it.isNotBlank() },
                    status = CostStatus.REPORTED,
                    sourceId = "anthropic-cost-${bucket.opt("starting_at")}-${item.optString("description").ifBlank { item.optString("line_item").ifBlank { j.toString() } }}"
                )
            }
        }
        return records
    }

    fun usage(body: String, providerName: String): List<UsageRecord> {
        val json = JSONObject(body)
        val buckets = json.optJSONArray("data") ?: JSONArray()
        val records = mutableListOf<UsageRecord>()

        for (i in 0 until buckets.length()) {
            val bucket = buckets.optJSONObject(i) ?: continue
            val results = bucket.optJSONArray("results") ?: continue

            for (j in 0 until results.length()) {
                val item = results.optJSONObject(j) ?: continue
                val input = jsonLong(item, "input_tokens")?.toDouble()
                val output = jsonLong(item, "output_tokens")?.toDouble()
                val requests = jsonLong(item, "request_count")
                    ?: jsonLong(item, "num_requests")

                if (input != null || output != null || requests != null) {
                    records += UsageRecord(
                        provider = providerName,
                        timestamp = parseUnix(bucket.opt("starting_at")),
                        costUsd = null,
                        inputUnits = input,
                        outputUnits = output,
                        totalUnits = ((input ?: 0.0) + (output ?: 0.0)).takeIf { it > 0 },
                        unitKind = UnitKind.TOKENS,
                        unitLabel = "tokens",
                        requests = requests,
                        model = item.optString("model").takeIf { it.isNotBlank() },
                        status = CostStatus.USAGE_ONLY,
                        sourceId = "anthropic-usage-${bucket.opt("starting_at")}-${item.optString("model").ifBlank { j.toString() }}"
                    )
                }
            }
        }
        return records
    }
}

/**
 * Anthropic Organization Admin API.
 *
 * Cost reports are authoritative provider-reported billing data. The Admin
 * Usage API can provide message usage; cost reports are kept separate so the
 * app never turns token counts into an official-looking bill.
 */
class AnthropicAdapter : ProviderAdapter {
    override val provider = ProviderId.ANTHROPIC

    override suspend fun sync(apiCredential: String): SyncResult {
        val costs = fetchCosts(apiCredential)
        val usage = fetchUsage(apiCredential)

        val records = costs.records + usage.records
        return SyncResult(
            records,
            "Anthropic synchronized ${costs.records.size} cost records and ${usage.records.size} usage records." +
                if (!costs.success) " Cost: ${costs.statusMessage}" else "",
            costs.success || usage.success
        )
    }

    private fun fetchCosts(key: String): SyncResult = runCatching {
        val start = Instant.now().minus(30, ChronoUnit.DAYS).toString()
        val end = Instant.now().toString()

        val url = "https://api.anthropic.com/v1/organizations/cost_report".toHttpUrl()
            .newBuilder()
            .addQueryParameter("starting_at", start)
            .addQueryParameter("ending_at", end)
            .addQueryParameter("bucket_width", "1d")
            .addQueryParameter("group_by[]", "description")
            .addQueryParameter("limit", "31")
            .build()

        val request = Request.Builder()
            .url(url)
            .header("x-api-key", key)
            .header("anthropic-version", "2023-06-01")
            .header("Accept", "application/json")
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return SyncResult(emptyList(), "HTTP ${response.code}", false)
            }
            val body = response.body?.string().orEmpty()
            SyncResult(AnthropicParsing.costs(body, provider.name), "OK", true)
        }
    }.getOrElse {
        SyncResult(emptyList(), it.message ?: "Unknown error", false)
    }

    private fun fetchUsage(key: String): SyncResult = runCatching {
        val start = Instant.now().minus(30, ChronoUnit.DAYS).toString()
        val end = Instant.now().toString()

        val url = "https://api.anthropic.com/v1/organizations/usage_report/messages".toHttpUrl()
            .newBuilder()
            .addQueryParameter("starting_at", start)
            .addQueryParameter("ending_at", end)
            .addQueryParameter("bucket_width", "1d")
            .addQueryParameter("group_by[]", "model")
            .addQueryParameter("limit", "31")
            .build()

        val request = Request.Builder()
            .url(url)
            .header("x-api-key", key)
            .header("anthropic-version", "2023-06-01")
            .header("Accept", "application/json")
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return SyncResult(emptyList(), "HTTP ${response.code}", false)
            }
            val body = response.body?.string().orEmpty()
            SyncResult(AnthropicParsing.usage(body, provider.name), "OK", true)
        }
    }.getOrElse {
        SyncResult(emptyList(), it.message ?: "Unknown error", false)
    }
}

/**
 * DeepSeek Get User Balance (GET /user/balance).
 *
 * This is DeepSeek's only official, documented account-financial endpoint. It is a
 * point-in-time balance snapshot (is_available + balance per currency), not a
 * historical usage/cost time series -- DeepSeek does not publish one. We therefore
 * record it honestly as an UNAVAILABLE-cost balance observation rather than a spend
 * figure. Two consecutive snapshots could in principle be subtracted to estimate
 * spend in between, but a balance can also rise from a manual top-up, and getting
 * that distinction wrong would show a fabricated negative "spend" -- so that
 * estimation is intentionally not implemented here. See PROVIDER_CAPABILITY_POLICY.md.
 */
internal object DeepSeekParsing {
    fun balance(body: String, providerName: String, syncTimestamp: Long): List<UsageRecord> {
        val json = JSONObject(body)
        val infos = json.optJSONArray("balance_infos") ?: JSONArray()
        val records = mutableListOf<UsageRecord>()

        for (i in 0 until infos.length()) {
            val info = infos.optJSONObject(i) ?: continue
            val total = info.optString("total_balance", "").toDoubleOrNull() ?: continue
            val currency = info.optString("currency", "USD")

            records += UsageRecord(
                provider = providerName,
                timestamp = syncTimestamp,
                costUsd = null,
                inputUnits = null,
                outputUnits = null,
                totalUnits = total,
                unitKind = UnitKind.OTHER,
                unitLabel = "$currency balance",
                requests = null,
                model = "Account balance",
                status = CostStatus.UNAVAILABLE,
                sourceId = "deepseek-balance-$currency-$syncTimestamp"
            )
        }
        return records
    }
}

/**
 * [baseUrl] defaults to the real DeepSeek host and only exists so tests can
 * point this adapter at a local mock server to exercise the actual sync()
 * network/error-handling path (not just [DeepSeekParsing]) without any
 * change to real runtime behavior -- [Adapters] always constructs this with
 * the default.
 */
class DeepSeekAdapter(
    private val baseUrl: String = "https://api.deepseek.com"
) : ProviderAdapter {
    override val provider = ProviderId.DEEPSEEK

    override suspend fun sync(apiCredential: String): SyncResult = runCatching {
        val request = Request.Builder()
            .url("$baseUrl/user/balance")
            .header("Authorization", "Bearer $apiCredential")
            .header("Accept", "application/json")
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return SyncResult(
                    emptyList(),
                    "DeepSeek: ${ProviderErrors.forHttpStatus(response.code, "DeepSeek", "API key")}",
                    false
                )
            }
            val body = response.body?.string().orEmpty()
            val records = DeepSeekParsing.balance(body, provider.name, System.currentTimeMillis())
            SyncResult(
                records,
                if (records.isEmpty())
                    "DeepSeek: balance endpoint returned no parseable balance. DeepSeek has no historical cost/usage API; billing beyond current balance is unavailable."
                else
                    "DeepSeek: current balance retrieved (${records.size} currency${if (records.size == 1) "" else "ies"}). " +
                        "DeepSeek has no historical cost/usage API, so spend-over-time is unavailable, not estimated.",
                records.isNotEmpty()
            )
        }
    }.getOrElse {
        SyncResult(emptyList(), "DeepSeek: ${ProviderErrors.forException(it, "DeepSeek")}", false)
    }
}

/**
 * xAI Management API: historical usage (real per-model cost, USD) plus prepaid
 * credit balance. This is a genuinely different API from the xai-... inference key
 * used for chat completions -- it lives at management-api.x.ai, requires a
 * Management API key created in the xAI Console, and is scoped to a team.
 *
 * The adapter first resolves the team ID from the credential itself (management
 * keys are bound to exactly one team) via /auth/management-keys/validation, then
 * requests a daily USD time series grouped by model description from
 * /v1/billing/teams/{team_id}/usage.
 */
internal object XaiParsing {
    fun teamId(body: String): String? {
        val json = JSONObject(body)
        return json.optString("teamId", json.optString("scopeId", "")).takeIf { it.isNotBlank() }
    }

    fun usage(body: String, providerName: String): List<UsageRecord> {
        val json = JSONObject(body)
        val series = json.optJSONArray("timeSeries") ?: JSONArray()
        val records = mutableListOf<UsageRecord>()

        for (s in 0 until series.length()) {
            val entry = series.optJSONObject(s) ?: continue
            val labels = entry.optJSONArray("groupLabels")
            val label = if (labels != null && labels.length() > 0) labels.optString(0) else null
            val points = entry.optJSONArray("dataPoints") ?: continue

            for (p in 0 until points.length()) {
                val point = points.optJSONObject(p) ?: continue
                val values = point.optJSONArray("values") ?: continue
                if (values.length() == 0) continue
                val amount = values.optDouble(0, Double.NaN)
                // Keep $0 days too -- that's xAI actually reporting no spend that day,
                // which is real information, not a gap. Only skip unparseable values.
                if (amount.isNaN()) continue

                records += UsageRecord(
                    provider = providerName,
                    timestamp = parseUnix(point.opt("timestamp")),
                    costUsd = amount,
                    inputUnits = null,
                    outputUnits = null,
                    totalUnits = null,
                    unitKind = UnitKind.OTHER,
                    unitLabel = "USD",
                    requests = null,
                    model = label,
                    status = CostStatus.REPORTED,
                    sourceId = "xai-usage-${point.opt("timestamp")}-${label ?: s}"
                )
            }
        }
        return records
    }
}

/**
 * [baseUrl] defaults to the real xAI Management API host and only exists so
 * tests can point this adapter at a local mock server to exercise the actual
 * sync() network/error-handling path (not just [XaiParsing]) without any
 * change to real runtime behavior -- [Adapters] always constructs this with
 * the default.
 */
class XaiAdapter(
    private val baseUrl: String = "https://management-api.x.ai"
) : ProviderAdapter {
    override val provider = ProviderId.XAI

    override suspend fun sync(apiCredential: String): SyncResult {
        val teamResult = runCatching {
            val request = Request.Builder()
                .url("$baseUrl/auth/management-keys/validation")
                .header("Authorization", "Bearer $apiCredential")
                .header("Accept", "application/json")
                .build()

            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val detail = when (response.code) {
                        401 -> "xAI rejected the Management API key as invalid. Make sure you copied the full key from console.x.ai → Settings → Management Keys -- not your xai-... inference key, which the Management API does not accept."
                        403 -> "xAI accepted the key but denied this request. The account that created it needs \"Management Keys Read + Write\" permission; check console.x.ai → Settings → Users."
                        else -> ProviderErrors.forHttpStatus(response.code, "xAI", "Management API key")
                    }
                    return SyncResult(emptyList(), "xAI: could not validate the Management API key. $detail", false)
                }
                XaiParsing.teamId(response.body?.string().orEmpty())
            }
        }.getOrElse {
            return SyncResult(emptyList(), "xAI: ${ProviderErrors.forException(it, "xAI")}", false)
        }

        val teamId = teamResult ?: return SyncResult(
            emptyList(),
            "xAI: the Management API key did not resolve to a team/organization ID. If your account belongs to " +
                "multiple teams, create the Management API key from inside the specific team you want IraTrack to track.",
            false
        )

        return runCatching {
            val end = Instant.now()
            val start = end.minus(30, ChronoUnit.DAYS)
            val requestBody = JSONObject().apply {
                put("analyticsRequest", JSONObject().apply {
                    put("timeRange", JSONObject().apply {
                        put("startTime", start.toString().replace("T", " ").replace("Z", ""))
                        put("endTime", end.toString().replace("T", " ").replace("Z", ""))
                        put("timezone", "Etc/GMT")
                    })
                    put("timeUnit", "TIME_UNIT_DAY")
                    put("values", JSONArray().put(JSONObject().apply {
                        put("name", "usd")
                        put("aggregation", "AGGREGATION_SUM")
                    }))
                    put("groupBy", JSONArray().put("description"))
                    put("filters", JSONArray())
                })
            }

            val request = Request.Builder()
                .url("$baseUrl/v1/billing/teams/$teamId/usage")
                .header("Authorization", "Bearer $apiCredential")
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .post(
                    requestBody.toString()
                        .toRequestBody("application/json; charset=utf-8".toMediaType())
                )
                .build()

            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val detail = when (response.code) {
                        403 -> "The Management API key doesn't have billing-read access for this team. Check its permissions at console.x.ai → Settings → Management Keys."
                        404 -> "xAI couldn't find billing data for this team. The team ID resolved from your key may no longer be valid."
                        else -> ProviderErrors.forHttpStatus(response.code, "xAI", "Management API key")
                    }
                    return SyncResult(emptyList(), "xAI: $detail", false)
                }
                val records = XaiParsing.usage(response.body?.string().orEmpty(), provider.name)
                SyncResult(
                    records,
                    "xAI synchronized ${records.size} reported daily cost records via the Management API.",
                    true
                )
            }
        }.getOrElse {
            SyncResult(emptyList(), "xAI: ${ProviderErrors.forException(it, "xAI")}", false)
        }
    }
}

/**
 * Parses a date-only string (`"2026-08-01"`) as UTC midnight. Runway's usage
 * endpoint buckets by calendar day rather than a timestamp, so [parseUnix]'s
 * `Instant.parse` (which requires a full offset/`Z`) doesn't apply; this
 * falls back to [parseUnix] for anything that isn't a bare date, so a
 * timestamp-shaped value from the API is still handled correctly.
 */
private fun parseDateOrInstant(value: Any?): Long =
    when (value) {
        is String -> runCatching {
            java.time.LocalDate.parse(value).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        }.getOrElse { parseUnix(value) }
        else -> parseUnix(value)
    }

/**
 * Pure, network-free parsing for the Runway Organization API
 * (GET /v1/organization, POST /v1/organization/usage).
 *
 * Runway meters everything in credits ($0.01/credit at the time of writing,
 * per Runway's published API pricing), not USD directly, so both the balance
 * and the per-model usage are recorded with [CostStatus.CREDIT_BASED] and
 * [UnitKind.CREDITS] rather than converted to a dollar figure -- per
 * PROVIDER_CAPABILITY_POLICY.md, a converted number would look like an
 * authoritative USD cost when it's actually IraTrack applying a rate that
 * can change or vary by contract, which is exactly the kind of estimate the
 * app declines to label as reported.
 *
 * GET /v1/organization's `creditBalance` field is confirmed directly from
 * Runway's own published setup snippets. POST /v1/organization/usage is
 * documented (Organization API reference, "Query credit usage": daily credit
 * usage grouped by model, up to 90 days per request). The current documented
 * response is `results[].usedCredits[].{model,amount}`. A legacy flat-array
 * fallback remains for older fixtures/compatibility, but the live shape is
 * parsed first.
 */
internal object RunwayParsing {
    fun organization(body: String, providerName: String, syncTimestamp: Long): UsageRecord? {
        val json = JSONObject(body)
        val credits = jsonDouble(json, "creditBalance") ?: jsonDouble(json, "credit_balance") ?: return null

        return UsageRecord(
            provider = providerName,
            timestamp = syncTimestamp,
            costUsd = null,
            inputUnits = null,
            outputUnits = null,
            totalUnits = credits,
            unitKind = UnitKind.CREDITS,
            unitLabel = "credits",
            requests = null,
            model = "Account credit balance",
            status = CostStatus.CREDIT_BASED,
            sourceId = "runway-balance-$syncTimestamp"
        )
    }

    fun usage(body: String, providerName: String): List<UsageRecord> {
        val json = JSONObject(body)
        val results = json.optJSONArray("results")
        if (results != null) {
            val records = mutableListOf<UsageRecord>()
            for (i in 0 until results.length()) {
                val day = results.optJSONObject(i) ?: continue
                val dateValue = day.opt("date") ?: continue
                val usedCredits = day.optJSONArray("usedCredits") ?: JSONArray()
                for (j in 0 until usedCredits.length()) {
                    val item = usedCredits.optJSONObject(j) ?: continue
                    val credits = jsonDouble(item, "amount") ?: continue
                    val model = item.optString("model").takeIf { it.isNotBlank() }
                    records += UsageRecord(
                        provider = providerName,
                        timestamp = parseDateOrInstant(dateValue),
                        costUsd = null,
                        inputUnits = null,
                        outputUnits = null,
                        totalUnits = credits,
                        unitKind = UnitKind.CREDITS,
                        unitLabel = "credits",
                        requests = null,
                        model = model,
                        status = CostStatus.CREDIT_BASED,
                        sourceId = "runway-usage-${dateValue}-${model ?: j}"
                    )
                }
            }
            return records
        }

        // Backward-compatible fallback for older/test payloads that used a flat
        // data/usage array. The live 2024-11-06 Organization API uses the
        // results[].usedCredits[].amount shape above.
        val entries = json.optJSONArray("data") ?: json.optJSONArray("usage") ?: JSONArray()
        val records = mutableListOf<UsageRecord>()
        for (i in 0 until entries.length()) {
            val item = entries.optJSONObject(i) ?: continue
            val credits = jsonDouble(item, "credits")
                ?: jsonDouble(item, "creditsUsed")
                ?: jsonDouble(item, "totalCredits")
                ?: jsonDouble(item, "amount")
                ?: continue
            val model = item.optString("model").takeIf { it.isNotBlank() }
                ?: item.optString("modelId").takeIf { it.isNotBlank() }
                ?: item.optString("modelName").takeIf { it.isNotBlank() }
            val dateValue = item.opt("date") ?: item.opt("day") ?: item.opt("period") ?: item.opt("startDate")
            records += UsageRecord(
                provider = providerName,
                timestamp = parseDateOrInstant(dateValue),
                costUsd = null,
                inputUnits = null,
                outputUnits = null,
                totalUnits = credits,
                unitKind = UnitKind.CREDITS,
                unitLabel = "credits",
                requests = null,
                model = model,
                status = CostStatus.CREDIT_BASED,
                sourceId = "runway-usage-${dateValue}-${model ?: i}"
            )
        }
        return records
    }
}

/**
 * Runway Organization API (dev.runwayml.com), a different product/host from
 * app.runwayml.com's consumer credits -- API credits are their own prepaid
 * pool. Combines the current credit balance (GET /v1/organization) with the
 * last 30 days of per-model daily credit usage (POST /v1/organization/usage,
 * which supports up to 90 days per request). Authenticated with the ordinary
 * organization-scoped RUNWAYML_API_SECRET key -- Runway does not have a
 * separate admin/management credential the way OpenAI, Anthropic, and xAI do.
 *
 * [baseUrl] defaults to the real Runway API host and only exists so tests can
 * point this adapter at a local mock server, matching [DeepSeekAdapter] and
 * [XaiAdapter].
 */
class RunwayAdapter(
    private val baseUrl: String = "https://api.dev.runwayml.com"
) : ProviderAdapter {
    override val provider = ProviderId.RUNWAY

    override suspend fun sync(apiCredential: String): SyncResult {
        val balanceResult = fetchOrganization(apiCredential)
        val usageResult = fetchUsage(apiCredential)

        if (!balanceResult.success && !usageResult.success) {
            return SyncResult(
                emptyList(),
                "Runway sync failed. Balance: ${balanceResult.statusMessage} Usage: ${usageResult.statusMessage}",
                false
            )
        }

        val records = balanceResult.records + usageResult.records
        val status = buildString {
            append("Runway synchronized ")
            if (balanceResult.records.isNotEmpty()) append("the current credit balance and ")
            append("${usageResult.records.size} daily per-model credit usage record(s) over the last 30 days. ")
            append("Runway meters usage in credits, not USD, so figures are shown as credits (credit-based), not converted to a dollar estimate.")
            if (!balanceResult.success) append(" Balance unavailable: ${balanceResult.statusMessage}")
            if (!usageResult.success) append(" Usage unavailable: ${usageResult.statusMessage}")
        }

        return SyncResult(records, status, balanceResult.success || usageResult.success)
    }

    private fun fetchOrganization(key: String): SyncResult = runCatching {
        val request = Request.Builder()
            .url("$baseUrl/v1/organization")
            .header("Authorization", "Bearer $key")
            .header("X-Runway-Version", RUNWAY_API_VERSION)
            .header("Accept", "application/json")
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return SyncResult(
                    emptyList(),
                    "Runway: ${ProviderErrors.forHttpStatus(response.code, "Runway", "API key")}",
                    false
                )
            }
            val body = response.body?.string().orEmpty()
            val record = RunwayParsing.organization(body, provider.name, System.currentTimeMillis())
            SyncResult(listOfNotNull(record), "OK", record != null)
        }
    }.getOrElse {
        SyncResult(emptyList(), "Runway: ${ProviderErrors.forException(it, "Runway")}", false)
    }

    private fun fetchUsage(key: String): SyncResult = runCatching {
        val end = java.time.LocalDate.now()
        val start = end.minusDays(30)
        val requestBody = JSONObject().apply {
            put("startDate", start.toString())
            put("beforeDate", end.toString())
        }

        val request = Request.Builder()
            .url("$baseUrl/v1/organization/usage")
            .header("Authorization", "Bearer $key")
            .header("X-Runway-Version", RUNWAY_API_VERSION)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return SyncResult(
                    emptyList(),
                    "Runway: ${ProviderErrors.forHttpStatus(response.code, "Runway", "API key")}",
                    false
                )
            }
            val body = response.body?.string().orEmpty()
            SyncResult(RunwayParsing.usage(body, provider.name), "OK", true)
        }
    }.getOrElse {
        SyncResult(emptyList(), "Runway: ${ProviderErrors.forException(it, "Runway")}", false)
    }

    private companion object {
        const val RUNWAY_API_VERSION = "2024-11-06"
    }
}

/*
 * The following providers remain first-class in the UI and common data model,
 * but their provider dashboards do not expose a single equivalent organization
 * billing API that IraTrack can safely call with only the provider credential.
 * We therefore report the limitation instead of scraping or inventing totals.
 *
 * Re-verified August 2026: no official, documented, read-only organization
 * usage/cost endpoint was found for Gemini (billing lives in Google Cloud
 * Billing, separate from the Gemini API key) or Groq (console-only billing;
 * API responses expose only per-request rate-limit headers, not historical
 * cost). DeepSeek, xAI, and Runway were previously scaffolded here too but
 * now have real adapters above/below -- DeepSeek exposes a balance snapshot
 * via GET /user/balance, xAI exposes real historical per-model cost via its
 * separate Management API, and Runway exposes credit balance + per-model
 * daily credit usage via its dev.runwayml.com Organization API. If any of
 * this changes, replace the relevant scaffold below with a real adapter
 * using the same pattern.
 */
class GeminiAdapter : OfficialApiAdapterScaffold(
    ProviderId.GEMINI,
    "Google documents API usage metadata on generation responses, while billing is handled through the Google Cloud/AI Studio billing system. IraTrack will not spend tokens merely to measure them and does not scrape the dashboard."
)

class GroqAdapter : OfficialApiAdapterScaffold(
    ProviderId.GROQ,
    "Groq exposes usage metrics in API responses and organization billing/usage in its console. IraTrack does not intercept your other applications' requests, so historical spend is unavailable through a documented read-only cross-account endpoint."
)

abstract class OfficialApiAdapterScaffold(
    override val provider: ProviderId,
    private val explanation: String
) : ProviderAdapter {
    override suspend fun sync(apiCredential: String): SyncResult =
        SyncResult(emptyList(), "${provider.label}: $explanation", false)
}

object Adapters {
    private val all: Map<ProviderId, ProviderAdapter> = listOf(
        OpenAiAdapter(),
        AnthropicAdapter(),
        GeminiAdapter(),
        GroqAdapter(),
        DeepSeekAdapter(),
        XaiAdapter(),
        RunwayAdapter()
    ).associateBy { it.provider }

    fun get(provider: ProviderId): ProviderAdapter =
        requireNotNull(all[provider])
}
