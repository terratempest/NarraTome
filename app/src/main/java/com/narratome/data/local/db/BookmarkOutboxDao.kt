package com.narratome.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkOutboxDao {

    @Insert
    suspend fun insert(entity: BookmarkOutboxEntity): Long

    @Query("SELECT * FROM bookmark_outbox ORDER BY createdAtEpochMs ASC LIMIT :limit")
    suspend fun listPending(limit: Int = 25): List<BookmarkOutboxEntity>

    @Query("SELECT COUNT(*) FROM bookmark_outbox")
    fun observePendingCount(): Flow<Int>

    @Query("DELETE FROM bookmark_outbox WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE bookmark_outbox SET attempts = :attempts, lastError = :err WHERE id = :id")
    suspend fun markAttempt(id: Long, attempts: Int, err: String?)
}
