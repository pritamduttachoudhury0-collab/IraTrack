package com.iratrack.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(records: List<UsageRecord>)

    @Query("SELECT * FROM usage_records ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<UsageRecord>>

    @Query("SELECT * FROM usage_records WHERE provider = :provider ORDER BY timestamp DESC")
    fun observeProvider(provider: String): Flow<List<UsageRecord>>

    @Query("DELETE FROM usage_records")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM usage_records")
    suspend fun count(): Long
}

@Dao
interface ProviderStateDao {
    @Query("SELECT * FROM provider_state")
    fun observeAll(): Flow<List<ProviderState>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: ProviderState)

    @Query("SELECT * FROM provider_state WHERE provider = :provider LIMIT 1")
    suspend fun get(provider: String): ProviderState?

    @Query("DELETE FROM provider_state")
    suspend fun deleteAll()
}
