package com.iratrack.app.providers

import com.iratrack.app.data.*

interface ProviderAdapter {
    val provider: ProviderId
    suspend fun sync(apiCredential: String): SyncResult
}
