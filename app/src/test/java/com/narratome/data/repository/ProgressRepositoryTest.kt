package com.narratome.data.repository

import com.narratome.data.local.db.ContinueListenRow
import com.narratome.data.local.db.MediaProgressEntity
import com.narratome.data.local.db.PlaybackHistoryDao
import com.narratome.data.local.db.PlaybackHistoryEntity
import com.narratome.data.local.db.ProgressDao
import com.narratome.data.remote.AudiobookshelfApi
import com.narratome.data.remote.dto.MediaProgressMeDto
import com.narratome.data.remote.dto.MediaProgressPatchDto
import com.narratome.domain.model.SyncConflictPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.lang.reflect.Proxy

class ProgressRepositoryTest {

    @Test
    fun updateLocalProgress_keepsExistingDurationWhenIncomingDurationIsNull() = runTest {
        val dao = FakeProgressDao()
        dao.upsert(
            MediaProgressEntity(
                progressKey = "book-1",
                libraryItemId = "book-1",
                currentTimeSec = 10.0,
                durationSec = 3600.0,
                lastKnownServerLastUpdate = null,
                localDirty = false,
                localRevision = 0L,
            ),
        )
        val repo = ProgressRepository(api = capturingApi(), progressDao = dao)

        repo.updateLocalProgress(
            libraryItemId = "book-1",
            currentTimeSec = 30.0,
            durationSec = null,
            markDirty = true,
        )

        assertEquals(3600.0, dao.get("book-1")!!.durationSec, 0.001)
    }

    @Test
    fun setFinished_marksDirtyPreservesPositionAndSetsFinishedAt() = runTest {
        val dao = FakeProgressDao()
        dao.upsert(
            MediaProgressEntity(
                progressKey = "book-1",
                libraryItemId = "book-1",
                currentTimeSec = 123.0,
                durationSec = 1000.0,
                lastKnownServerLastUpdate = 55L,
                localDirty = false,
                localRevision = 4L,
                libraryId = "lib-1",
            ),
        )
        val repo = ProgressRepository(api = capturingApi(), progressDao = dao)

        repo.setFinished(
            libraryItemId = "book-1",
            finished = true,
            libraryId = "ignored",
            currentTimeSec = 999.0,
            durationSec = 2000.0,
        )

        val row = dao.get("book-1")!!
        assertEquals(123.0, row.currentTimeSec, 0.001)
        assertEquals(1000.0, row.durationSec, 0.001)
        assertTrue(row.localDirty)
        assertEquals(5L, row.localRevision)
        assertEquals(55L, row.lastKnownServerLastUpdate)
        assertEquals("lib-1", row.libraryId)
        assertTrue(row.finishedAt != null)
    }

    @Test
    fun setFinished_falseClearsFinishedAtAndKeepsPosition() = runTest {
        val dao = FakeProgressDao()
        dao.upsert(
            MediaProgressEntity(
                progressKey = "book-1",
                libraryItemId = "book-1",
                currentTimeSec = 123.0,
                durationSec = 1000.0,
                lastKnownServerLastUpdate = null,
                localDirty = false,
                localRevision = 4L,
                libraryId = "lib-1",
                finishedAt = 999L,
            ),
        )
        val repo = ProgressRepository(api = capturingApi(), progressDao = dao)

        repo.setFinished(
            libraryItemId = "book-1",
            finished = false,
            libraryId = "lib-1",
            currentTimeSec = 999.0,
            durationSec = 2000.0,
        )

        val row = dao.get("book-1")!!
        assertEquals(123.0, row.currentTimeSec, 0.001)
        assertEquals(1000.0, row.durationSec, 0.001)
        assertTrue(row.localDirty)
        assertEquals(5L, row.localRevision)
        assertNull(row.finishedAt)
    }

    @Test
    fun pushDirtyToServer_usesFinishedAtNotProgressThreshold() = runTest {
        val dao = FakeProgressDao()
        dao.upsert(
            MediaProgressEntity(
                progressKey = "book-1",
                libraryItemId = "book-1",
                currentTimeSec = 100.0,
                durationSec = 100.0,
                lastKnownServerLastUpdate = null,
                localDirty = true,
                localRevision = 1L,
            ),
        )
        var patch: MediaProgressPatchDto? = null
        val repo = ProgressRepository(
            api = capturingApi { patch = it },
            progressDao = dao,
        )

        val result = repo.pushDirtyToServer()

        assertFalse(result.hasFailures)
        assertEquals(1, result.successfulItems.size)
        assertEquals(1.0, patch!!.progress!!, 0.001)
        assertEquals(false, patch!!.isFinished)
        assertNull(patch!!.finishedAt)
        assertFalse(dao.get("book-1")!!.localDirty)
    }

