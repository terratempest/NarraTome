package com.narratome.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackHistoryDao {

    @Insert
    suspend fun insert(entity: PlaybackHistoryEntity): Long

    @Query(
        """
        SELECT * FROM playback_history
        WHERE libraryItemId = :libraryItemId
        ORDER BY occurredAtEpochMs DESC, id DESC
        """,
    )
    fun observeLatestForItem(libraryItemId: String): Flow<List<PlaybackHistoryEntity>>

    @Query(
        """
        SELECT * FROM playback_history
        WHERE libraryItemId = :libraryItemId
        ORDER BY occurredAtEpochMs DESC, id DESC
        LIMIT 1
        """,
    )
    suspend fun getLatestForItem(libraryItemId: String): PlaybackHistoryEntity?

    @Query(
        """
        SELECT * FROM playback_history
        WHERE libraryItemId = :libraryItemId
          AND eventType = :eventType
        ORDER BY occurredAtEpochMs DESC, id DESC
        LIMIT 1
        """,
    )
    suspend fun getLatestForItemAndType(
        libraryItemId: String,
        eventType: String,
    ): PlaybackHistoryEntity?

    @Query(
        """
        UPDATE playback_history
        SET positionSec = :positionSec,
            occurredAtEpochMs = :occurredAtEpochMs
        WHERE id = :id
        """,
    )
    suspend fun updatePositionAndOccurredAt(
        id: Long,
        positionSec: Double,
        occurredAtEpochMs: Long,
    )

    @Query(
        """
        DELETE FROM playback_history
        WHERE libraryItemId = :libraryItemId
          AND id NOT IN (
              SELECT id FROM playback_history
              WHERE libraryItemId = :libraryItemId
              ORDER BY occurredAtEpochMs DESC, id DESC
              LIMIT :keepCount
          )
        """,
    )
    suspend fun pruneForItem(libraryItemId: String, keepCount: Int)
}
