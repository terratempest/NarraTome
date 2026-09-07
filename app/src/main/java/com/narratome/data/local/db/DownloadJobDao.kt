package com.narratome.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadJobDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DownloadJobEntity)

    @Transaction
    suspend fun saveProgress(entity: DownloadJobEntity) {
        if (getJob(entity.downloadKey)?.state != "CANCELLED") upsert(entity)
    }

    @Query("SELECT * FROM download_jobs WHERE downloadKey = :downloadKey LIMIT 1")
    fun observeJob(downloadKey: String): Flow<DownloadJobEntity?>

    @Query("SELECT * FROM download_jobs WHERE downloadKey = :downloadKey LIMIT 1")
    suspend fun getJob(downloadKey: String): DownloadJobEntity?

    @Query("UPDATE download_jobs SET state = 'QUEUED', lastError = NULL, updatedAtEpochMs = :now WHERE downloadKey = :key AND state = 'PAUSED_NETWORK'")
    suspend fun queueNetworkPaused(key: String, now: Long): Int

    @Query("UPDATE download_jobs SET state = :state, lastError = :error, updatedAtEpochMs = :now WHERE downloadKey = :key AND (state != 'CANCELLED' OR :state = 'CANCELLED')")
    suspend fun setState(key: String, state: String, error: String?, now: Long)

    @Query("SELECT * FROM download_jobs WHERE libraryItemId = :libraryItemId ORDER BY startedAtEpochMs ASC, updatedAtEpochMs ASC")
    fun observeJobsForItem(libraryItemId: String): Flow<List<DownloadJobEntity>>

    @Query("SELECT * FROM download_jobs ORDER BY startedAtEpochMs ASC, updatedAtEpochMs ASC")
    fun observeAllJobs(): Flow<List<DownloadJobEntity>>

    @Query("SELECT COUNT(*) FROM download_jobs WHERE state IN ('QUEUED', 'RUNNING', 'PAUSED_NETWORK')")
    fun observeActiveCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM download_jobs WHERE state IN ('FAILED', 'CANCELLED')")
    fun observeFailedCount(): Flow<Int>

    @Query("DELETE FROM download_jobs WHERE downloadKey = :downloadKey")
    suspend fun delete(downloadKey: String)

    @Query("DELETE FROM download_jobs WHERE libraryItemId = :libraryItemId")
    suspend fun deleteForItem(libraryItemId: String)

    @Query("DELETE FROM download_jobs WHERE state IN (:states)")
    suspend fun deleteByStates(states: List<String>): Int
}