    @Test
    fun pushDirtyToServer_skipsBlankLibraryItemIds() = runTest {
        val dao = FakeProgressDao()
        dao.upsert(
            MediaProgressEntity(
                progressKey = "",
                libraryItemId = "",
                currentTimeSec = 10.0,
                durationSec = 100.0,
                lastKnownServerLastUpdate = null,
                localDirty = true,
                localRevision = 1L,
            ),
        )
        var patchCount = 0
        val repo = ProgressRepository(
            api = capturingApi { patchCount++ },
            progressDao = dao,
        )

        val result = repo.pushDirtyToServer()

        assertFalse(result.hasFailures)
        assertEquals(0, patchCount)
        assertFalse(dao.get("")!!.localDirty)
    }

    @Test
    fun mergeMediaProgressFromMe_appliesFinishedAtWithoutCurrentTime() = runTest {
        val dao = FakeProgressDao()
        dao.upsert(
            MediaProgressEntity(
                progressKey = "book-1",
                libraryItemId = "book-1",
                currentTimeSec = 42.0,
                durationSec = 500.0,
                lastKnownServerLastUpdate = 10L,
                localDirty = false,
                localRevision = 2L,
                libraryId = "lib-1",
            ),
        )
        val repo = ProgressRepository(api = capturingApi(), progressDao = dao)

        repo.mergeMediaProgressFromMe(
            rows = listOf(
                MediaProgressMeDto(
                    libraryItemId = "book-1",
                    libraryId = "lib-1",
                    finishedAt = 1234L,
                    lastUpdate = 99L,
                ),
            ),
            policy = SyncConflictPolicy.PREFER_SERVER,
        )

        val row = dao.get("book-1")!!
        assertEquals(42.0, row.currentTimeSec, 0.001)
        assertEquals(500.0, row.durationSec, 0.001)
        assertEquals(99L, row.lastKnownServerLastUpdate)
        assertFalse(row.localDirty)
        assertEquals(2L, row.localRevision)
        assertEquals(1234L, row.finishedAt)
    }

    @Test
    fun mergeMediaProgressFromMe_createsFinishedRowWithoutCurrentTime() = runTest {
        val dao = FakeProgressDao()
        val repo = ProgressRepository(api = capturingApi(), progressDao = dao)

        repo.mergeMediaProgressFromMe(
            rows = listOf(
                MediaProgressMeDto(
                    libraryItemId = "book-1",
                    libraryId = "lib-1",
                    finishedAt = 1234L,
                ),
            ),
            policy = SyncConflictPolicy.PREFER_SERVER,
        )

        val row = dao.get("book-1")!!
        assertEquals(0.0, row.currentTimeSec, 0.001)
        assertEquals(0.0, row.durationSec, 0.001)
        assertEquals("lib-1", row.libraryId)
        assertEquals(1234L, row.finishedAt)
    }

    @Test
    fun applyServerProgressChoice_recordsServerUpdateWhenPositionChanges() = runTest {
        val dao = FakeProgressDao()
        val historyDao = FakePlaybackHistoryDao()
        dao.upsert(
            MediaProgressEntity(
                progressKey = "book-1",
                libraryItemId = "book-1",
                currentTimeSec = 10.0,
                durationSec = 500.0,
                lastKnownServerLastUpdate = null,
                localDirty = true,
                localRevision = 4L,
            ),
        )
        val repo = ProgressRepository(
            api = capturingApi(),
            progressDao = dao,
            playbackHistoryRepository = PlaybackHistoryRepository(historyDao),
        )

        repo.applyServerProgressChoice(
            libraryItemId = "book-1",
            serverTimeSec = 42.0,
            serverDurationSec = 500.0,
            serverLastUpdate = 99L,
        )

        val history = historyDao.latestFor("book-1")
        assertEquals(1, history.size)
        assertEquals("SERVER_UPDATE", history.single().eventType)
        assertEquals(42.0, history.single().positionSec, 0.001)
        assertFalse(dao.get("book-1")!!.localDirty)
    }

    @Test
    fun applyServerProgressChoice_doesNotRecordServerUpdateWhenPositionUnchanged() = runTest {
        val dao = FakeProgressDao()
        val historyDao = FakePlaybackHistoryDao()
        dao.upsert(
            MediaProgressEntity(
                progressKey = "book-1",
                libraryItemId = "book-1",
                currentTimeSec = 42.0,
                durationSec = 500.0,
                lastKnownServerLastUpdate = null,
                localDirty = true,
                localRevision = 4L,
            ),
        )
        val repo = ProgressRepository(
            api = capturingApi(),
            progressDao = dao,
            playbackHistoryRepository = PlaybackHistoryRepository(historyDao),
        )

        repo.applyServerProgressChoice(
            libraryItemId = "book-1",
            serverTimeSec = 42.0,
            serverDurationSec = 500.0,
            serverLastUpdate = 99L,
        )

        assertEquals(0, historyDao.latestFor("book-1").size)
        assertFalse(dao.get("book-1")!!.localDirty)
    }

