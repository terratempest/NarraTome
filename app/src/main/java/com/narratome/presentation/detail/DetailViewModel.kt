package com.narratome.presentation.detail

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.narratome.R
import com.narratome.data.local.db.BookmarkEntity
import com.narratome.data.local.db.DownloadJobEntity
import com.narratome.data.local.db.PlaybackHistoryEntity
import com.narratome.data.local.dto.decodeBookDetailFromCatalogPayload
import com.narratome.data.local.preferences.AppPreferencesRepository
import com.narratome.data.repository.BookmarkRepository
import com.narratome.data.repository.CoverCacheRepository
import com.narratome.data.repository.DownloadRepository
import com.narratome.data.repository.ItemRepository
import com.narratome.data.repository.PlaybackHistoryRepository
import com.narratome.data.repository.ProgressReconcileOutcome
import com.narratome.data.repository.ProgressRepository
import com.narratome.data.repository.ServerReachabilityRepository
import com.narratome.data.repository.mediaProgressKey
import com.narratome.domain.download.DownloadJobState
import com.narratome.domain.model.BookDetail
import com.narratome.domain.model.PodcastEpisode
import com.narratome.domain.model.SyncConflictPolicy
import com.narratome.domain.progress.ReconcileProgressUseCase
import com.narratome.domain.progress.ReconciledBookDetail
import com.narratome.player.PlaybackConnector
import com.narratome.sync.BackgroundWorkScheduler
import com.narratome.sync.DownloadForegroundService
import com.narratome.util.ErrorMapper
import com.narratome.util.formatByteCount
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

data class DetailUiState(
    val detail: BookDetail? = null,
    val isPlaying: Boolean = false,
    val message: String? = null,
    val busy: Boolean = false,
    val refreshing: Boolean = false,
    val progressConflict: ProgressReconcileOutcome.ChooseProgress? = null,
    val downloadJob: DownloadJobEntity? = null,
    val episodeDownloadJobs: Map<String, DownloadJobEntity> = emptyMap(),
    val downloadedEpisodeIds: Set<String> = emptySet(),
    val episodeDownloadSizeSubtitles: Map<String, String> = emptyMap(),
    val playbackHistory: List<PlaybackHistoryEntity> = emptyList(),
    val isFinished: Boolean = false,
    val localDownloadSizeBytes: Long? = null,
    val downloadSizeSubtitle: String? = null,
    val unavailableOffline: Boolean = false,
    val activePodcastEpisodeId: String? = null,
)

