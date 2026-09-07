package com.narratome.data.repository

import com.narratome.data.local.db.ContinueListenRow
import com.narratome.data.local.db.MediaProgressEntity
import com.narratome.data.local.db.ProgressDao
import com.narratome.data.remote.AudiobookshelfApi
import com.narratome.data.remote.dto.MediaProgressMeDto
import com.narratome.data.remote.dto.MediaProgressPatchDto
import com.narratome.domain.model.SyncConflictPolicy
import com.narratome.domain.progress.ProgressConflictRules
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

sealed interface ProgressReconcileOutcome {
    data object Idle : ProgressReconcileOutcome
    data object AppliedServerSnapshot : ProgressReconcileOutcome
    data object KeptLocalDirty : ProgressReconcileOutcome
    data class ChooseProgress(
        val localSec: Double,
        val serverSec: Double,
        val durationSec: Double,
        val serverLastUpdate: Long?,
        val finishedAt: Long?,
    ) : ProgressReconcileOutcome
}

data class ProgressPushResult(
    val successfulItems: List<Pair<String, String?>>,
    val failureCount: Int,
) {
    val hasFailures: Boolean get() = failureCount > 0
}

data class BookProgressUi(
    val progress: Float,
    val isFinished: Boolean,
)

@Singleton
class ProgressRepository @Inject constructor(
    private val api: AudiobookshelfApi,
    private val progressDao: ProgressDao,
    private val playbackHistoryRepository: PlaybackHistoryRepository? = null,
) {

    fun observeLocal(itemId: String): Flow<MediaProgressEntity?> = progressDao.observe(mediaProgressKey(itemId))

    fun observeLocal(itemId: String, episodeId: String?): Flow<MediaProgressEntity?> =
        progressDao.observe(mediaProgressKey(normalizedLibraryItemId(itemId), episodeId ?: episodeIdFromProgressKey(itemId)))

    fun observeProgressUiMap(): Flow<Map<String, BookProgressUi>> =
        progressDao.getAll().map { list ->
            list.groupBy { it.libraryItemId }.mapValues { (_, rows) ->
                val row = rows.maxByOrNull { it.lastInteractionTime ?: it.lastKnownServerLastUpdate ?: 0L }
                val progress = if (row != null && row.durationSec > 0) {
                    (row.currentTimeSec / row.durationSec).toFloat().coerceIn(0f, 1f)
                } else {
                    0f
                }
                BookProgressUi(
                    progress = progress,
                    isFinished = row?.finishedAt != null,
                )
            }
        }

    suspend fun getMediaProgress(libraryItemId: String): MediaProgressEntity? =
        getMediaProgress(libraryItemId, episodeId = null)

    suspend fun getMediaProgress(libraryItemId: String, episodeId: String?): MediaProgressEntity? =
        withContext(Dispatchers.IO) {
            val actualId = normalizedLibraryItemId(libraryItemId)
            val actualEpisodeId = episodeId ?: episodeIdFromProgressKey(libraryItemId)
            progressDao.get(mediaProgressKey(actualId, actualEpisodeId))
        }

    fun observeContinueListening(limit: Int = 25, downloadedOnly: Boolean = false): Flow<List<ContinueListenRow>> =
        progressDao.observeContinueListening(limit, downloadedOnly)

    fun observeContinueListening(
        libraryId: String,
        limit: Int = 25,
        downloadedOnly: Boolean = false,
    ): Flow<List<ContinueListenRow>> =
        progressDao.observeContinueListeningForLibrary(libraryId, limit, downloadedOnly)

    fun observeContinueSeries(limit: Int = 25, downloadedOnly: Boolean = false): Flow<List<ContinueListenRow>> =
        progressDao.observeContinueSeries(limit, downloadedOnly)

    fun observeContinueSeries(
        libraryId: String,
        limit: Int = 25,
        downloadedOnly: Boolean = false,
    ): Flow<List<ContinueListenRow>> =
        progressDao.observeContinueSeriesForLibrary(libraryId, limit, downloadedOnly)

    suspend fun listContinueListening(
        limit: Int = 25,
        downloadedOnly: Boolean = false,
        offset: Int = 0,
    ): List<ContinueListenRow> =
        withContext(Dispatchers.IO) { progressDao.listContinueListening(limit, downloadedOnly, offset) }

    suspend fun listContinueListening(
        libraryId: String,
        limit: Int = 25,
        downloadedOnly: Boolean = false,
    ): List<ContinueListenRow> =
        withContext(Dispatchers.IO) { progressDao.listContinueListeningForLibrary(libraryId, limit, downloadedOnly) }

    suspend fun listContinueSeries(
        limit: Int = 25,
        downloadedOnly: Boolean = false,
        offset: Int = 0,
    ): List<ContinueListenRow> =
        withContext(Dispatchers.IO) { progressDao.listContinueSeries(limit, downloadedOnly, offset) }

    suspend fun listContinueSeries(
        libraryId: String,
        limit: Int = 25,
        downloadedOnly: Boolean = false,
    ): List<ContinueListenRow> =
        withContext(Dispatchers.IO) { progressDao.listContinueSeriesForLibrary(libraryId, limit, downloadedOnly) }

    suspend fun updateLocalProgress(
        libraryItemId: String,
        currentTimeSec: Double,
        durationSec: Double?,
        markDirty: Boolean,
        libraryId: String? = null,
        episodeId: String? = null,
        hideFromContinueListening: Boolean? = null,
        startedAt: Long? = null,
        finishedAt: Long? = null,
    ) = withContext(Dispatchers.IO) {
        if (libraryItemId.isBlank()) return@withContext
        val actualId = normalizedLibraryItemId(libraryItemId)
        val actualEpisodeId = episodeId ?: episodeIdFromProgressKey(libraryItemId)
        val key = mediaProgressKey(actualId, actualEpisodeId)
        val existing = progressDao.get(key)
        progressDao.upsert(
            MediaProgressEntity(
                progressKey = key,
                libraryItemId = actualId,
                currentTimeSec = currentTimeSec,
                durationSec = durationSec?.takeIf { it > 0.0 } ?: existing?.durationSec ?: 0.0,
                lastKnownServerLastUpdate = existing?.lastKnownServerLastUpdate,
                localDirty = markDirty || (existing?.localDirty == true),
                localRevision = (existing?.localRevision ?: 0L) + if (markDirty) 1 else 0,
                libraryId = libraryId ?: existing?.libraryId,
                episodeId = actualEpisodeId ?: existing?.episodeId,
                hideFromContinueListening = hideFromContinueListening ?: existing?.hideFromContinueListening ?: false,
                startedAt = startedAt ?: existing?.startedAt,
                finishedAt = finishedAt ?: existing?.finishedAt,
                lastInteractionTime = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun setFinished(
        libraryItemId: String,
        finished: Boolean,
        libraryId: String?,
        currentTimeSec: Double,
        durationSec: Double?,
        episodeId: String? = null,
    ) = withContext(Dispatchers.IO) {
        if (libraryItemId.isBlank()) return@withContext
        val actualId = normalizedLibraryItemId(libraryItemId)
        val actualEpisodeId = episodeId ?: episodeIdFromProgressKey(libraryItemId)
        val key = mediaProgressKey(actualId, actualEpisodeId)
        val existing = progressDao.get(key)
        val now = System.currentTimeMillis()
        progressDao.upsert(
            MediaProgressEntity(
                progressKey = key,
                libraryItemId = actualId,
                currentTimeSec = existing?.currentTimeSec ?: currentTimeSec,
                durationSec = existing?.durationSec?.takeIf { it > 0 } ?: durationSec ?: 0.0,
                lastKnownServerLastUpdate = existing?.lastKnownServerLastUpdate,
                localDirty = true,
                localRevision = (existing?.localRevision ?: 0L) + 1L,
                libraryId = existing?.libraryId ?: libraryId,
                episodeId = actualEpisodeId ?: existing?.episodeId,
                hideFromContinueListening = existing?.hideFromContinueListening ?: false,
                startedAt = existing?.startedAt,
                finishedAt = if (finished) (existing?.finishedAt ?: now) else null,
                lastInteractionTime = now,
            ),
        )
    }

    suspend fun getDirtyItemIds(): List<Pair<String, String?>> = withContext(Dispatchers.IO) {
        progressDao.getAllDirty()
            .filter { it.libraryItemId.isNotBlank() }
            .map { it.libraryItemId to it.libraryId }
    }

    suspend fun pushDirtyToServer(): ProgressPushResult = withContext(Dispatchers.IO) {
        val dirty = progressDao.getAllDirty()
        val successful = mutableListOf<Pair<String, String?>>()
        var failCount = 0
        for (row in dirty) {
            if (row.libraryItemId.isBlank()) {
                progressDao.upsert(row.copy(localDirty = false))
                continue
            }
            if (row.durationSec <= 0) continue

            val progressFraction = (Math.round((row.currentTimeSec / row.durationSec) * 10000.0) / 10000.0).coerceIn(0.0, 1.0)
            val isFinished = row.finishedAt != null

            val patch = MediaProgressPatchDto(
                currentTime = row.currentTimeSec,
                duration = row.durationSec,
                progress = progressFraction,
                isFinished = isFinished,
                hideFromContinueListening = row.hideFromContinueListening,
                startedAt = row.startedAt,
                finishedAt = row.finishedAt,
            )
            val succeeded = try {
                val response = if (!row.episodeId.isNullOrBlank()) {
                    api.patchEpisodeProgress(row.libraryItemId, row.episodeId, patch)
                } else {
                    api.patchProgress(row.libraryItemId, patch)
                }
                response.isSuccessful
            } catch (_: Exception) {
                false
            }
            if (succeeded) {
                progressDao.upsert(row.copy(localDirty = false))
                successful += row.libraryItemId to row.libraryId
            } else {
                failCount++
            }
        }
        ProgressPushResult(successfulItems = successful, failureCount = failCount)
    }

    suspend fun markServerBaseline(libraryItemId: String, serverLastUpdate: Long?) {
        val actualId = normalizedLibraryItemId(libraryItemId)
        val actualEpisodeId = episodeIdFromProgressKey(libraryItemId)
        val row = progressDao.get(mediaProgressKey(actualId, actualEpisodeId)) ?: return
        progressDao.upsert(row.copy(lastKnownServerLastUpdate = serverLastUpdate))
    }

    suspend fun reconcileDetailOpened(
        libraryItemId: String,
        serverTimeSec: Double,
        serverDurationSec: Double,
        serverLastUpdate: Long?,
        policy: SyncConflictPolicy,
        libraryId: String? = null,
        episodeId: String? = null,
        hideFromContinueListening: Boolean = false,
        startedAt: Long? = null,
        finishedAt: Long? = null,
    ): ProgressReconcileOutcome = withContext(Dispatchers.IO) {
        if (libraryItemId.isBlank()) return@withContext ProgressReconcileOutcome.Idle
        val actualId = normalizedLibraryItemId(libraryItemId)
        val actualEpisodeId = episodeId ?: episodeIdFromProgressKey(libraryItemId)
        val key = mediaProgressKey(actualId, actualEpisodeId)
        val local = progressDao.get(key)
        if (local == null || !local.localDirty) {
            val shouldRecordServerUpdate = local?.let {
                abs(it.currentTimeSec - serverTimeSec) > PositionChangeToleranceSec
            } ?: (serverTimeSec > PositionChangeToleranceSec)
            progressDao.upsert(
                MediaProgressEntity(
                    progressKey = key,
                    libraryItemId = actualId,
                    currentTimeSec = serverTimeSec,
                    durationSec = serverDurationSec,
                    lastKnownServerLastUpdate = serverLastUpdate,
                    localDirty = false,
                    localRevision = local?.localRevision ?: 0L,
                    libraryId = libraryId ?: local?.libraryId,
                    episodeId = actualEpisodeId ?: local?.episodeId,
                    hideFromContinueListening = hideFromContinueListening,
                    startedAt = startedAt ?: local?.startedAt,
                    finishedAt = finishedAt,
                    lastInteractionTime = local?.lastInteractionTime,
                ),
            )
            if (shouldRecordServerUpdate) {
                playbackHistoryRepository?.recordEvent(
                    libraryItemId = actualId,
                    eventType = PlaybackHistoryEventType.SERVER_UPDATE,
                    positionSec = serverTimeSec,
                )
            }
            return@withContext ProgressReconcileOutcome.Idle
        }
        if (!ProgressConflictRules.hasDirtyConflict(local, serverTimeSec, serverDurationSec, serverLastUpdate)) {
            return@withContext ProgressReconcileOutcome.KeptLocalDirty
        }
        when (policy) {
            SyncConflictPolicy.PREFER_SERVER -> {
                applyServerProgressChoice(
                    actualId,
                    serverTimeSec,
                    serverDurationSec,
                    serverLastUpdate,
                    libraryId,
                    actualEpisodeId,
                    hideFromContinueListening,
                    startedAt,
                    finishedAt,
                )
                ProgressReconcileOutcome.AppliedServerSnapshot
            }
            SyncConflictPolicy.PREFER_LOCAL -> ProgressReconcileOutcome.KeptLocalDirty
            SyncConflictPolicy.ALWAYS_ASK -> ProgressReconcileOutcome.ChooseProgress(
                localSec = local.currentTimeSec,
                serverSec = serverTimeSec,
                durationSec = serverDurationSec,
                serverLastUpdate = serverLastUpdate,
                finishedAt = finishedAt,
            )
        }
    }

    suspend fun mergeMediaProgressFromMe(
        rows: List<MediaProgressMeDto>,
        policy: SyncConflictPolicy,
    ) = withContext(Dispatchers.IO) {
        for (r in rows) {
            val actualId = r.resolvedLibraryItemId()?.takeIf { it.isNotBlank() } ?: continue
            val episodeId = r.episodeId?.takeIf { it.isNotBlank() }
            val key = mediaProgressKey(actualId, episodeId)
            val local = progressDao.get(key)
            val hasServerProgressPayload =
                r.currentTime != null ||
                    r.duration != null ||
                    r.progress != null ||
                    r.lastUpdate != null ||
                    r.startedAt != null ||
                    r.finishedAt != null ||
                    r.hideFromContinueListening != null
            if (!hasServerProgressPayload) continue

            val serverTime = r.currentTime ?: local?.currentTimeSec ?: 0.0
            val duration = r.duration ?: local?.durationSec ?: 0.0
            val last = r.lastUpdate
            val libraryId = r.libraryId
            val hideFromContinueListening = r.hideFromContinueListening ?: false
            val startedAt = r.startedAt
            val finishedAt = r.finishedAt
            if (local == null || !local.localDirty) {
                val shouldRecordServerUpdate = local?.let {
                    abs(it.currentTimeSec - serverTime) > PositionChangeToleranceSec
                } ?: (serverTime > PositionChangeToleranceSec)
                progressDao.upsert(
                    MediaProgressEntity(
                        progressKey = key,
                        libraryItemId = actualId,
                        currentTimeSec = serverTime,
                        durationSec = duration,
                        lastKnownServerLastUpdate = last,
                        localDirty = false,
                        localRevision = local?.localRevision ?: 0L,
                        libraryId = libraryId ?: local?.libraryId,
                        episodeId = episodeId ?: local?.episodeId,
                        hideFromContinueListening = hideFromContinueListening,
                        startedAt = startedAt ?: local?.startedAt,
                        finishedAt = finishedAt,
                    ),
                )
                if (shouldRecordServerUpdate) {
                    playbackHistoryRepository?.recordEvent(
                        libraryItemId = actualId,
                        eventType = PlaybackHistoryEventType.SERVER_UPDATE,
                        positionSec = serverTime,
                    )
                }
                continue
            }
            when (policy) {
                SyncConflictPolicy.PREFER_SERVER ->
                    applyServerProgressChoice(actualId, serverTime, duration, last, libraryId, episodeId, hideFromContinueListening, startedAt, finishedAt)
                SyncConflictPolicy.PREFER_LOCAL -> Unit
                SyncConflictPolicy.ALWAYS_ASK -> Unit
            }
        }
    }

    suspend fun applyServerProgressChoice(
        libraryItemId: String,
        serverTimeSec: Double,
        serverDurationSec: Double,
        serverLastUpdate: Long?,
        libraryId: String? = null,
        episodeId: String? = null,
        hideFromContinueListening: Boolean = false,
        startedAt: Long? = null,
        finishedAt: Long? = null,
    ) = withContext(Dispatchers.IO) {
        if (libraryItemId.isBlank()) return@withContext
        val actualId = normalizedLibraryItemId(libraryItemId)
        val actualEpisodeId = episodeId ?: episodeIdFromProgressKey(libraryItemId)
        val key = mediaProgressKey(actualId, actualEpisodeId)
        val existing = progressDao.get(key)
        val shouldRecordServerUpdate = existing?.let {
            abs(it.currentTimeSec - serverTimeSec) > PositionChangeToleranceSec
        } ?: (serverTimeSec > PositionChangeToleranceSec)
        progressDao.upsert(
            MediaProgressEntity(
                progressKey = key,
                libraryItemId = actualId,
                currentTimeSec = serverTimeSec,
                durationSec = serverDurationSec,
                lastKnownServerLastUpdate = serverLastUpdate,
                localDirty = false,
                localRevision = existing?.localRevision ?: 0L,
                libraryId = libraryId ?: existing?.libraryId,
                episodeId = actualEpisodeId ?: existing?.episodeId,
                hideFromContinueListening = hideFromContinueListening,
                startedAt = startedAt ?: existing?.startedAt,
                finishedAt = finishedAt,
            ),
        )
        if (shouldRecordServerUpdate) {
            playbackHistoryRepository?.recordEvent(
                libraryItemId = actualId,
                eventType = PlaybackHistoryEventType.SERVER_UPDATE,
                positionSec = serverTimeSec,
            )
        }
    }

    suspend fun getLastPlayed(): MediaProgressEntity? = progressDao.getLastPlayed()

    private companion object {
        const val PositionChangeToleranceSec = 0.001
    }
}
