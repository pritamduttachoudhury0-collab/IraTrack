package com.iratrack.app.sync

/** Decides how WorkManager should classify a synchronization attempt. */
object SyncPolicy {
    enum class Decision { SUCCESS, RETRY, FAILURE }

    fun classify(successes: Int, failures: List<String>): Decision {
        if (failures.isEmpty()) return Decision.SUCCESS

        // A transient failure should be retried even when another provider succeeded;
        // successful records are deduplicated by sourceId, so retrying is safe.
        if (failures.any(::isTransientFailure)) return Decision.RETRY

        return if (successes > 0) Decision.SUCCESS else Decision.FAILURE
    }

    private fun isTransientFailure(message: String): Boolean {
        val m = message.lowercase()
        return listOf(
            "network error",
            "couldn't reach",
            "didn't respond in time",
            "did not respond in time",
            "temporarily unavailable",
            "rate-limited",
            "rate limited",
            "timed out",
            "timeout",
            "connection",
            "http 408",
            "http 429"
        ).any(m::contains) || Regex("\\b5\\d\\d\\b").containsMatchIn(m)
    }
}