private data class DetailDisplayRow(
    val reconciled: ReconciledBookDetail?,
    val unavailableOffline: Boolean,
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val appContext: Context,
    private val itemRepository: ItemRepository,
    private val json: Json,
    private val bookmarkRepository: BookmarkRepository,
    private val playbackHistoryRepository: PlaybackHistoryRepository,
    private val progressRepository: ProgressRepository,
    private val preferences: AppPreferencesRepository,
    private val serverReachability: ServerReachabilityRepository,
    private val playbackConnector: PlaybackConnector,
    private val downloadRepository: DownloadRepository,
    private val coverCacheRepository: CoverCacheRepository,
    private val reconcileProgressUseCase: ReconcileProgressUseCase,
    private val workScheduler: BackgroundWorkScheduler,
) : ViewModel() {

    private val libraryId: String = checkNotNull(savedStateHandle["libraryId"])
    private val itemId: String = checkNotNull(savedStateHandle["itemId"])

    private val _ui = MutableStateFlow(DetailUiState())
    val ui: StateFlow<DetailUiState> = _ui.asStateFlow()

    val bookmarks: StateFlow<List<BookmarkEntity>> =
        bookmarkRepository.observeBookmarks(itemId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val coverRevision: StateFlow<Long> = coverCacheRepository.coverRevision

    init {
        viewModelScope.launch {
            combine(
                itemRepository.observeItemEntity(itemId),
                progressRepository.observeLocal(itemId),
                serverReachability.serverReachable,
            ) { catalogRow, localProgress, serverReachable ->
                if (!serverReachable && (catalogRow == null || !catalogRow.isDownloaded)) {
                    return@combine DetailDisplayRow(reconciled = null, unavailableOffline = true)
                }
                if (catalogRow == null) {
                    return@combine DetailDisplayRow(reconciled = null, unavailableOffline = false)
                }
                val rawServerDetail = decodeBookDetailFromCatalogPayload(catalogRow.payloadJson, json)
                    ?: return@combine DetailDisplayRow(reconciled = null, unavailableOffline = false)
                val displayDetail = if (!rawServerDetail.isPodcast && localProgress != null) {
                    val progressVal = if (localProgress.durationSec > 0) {
                        (localProgress.currentTimeSec / localProgress.durationSec).coerceIn(0.0, 1.0)
                    } else null
                    rawServerDetail.copy(
                        currentTimeSec = localProgress.currentTimeSec,
                        durationSec = if (localProgress.durationSec > 0) localProgress.durationSec else rawServerDetail.durationSec,
                        progress = progressVal,
                        finishedAt = localProgress.finishedAt,
                    )
                } else rawServerDetail
                DetailDisplayRow(
                    reconciled = ReconciledBookDetail(displayDetail, _ui.value.progressConflict),
                    unavailableOffline = false,
                )
            }.collect { row ->
                _ui.update { prev ->
                    val detail = row.reconciled?.detail
                    val activeEpisode = detail?.episodes?.firstOrNull { it.id == prev.activePodcastEpisodeId }
                    prev.copy(
                        detail = detail,
                        progressConflict = row.reconciled?.progressConflict,
                        isFinished = if (detail?.isPodcast == true) activeEpisode?.finishedAt != null else detail?.finishedAt != null,
                        unavailableOffline = row.unavailableOffline,
                    )
                }
            }
        }
        viewModelScope.launch {
            playbackConnector.playerState.collect { state ->
                _ui.update { prev ->
                    val sameItem = state.libraryItemId == itemId
                    val activeEpisodeId = if (sameItem) state.episodeId else prev.activePodcastEpisodeId
                    val activeEpisode = prev.detail?.episodes?.firstOrNull { it.id == activeEpisodeId }
                    prev.copy(
                        isPlaying = state.isPlaying && sameItem,
                        activePodcastEpisodeId = activeEpisodeId,
                        isFinished = if (prev.detail?.isPodcast == true) activeEpisode?.finishedAt != null else prev.isFinished,
                    )
                }
            }
        }
        viewModelScope.launch {
            playbackHistoryRepository.observeHistory(itemId).collect { history ->
                _ui.update { it.copy(playbackHistory = history) }
            }
        }
        viewModelScope.launch {
            if (serverReachability.serverReachable.value) {
                itemRepository.ensureItemHydrated(itemId, libraryId).onFailure { e ->
                    _ui.update { it.copy(message = if (it.detail != null) it.message else e.message) }
                }
            }
        }
        viewModelScope.launch {
            combine(
                downloadRepository.observeDownloadJobsForItem(itemId),
                downloadRepository.observePartsForCatalogItem(itemId),
                downloadRepository.observeAllDownloadManifests(),
            ) { jobs, _, manifests ->
                jobs to manifests.filter { it.libraryItemId == itemId }
            }.collect { (jobs, manifests) ->
                val bookJob = jobs.firstOrNull { it.episodeId.isNullOrBlank() }
                val bookManifest = manifests.firstOrNull { it.episodeId.isNullOrBlank() }
                val episodeJobs = jobs.mapNotNull { job -> job.episodeId?.let { it to job } }.toMap()
                val downloadedEpisodeIds = manifests.mapNotNull { it.episodeId }.toSet()
                val episodeSubtitles = withContext(Dispatchers.IO) {
                    (episodeJobs.keys + downloadedEpisodeIds).associateWith { episodeId ->
                        val job = episodeJobs[episodeId]
                        job?.let { activeSizeSubtitle(it) }
                            ?: downloadRepository.getLocalDownloadTotalBytesIfComplete(itemId, episodeId)?.let { formatByteCount(it) }
                            ?: "Downloaded"
                    }
                }
                val bookSubtitle = bookJob?.let { activeSizeSubtitle(it) }
                    ?: bookManifest?.let { formatByteCount(downloadRepository.getLocalDownloadTotalBytesIfComplete(itemId) ?: 0L) }
                val bookBytes = withContext(Dispatchers.IO) { downloadRepository.getLocalDownloadTotalBytesIfComplete(itemId) }
                _ui.update {
                    it.copy(
                        downloadJob = bookJob,
                        episodeDownloadJobs = episodeJobs,
                        downloadedEpisodeIds = downloadedEpisodeIds,
                        episodeDownloadSizeSubtitles = episodeSubtitles,
                        localDownloadSizeBytes = bookBytes,
                        downloadSizeSubtitle = bookSubtitle,
                    )
                }
            }
        }
    }

    private suspend fun activeSizeSubtitle(job: DownloadJobEntity): String? = when (job.state) {
        DownloadJobState.RUNNING, DownloadJobState.PAUSED_NETWORK -> when {
            job.bytesTotal > 0L -> "${formatByteCount(job.bytesDownloadedTotal)} / ${formatByteCount(job.bytesTotal)}"
            else -> downloadRepository.sumOnDiskPartBytes(job.libraryItemId, job.episodeId).takeIf { it > 0L }?.let { "${formatByteCount(it)} on device" }
        }
        DownloadJobState.QUEUED -> downloadRepository.sumOnDiskPartBytes(job.libraryItemId, job.episodeId).takeIf { it > 0L }?.let { "${formatByteCount(it)} on device" }
        else -> null
    }

    fun resolveCoverModel(itemId: String): Any? = coverCacheRepository.coverModelForItem(itemId)

    fun refreshFromPull() {
        viewModelScope.launch { refreshMetadata(forceCover = false) }
    }

    fun forceRefresh() {
        viewModelScope.launch { refreshMetadata(forceCover = true) }
    }

    private suspend fun refreshMetadata(forceCover: Boolean) {
        if (!serverReachability.serverReachable.value) {
            _ui.update { it.copy(refreshing = false, message = appContext.getString(R.string.detail_offline_refresh)) }
            return
        }
        _ui.update { it.copy(refreshing = true, message = null) }
        if (progressRepository.getMediaProgress(itemId)?.localDirty == true) progressRepository.pushDirtyToServer()
        if (forceCover) runCatching { coverCacheRepository.prefetchCover(itemId, force = true) }
        itemRepository.refreshItemFromServer(itemId, libraryId)
            .onSuccess { d ->
                val rec = if (d.isPodcast) ReconciledBookDetail(d, null) else reconcileProgressUseCase(itemId, d)
                _ui.update {
                    it.copy(
                        detail = rec.detail,
                        refreshing = false,
                        progressConflict = rec.progressConflict,
                        isFinished = if (rec.detail.isPodcast) {
                            rec.detail.episodes.firstOrNull { ep -> ep.id == it.activePodcastEpisodeId }?.finishedAt != null
                        } else {
                            rec.detail.finishedAt != null
                        },
                        message = null,
                    )
                }
                if (!d.isPodcast) runCatching { bookmarkRepository.refreshFromServerForItem(itemId) }
            }
            .onFailure { e ->
                _ui.update {
                    it.copy(
                        refreshing = false,
                        message = if (it.detail != null) appContext.getString(R.string.detail_refresh_failed_showing_cache) else ErrorMapper.map(appContext, e),
                    )
                }
            }
    }

    fun resolveProgressConflict(useServer: Boolean, rememberChoice: Boolean) {
        val conflict = _ui.value.progressConflict ?: return
        viewModelScope.launch {
            if (rememberChoice) preferences.setSyncConflictPolicy(if (useServer) SyncConflictPolicy.PREFER_SERVER else SyncConflictPolicy.PREFER_LOCAL)
            if (useServer) {
                progressRepository.applyServerProgressChoice(itemId, conflict.serverSec, conflict.durationSec, conflict.serverLastUpdate, finishedAt = conflict.finishedAt)
                _ui.update { it.copy(progressConflict = null, detail = it.detail?.copy(currentTimeSec = conflict.serverSec, finishedAt = conflict.finishedAt), isFinished = conflict.finishedAt != null) }
            } else {
                _ui.update { it.copy(progressConflict = null, detail = it.detail?.copy(currentTimeSec = conflict.localSec)) }
            }
        }
    }

    fun dismissProgressConflict() {
        _ui.update { it.copy(progressConflict = null) }
    }

    fun play() {
        viewModelScope.launch {
            val detail = _ui.value.detail
            if (detail?.isPodcast == true) {
                val activeEpisodeId = _ui.value.activePodcastEpisodeId
                if (playbackConnector.playerState.value.libraryItemId == itemId && activeEpisodeId != null) {
                    playbackConnector.resume()
                    return@launch
                }
                val episode = choosePodcastEpisode(detail) ?: run {
                    _ui.update { it.copy(message = "No podcast episodes found.") }
                    return@launch
                }
                startPodcastEpisodePlayback(episode)
                return@launch
            }
            if (playbackConnector.playerState.value.libraryItemId == itemId) playbackConnector.resume() else startPlaybackFromResolvedPosition()
        }
    }

    fun playEpisode(episodeId: String) {
        viewModelScope.launch {
            val episode = _ui.value.detail?.episodes?.firstOrNull { it.id == episodeId } ?: return@launch
            startPodcastEpisodePlayback(episode)
        }
    }

    fun pause() {
        viewModelScope.launch {
            playbackConnector.pause(progressRepository)
        }
    }

    fun toggleFinished() {
        val detail = _ui.value.detail ?: return
        if (detail.isPodcast) {
            val episode = _ui.value.activePodcastEpisodeId?.let { id -> detail.episodes.firstOrNull { it.id == id } }
                ?: choosePodcastEpisode(detail)
                ?: return
            val nextFinished = episode.finishedAt == null
            val optimisticFinishedAt = if (nextFinished) System.currentTimeMillis() else null
            _ui.update { state ->
                state.copy(
                    detail = state.detail?.copy(
                        episodes = state.detail.episodes.map { ep ->
                            if (ep.id == episode.id) ep.copy(finishedAt = optimisticFinishedAt) else ep
                        },
                    ),
                    activePodcastEpisodeId = episode.id,
                    isFinished = nextFinished,
                )
            }
            viewModelScope.launch {
                progressRepository.setFinished(
                    libraryItemId = itemId,
                    finished = nextFinished,
                    libraryId = libraryId,
                    currentTimeSec = episode.currentTimeSec,
                    durationSec = episode.durationSec,
                    episodeId = episode.id,
                )
                workScheduler.enqueueProgressPush()
            }
            return
        }

        val nextFinished = !_ui.value.isFinished
        val optimisticFinishedAt = if (nextFinished) System.currentTimeMillis() else null
        _ui.update { it.copy(isFinished = nextFinished, detail = it.detail?.copy(finishedAt = optimisticFinishedAt)) }
        viewModelScope.launch {
            progressRepository.setFinished(itemId, nextFinished, libraryId, detail.currentTimeSec, detail.durationSec)
            workScheduler.enqueueProgressPush()
            val finishedAt = progressRepository.getMediaProgress(itemId)?.finishedAt
            _ui.update { it.copy(isFinished = finishedAt != null, detail = it.detail?.copy(finishedAt = finishedAt)) }
        }
    }

    fun playFromTime(startTimeSec: Double) {
        viewModelScope.launch {
            val detail = _ui.value.detail
            if (detail?.isPodcast == true) {
                val episode = _ui.value.activePodcastEpisodeId?.let { id -> detail.episodes.firstOrNull { it.id == id } }
                    ?: choosePodcastEpisode(detail)
                    ?: return@launch
                if (playbackConnector.playerState.value.libraryItemId == itemId) {
                    playbackConnector.seekTo((startTimeSec * 1000).toLong(), progressRepository)
                } else {
                    startPodcastEpisodePlayback(episode, startTimeSecOverrideSec = startTimeSec)
                }
                return@launch
            }
            if (playbackConnector.playerState.value.libraryItemId == itemId) {
                playbackConnector.seekTo((startTimeSec * 1000).toLong(), progressRepository)
                return@launch
            }
            startPlaybackFromResolvedPosition(startTimeSecOverrideSec = startTimeSec)
        }
    }

    private suspend fun startPlaybackFromResolvedPosition(startTimeSecOverrideSec: Double? = null) {
        _ui.update { it.copy(busy = true, message = null) }
        var detail = _ui.value.detail ?: run {
            _ui.update { it.copy(busy = false) }
            return
        }
        val serverReachable = serverReachability.serverReachable.value
        val tracks = itemRepository.resolvePlayableUrls(itemId = itemId, allowRemoteFallback = serverReachable).getOrElse { e ->
            _ui.update { it.copy(busy = false, message = e.message) }
            return
        }
        val playingLocalDownload = tracks.all { it.url.startsWith("file:", ignoreCase = true) }
        if (!playingLocalDownload && serverReachable) refreshDetailForPlayback()?.let { detail = it }
        val startSec = startTimeSecOverrideSec ?: detail.currentTimeSec
        runCatching {
            val durationMs = (detail.durationSec * 1000).toLong()
            val rawStartMs = (startSec * 1000).toLong()
            val startPositionMs = if (durationMs > 0L && rawStartMs >= durationMs - 5_000L) 0L else rawStartMs
            playbackConnector.playPlaylist(
                tracks = tracks,
                libraryItemId = itemId,
                libraryId = libraryId,
                title = detail.title,
                author = detail.author,
                startPositionMs = startPositionMs,
                catalogDurationSec = detail.durationSec,
                seriesName = detail.seriesName,
                seriesSequence = detail.seriesSequence,
                seriesId = detail.seriesId,
                chapters = detail.chapters,
                progressRepository = progressRepository,
            )
        }
        _ui.update { it.copy(busy = false) }
        if (playingLocalDownload && serverReachability.serverReachable.value) viewModelScope.launch { refreshDetailForPlayback() }
    }

    private suspend fun startPodcastEpisodePlayback(episode: PodcastEpisode, startTimeSecOverrideSec: Double? = null) {
        _ui.update { it.copy(busy = true, message = null, activePodcastEpisodeId = episode.id) }
        val serverReachable = serverReachability.serverReachable.value
        val tracks = itemRepository.resolvePlayableUrls(
            itemId = itemId,
            episodeId = episode.id,
            allowRemoteFallback = serverReachable,
        ).getOrElse { e ->
            _ui.update { it.copy(busy = false, message = e.message) }
            return
        }
        val local = progressRepository.getMediaProgress(itemId, episode.id)
        val startSec = startTimeSecOverrideSec ?: local?.currentTimeSec ?: episode.currentTimeSec
        val durationSec = local?.durationSec?.takeIf { it > 0.0 } ?: episode.durationSec
        val rawStartMs = (startSec * 1000).toLong()
        val durationMs = (durationSec * 1000).toLong()
        val startPositionMs = if (durationMs > 0L && rawStartMs >= durationMs - 5_000L) 0L else rawStartMs
        playbackConnector.playPlaylist(
            tracks = tracks,
            libraryItemId = itemId,
            libraryId = libraryId,
            title = episode.title,
            author = _ui.value.detail?.author,
            startPositionMs = startPositionMs,
            catalogDurationSec = durationSec,
            chapters = emptyList(),
            progressRepository = progressRepository,
            episodeId = episode.id,
        )
        _ui.update { it.copy(busy = false, activePodcastEpisodeId = episode.id) }
    }

    private fun choosePodcastEpisode(detail: BookDetail): PodcastEpisode? =
        detail.episodes.firstOrNull { it.finishedAt == null && it.progress?.let { progress -> progress > 0.0 } == true } ?: 
            detail.episodes.firstOrNull { it.finishedAt == null } ?: 
            detail.episodes.firstOrNull()

    private suspend fun refreshDetailForPlayback(): BookDetail? = itemRepository.getBookDetail(itemId, libraryId).map { d ->
        val rec = if (d.isPodcast) ReconciledBookDetail(d, null) else reconcileProgressUseCase(itemId, d)
        _ui.update { it.copy(detail = rec.detail, progressConflict = rec.progressConflict, isFinished = rec.detail.finishedAt != null) }
        rec.detail
    }.getOrNull()

    fun enqueueDownload() {
        val detail = _ui.value.detail ?: return
        if (detail.isPodcast) {
            val episode = selectedPodcastEpisode() ?: return
            enqueueEpisodeDownload(episode.id)
        } else {
            DownloadForegroundService.startDownload(appContext, itemId, detail.title)
        }
    }

    fun enqueueEpisodeDownload(episodeId: String) {
        val episode = _ui.value.detail?.episodes?.firstOrNull { it.id == episodeId } ?: return
        DownloadForegroundService.startDownload(appContext, itemId, episode.title, episode.id)
    }

    fun cancelDownload() {
        val episodeId = if (_ui.value.detail?.isPodcast == true) selectedPodcastEpisode()?.id else null
        DownloadForegroundService.cancelDownload(appContext, itemId, episodeId)
    }

    fun cancelEpisodeDownload(episodeId: String) {
        DownloadForegroundService.cancelDownload(appContext, itemId, episodeId)
    }

    fun retryDownload() {
        val detail = _ui.value.detail ?: return
        if (detail.isPodcast) {
            val episode = selectedPodcastEpisode() ?: return
            retryEpisodeDownload(episode.id)
        } else {
            DownloadForegroundService.retryDownload(appContext, itemId, detail.title)
        }
    }

    fun retryEpisodeDownload(episodeId: String) {
        val episode = _ui.value.detail?.episodes?.firstOrNull { it.id == episodeId } ?: return
        DownloadForegroundService.retryDownload(appContext, itemId, episode.title, episode.id)
    }

    fun deleteDownload() {
        val detail = _ui.value.detail ?: return
        if (detail.isPodcast) {
            val episode = selectedPodcastEpisode() ?: return
            deleteEpisodeDownload(episode.id)
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            downloadRepository.deleteDownload(itemId)
            _ui.update { it.copy(busy = false) }
        }
    }

    fun deleteEpisodeDownload(episodeId: String) {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            downloadRepository.deleteDownload(itemId, episodeId)
            _ui.update { it.copy(busy = false) }
        }
    }

    private fun selectedPodcastEpisode(): PodcastEpisode? {
        val detail = _ui.value.detail ?: return null
        return _ui.value.activePodcastEpisodeId?.let { id -> detail.episodes.firstOrNull { it.id == id } }
            ?: choosePodcastEpisode(detail)
    }

    fun addBookmarkAtCurrentPosition(title: String) {
        val time = _ui.value.detail?.currentTimeSec ?: return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, message = null) }
            bookmarkRepository.createBookmark(itemId, time, title)
                .onSuccess { _ui.update { it.copy(busy = false) } }
                .onFailure { e -> _ui.update { it.copy(busy = false, message = e.message) } }
        }
    }

    fun removeBookmark(timeSec: Double) {
        viewModelScope.launch {
            bookmarkRepository.deleteBookmark(itemId, timeSec).onFailure { e -> _ui.update { it.copy(message = e.message) } }
        }
    }
}
