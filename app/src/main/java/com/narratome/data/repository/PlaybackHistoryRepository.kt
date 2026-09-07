package com.narratome.data.repository

import com.narratome.data.local.db.PlaybackHistoryDao
import com.narratome.data.local.db.PlaybackHistoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

enum class PlaybackHistoryEventType {
    PLAY,
    PAUSE,
    SEEK,
    SERVER_UPDATE,
}

@Singleton
class PlaybackHistoryRepository @Inject constructor(
    private val playbackHistoryDao: PlaybackHistoryDao,
) {
    fun observeHistory(libraryItemId: String): Flow<List<PlaybackHistoryEntity>> =
        playbackHistoryDao.observeLatestForItem(libraryItemId)

    suspend fun recordEvent(
        libraryItemId: String,
        eventType: PlaybackHistoryEventType,
        positionSec: Double,
        occurredAtEpochMs: Long = System.currentTimeMillis(),
    ) = withContext(Dispatchers.IO) {
        if (libraryItemId.isBlank()) return@withContext
        val clampedPositionSec = positionSec.coerceAtLeast(0.0)
        if (eventType == PlaybackHistoryEventType.PAUSE) {
            val latest = playbackHistoryDao.getLatestForItem(libraryItemId)
            if (
                latest?.eventType == PlaybackHistoryEventType.PAUSE.name &&
                latest.positionSec == clampedPositionSec
            ) {
                return@withContext
            }
        }
        if (eventType == PlaybackHistoryEventType.SEEK) {
            val latestSeek = playbackHistoryDao.getLatestForItemAndType(
                libraryItemId = libraryItemId,
                eventType = PlaybackHistoryEventType.SEEK.name,
            )
            if (
                latestSeek != null &&
                occurredAtEpochMs - latestSeek.occurredAtEpochMs in 0..SeekOverwriteWindowMs
            ) {
                playbackHistoryDao.updatePositionAndOccurredAt(
                    id = latestSeek.id,
                    positionSec = clampedPositionSec,
                    occurredAtEpochMs = occurredAtEpochMs,
                )
                return@withContext
            }
        }
        playbackHistoryDao.insert(
            PlaybackHistoryEntity(
                libraryItemId = libraryItemId,
                eventType = eventType.name,
                positionSec = clampedPositionSec,
                occurredAtEpochMs = occurredAtEpochMs,
            ),
        )
        playbackHistoryDao.pruneForItem(libraryItemId, MaxHistoryEventsPerItem)
    }

    private companion object {
        const val MaxHistoryEventsPerItem = 50
        const val SeekOverwriteWindowMs = 60_000L
    }
}
