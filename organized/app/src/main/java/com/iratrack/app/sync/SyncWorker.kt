package com.iratrack.app.sync

import android.content.Context
import androidx.work.*
import com.iratrack.app.data.*
import com.iratrack.app.providers.Adapters
import com.iratrack.app.security.CredentialStore
import java.util.concurrent.TimeUnit

class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.get(applicationContext)
        val creds = CredentialStore(applicationContext)
        var successes = 0
        val failures = mutableListOf<String>()
        var credentialCount = 0

        ProviderId.entries.forEach { provider ->
            val key = creds.get(provider.name)
            if (key.isNullOrBlank()) return@forEach
            credentialCount++

            val result = runCatching { Adapters.get(provider).sync(key) }.getOrElse {
                SyncResult(emptyList(), "${provider.label}: ${it.message ?: "synchronization failed"}", false)
            }

            if (result.records.isNotEmpty()) {
                db.usageDao().insertAll(result.records)
            }

            // lastSync means the last successful synchronization, not merely an attempt.
            // Failed attempts remain visible through lastStatus/success without making
            // stale data look fresh.
            val previousState = db.providerStateDao().get(provider.name)
            val lastSync = if (result.success) System.currentTimeMillis() else previousState?.lastSync
            db.providerStateDao().upsert(
                ProviderState(
                    provider = provider.name,
                    enabled = true,
                    lastSync = lastSync,
                    lastStatus = result.statusMessage,
                    success = result.success
                )
            )

            if (result.success) successes++ else failures += result.statusMessage
        }

        // No configured credentials is not a WorkManager failure: there is simply
        // nothing to synchronize. If configured providers fail, classify transient
        // failures as retryable and permanent failures as failed work.
        if (credentialCount == 0) return Result.success()

        return when (SyncPolicy.classify(successes, failures)) {
            SyncPolicy.Decision.SUCCESS -> Result.success()
            SyncPolicy.Decision.RETRY -> Result.retry()
            SyncPolicy.Decision.FAILURE -> Result.failure()
        }
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "iratrack_periodic_sync",
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