    private fun capturingApi(
        onPatch: (MediaProgressPatchDto) -> Unit = {},
    ): AudiobookshelfApi {
        return Proxy.newProxyInstance(
            AudiobookshelfApi::class.java.classLoader,
            arrayOf(AudiobookshelfApi::class.java),
        ) { _, method, args ->
            when (method.name) {
                "patchProgress", "patchEpisodeProgress" -> {
                    onPatch(args?.filterIsInstance<MediaProgressPatchDto>()?.single()!!)
                    Response.success(Unit)
                }
                else -> error("Unexpected API call: ${method.name}")
            }
        } as AudiobookshelfApi
    }

    private class FakeProgressDao : ProgressDao {
        private val rows = LinkedHashMap<String, MediaProgressEntity>()
        private val state = MutableStateFlow<List<MediaProgressEntity>>(emptyList())

        override suspend fun upsert(entity: MediaProgressEntity) {
            rows[entity.libraryItemId] = entity
            state.value = rows.values.toList()
        }

        override suspend fun get(id: String): MediaProgressEntity? = rows[id]

        override fun observe(id: String): Flow<MediaProgressEntity?> =
            state.map { list -> list.firstOrNull { it.libraryItemId == id } }

        override suspend fun getAllDirty(): List<MediaProgressEntity> =
            rows.values.filter { it.localDirty }

        override fun getAll(): Flow<List<MediaProgressEntity>> = state

        override fun observeContinueListening(limit: Int, downloadedOnly: Boolean): Flow<List<ContinueListenRow>> =
            MutableStateFlow(emptyList())

        override fun observeContinueListeningForLibrary(
            libraryId: String,
            limit: Int,
            downloadedOnly: Boolean,
        ): Flow<List<ContinueListenRow>> = MutableStateFlow(emptyList())

        override fun observeContinueSeries(limit: Int, downloadedOnly: Boolean): Flow<List<ContinueListenRow>> =
            MutableStateFlow(emptyList())

        override fun observeContinueSeriesForLibrary(
            libraryId: String,
            limit: Int,
            downloadedOnly: Boolean,
        ): Flow<List<ContinueListenRow>> = MutableStateFlow(emptyList())

        override suspend fun listContinueListening(
            limit: Int,
            downloadedOnly: Boolean,
            offset: Int,
        ): List<ContinueListenRow> =
            emptyList()

        override suspend fun listContinueListeningForLibrary(
            libraryId: String,
            limit: Int,
            downloadedOnly: Boolean,
        ): List<ContinueListenRow> = emptyList()

        override suspend fun listContinueSeries(
            limit: Int,
            downloadedOnly: Boolean,
            offset: Int,
        ): List<ContinueListenRow> =
            emptyList()

        override suspend fun listContinueSeriesForLibrary(
            libraryId: String,
            limit: Int,
            downloadedOnly: Boolean,
        ): List<ContinueListenRow> = emptyList()

        override suspend fun getLastPlayed(): MediaProgressEntity? =
            rows.values.maxByOrNull { it.lastInteractionTime ?: Long.MIN_VALUE }
    }

    private class FakePlaybackHistoryDao : PlaybackHistoryDao {
        private var nextId = 1L
        private val rows = mutableListOf<PlaybackHistoryEntity>()
        private val state = MutableStateFlow<List<PlaybackHistoryEntity>>(emptyList())

        override suspend fun insert(entity: PlaybackHistoryEntity): Long {
            val id = nextId++
            rows += entity.copy(id = id)
            state.value = rows.toList()
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
            rows.replaceAll {
                if (it.id == id) {
                    it.copy(positionSec = positionSec, occurredAtEpochMs = occurredAtEpochMs)
                } else {
                    it
                }
            }
            state.value = rows.toList()
        }

        override suspend fun pruneForItem(libraryItemId: String, keepCount: Int) {
            val keepIds = latestFor(libraryItemId).take(keepCount).map { it.id }.toSet()
            rows.removeAll { it.libraryItemId == libraryItemId && it.id !in keepIds }
            state.value = rows.toList()
        }

        fun latestFor(libraryItemId: String): List<PlaybackHistoryEntity> =
            rows
                .filter { it.libraryItemId == libraryItemId }
                .sortedWith(compareByDescending<PlaybackHistoryEntity> { it.occurredAtEpochMs }.thenByDescending { it.id })
    }
}
