package com.narratome.data.repository

import com.narratome.data.local.db.PlaybackHistoryDao
import com.narratome.data.local.db.PlaybackHistoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackHistoryRepositoryTest {

    @Test
    fun recordEvent_clampsNegativePositionAndStoresFields() = runTest {
        val dao = FakePlaybackHistoryDao()
        val repo = PlaybackHistoryRepository(dao)

        repo.recordEvent(
            libraryItemId = "book-1",
            eventType = PlaybackHistoryEventType.PAUSE,
            positionSec = -10.0,
            occurredAtEpochMs = 1234L,
        )

        val row = dao.rows.single()
        assertEquals("book-1", row.libraryItemId)
        assertEquals("PAUSE", row.eventType)
        assertEquals(0.0, row.positionSec, 0.001)
        assertEquals(1234L, row.occurredAtEpochMs)
    }

    @Test
    fun observeHistory_returnsNewestFirst() = runTest {
        val dao = FakePlaybackHistoryDao()
        val repo = PlaybackHistoryRepository(dao)

        repo.recordEvent("book-1", PlaybackHistoryEventType.PLAY, 1.0, occurredAtEpochMs = 100L)
        repo.recordEvent("book-1", PlaybackHistoryEventType.SEEK, 2.0, occurredAtEpochMs = 300L)
        repo.recordEvent("book-1", PlaybackHistoryEventType.PAUSE, 3.0, occurredAtEpochMs = 200L)

        val observed = dao.latestFor("book-1")
        assertEquals(listOf("SEEK", "PAUSE", "PLAY"), observed.map { it.eventType })
    }

    @Test
    fun recordEvent_prunesToFiftyPerAudiobookOnly() = runTest {
        val dao = FakePlaybackHistoryDao()
        val repo = PlaybackHistoryRepository(dao)

        repeat(55) { index ->
            repo.recordEvent(
                "book-1",
                PlaybackHistoryEventType.SEEK,
                index.toDouble(),
                occurredAtEpochMs = index * 61_000L,
            )
        }
        repeat(3) { index ->
            repo.recordEvent("book-2", PlaybackHistoryEventType.PLAY, index.toDouble(), occurredAtEpochMs = index.toLong())
        }

        val bookOne = dao.latestFor("book-1")
        val bookTwo = dao.latestFor("book-2")
        assertEquals(50, bookOne.size)
        assertEquals(3, bookTwo.size)
        assertEquals(54.0, bookOne.first().positionSec, 0.001)
        assertEquals(5.0, bookOne.last().positionSec, 0.001)
    }

    @Test
    fun recordEvent_overwritesPreviousSeekWithinSixtySeconds() = runTest {
        val dao = FakePlaybackHistoryDao()
        val repo = PlaybackHistoryRepository(dao)

        repo.recordEvent("book-1", PlaybackHistoryEventType.SEEK, 10.0, occurredAtEpochMs = 1_000L)
        repo.recordEvent("book-1", PlaybackHistoryEventType.SEEK, 25.0, occurredAtEpochMs = 60_999L)

        val rows = dao.latestFor("book-1")
        assertEquals(1, rows.size)
        assertEquals(25.0, rows.single().positionSec, 0.001)
        assertEquals(60_999L, rows.single().occurredAtEpochMs)
    }

    @Test
    fun recordEvent_addsNewSeekAfterSixtySeconds() = runTest {
        val dao = FakePlaybackHistoryDao()
        val repo = PlaybackHistoryRepository(dao)

        repo.recordEvent("book-1", PlaybackHistoryEventType.SEEK, 10.0, occurredAtEpochMs = 1_000L)
        repo.recordEvent("book-1", PlaybackHistoryEventType.SEEK, 25.0, occurredAtEpochMs = 61_001L)

        val rows = dao.latestFor("book-1")
        assertEquals(2, rows.size)
        assertEquals(listOf(25.0, 10.0), rows.map { it.positionSec })
    }

    @Test
    fun recordEvent_ignoresConsecutivePauseAndKeepsOriginalTimestamp() = runTest {
        val dao = FakePlaybackHistoryDao()
        val repo = PlaybackHistoryRepository(dao)

        repo.recordEvent("book-1", PlaybackHistoryEventType.PAUSE, 240.0, occurredAtEpochMs = 1_000L)
        repo.recordEvent("book-1", PlaybackHistoryEventType.PAUSE, 240.0, occurredAtEpochMs = 3_601_000L)

        val rows = dao.latestFor("book-1")
        assertEquals(1, rows.size)
        assertEquals(240.0, rows.single().positionSec, 0.001)
        assertEquals(1_000L, rows.single().occurredAtEpochMs)
    }

    @Test
    fun recordEvent_addsConsecutivePauseWhenPositionChanged() = runTest {
        val dao = FakePlaybackHistoryDao()
        val repo = PlaybackHistoryRepository(dao)

        repo.recordEvent("book-1", PlaybackHistoryEventType.PAUSE, 240.0, occurredAtEpochMs = 1_000L)
        repo.recordEvent("book-1", PlaybackHistoryEventType.PAUSE, 241.0, occurredAtEpochMs = 3_601_000L)

        val rows = dao.latestFor("book-1")
        assertEquals(2, rows.size)
        assertEquals(listOf(241.0, 240.0), rows.map { it.positionSec })
        assertEquals(listOf(3_601_000L, 1_000L), rows.map { it.occurredAtEpochMs })
    }

    @Test
    fun recordEvent_addsPauseWhenDifferentEventHappenedSincePreviousPause() = runTest {
        val dao = FakePlaybackHistoryDao()
        val repo = PlaybackHistoryRepository(dao)

        repo.recordEvent("book-1", PlaybackHistoryEventType.PAUSE, 240.0, occurredAtEpochMs = 1_000L)
        repo.recordEvent("book-1", PlaybackHistoryEventType.PLAY, 240.0, occurredAtEpochMs = 2_000L)
        repo.recordEvent("book-1", PlaybackHistoryEventType.PAUSE, 300.0, occurredAtEpochMs = 3_000L)

        val rows = dao.latestFor("book-1")
        assertEquals(listOf("PAUSE", "PLAY", "PAUSE"), rows.map { it.eventType })
        assertEquals(listOf(3_000L, 2_000L, 1_000L), rows.map { it.occurredAtEpochMs })
    }

    private class FakePlaybackHistoryDao : PlaybackHistoryDao {
        private var nextId = 1L
        private val state = MutableStateFlow<List<PlaybackHistoryEntity>>(emptyList())
        val rows: List<PlaybackHistoryEntity> get() = state.value

        override suspend fun insert(entity: PlaybackHistoryEntity): Long {
            val id = nextId++
            state.value = state.value + entity.copy(id = id)
            return id
        }

        override fun observeLatestForItem(libraryItemId: String): Flow<List<PlaybackHistoryEntity>> =
            state.map { latestFor(libraryItemId) }

        override suspend fun getLatestForItem(libraryItemId: String): PlaybackHistoryEntity? =
            latestFor(libraryItemId).firstOrNull()

        override suspend fun getLatestForItemAndType(
            libraryItemId: String,
            eventType: String,
        ): PlaybackHistoryEntity? =
            latestFor(libraryItemId).firstOrNull { it.eventType == eventType }

        override suspend fun updatePositionAndOccurredAt(
            id: Long,
            positionSec: Double,
            occurredAtEpochMs: Long,
        ) {
            state.value = state.value.map {
                if (it.id == id) {
                    it.copy(positionSec = positionSec, occurredAtEpochMs = occurredAtEpochMs)
                } else {
                    it
                }
            }
        }

        override suspend fun pruneForItem(libraryItemId: String, keepCount: Int) {
            val keepIds = latestFor(libraryItemId).take(keepCount).map { it.id }.toSet()
            state.value = state.value.filter { it.libraryItemId != libraryItemId || it.id in keepIds }
        }

        fun latestFor(libraryItemId: String): List<PlaybackHistoryEntity> =
            state.value
                .filter { it.libraryItemId == libraryItemId }
                .sortedWith(compareByDescending<PlaybackHistoryEntity> { it.occurredAtEpochMs }.thenByDescending { it.id })
    }
}
