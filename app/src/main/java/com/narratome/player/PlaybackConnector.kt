package com.narratome.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.concurrent.futures.await
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import com.narratome.data.local.preferences.AppPreferencesRepository
import com.narratome.data.repository.CoverCacheRepository
import com.narratome.data.repository.ItemRepository
import com.narratome.data.repository.PlaybackHistoryEventType
import com.narratome.data.repository.PlaybackHistoryRepository
import com.narratome.data.repository.ProgressRepository
import com.narratome.di.ApplicationScope
import com.narratome.domain.model.BookChapter
import com.narratome.domain.model.PlayableTrack
import com.narratome.domain.model.displayTitleAtTimelineSec
import com.narratome.sync.BackgroundWorkScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Singleton
class PlaybackConnector @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val coverCacheRepository: CoverCacheRepository,
    private val itemRepository: ItemRepository,
    private val playbackHistoryRepository: PlaybackHistoryRepository,
    private val playbackResumeStore: PlaybackResumeStore,
    private val preferences: AppPreferencesRepository,
    private val scheduler: BackgroundWorkScheduler,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) {
    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private var controller: MediaController? = null
    private var listenerAttached = false
    private val timelineWindow = Timeline.Window()
    private var positionTicker: Job? = null
    private var currentTracks: List<PlayableTrack> = emptyList()
    private var activeItemHydrationJob: Job? = null
    private var activeItemHydrationId: String? = null
    private var stoppedSession = false
    private var suppressSeekHistoryUntilEpochMs = 0L
    private var lastRecordedHistoryEvent: RecordedHistoryEvent? = null

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val _chapterEndFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val chapterEndFlow: SharedFlow<Unit> = _chapterEndFlow.asSharedFlow()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            controller?.let { c ->
                recordPlaybackStateChange(c, isPlaying)
                publish(c)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            controller?.let { publish(it) }
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            controller?.let { publish(it) }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            controller?.let { c ->
                recordSeekIfNeeded(c, reason)
                publish(c)
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            controller?.let { publish(it) }
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            controller?.let { publish(it) }
        }
    }

    suspend fun attachToSession() {
        connect(startService = false)
    }

    suspend fun playFromSearch(query: String) {
        // Binding starts the service; Media3 promotes it when playback actually begins.
        attachToSession()
        if (query.isNotBlank()) {
            val browser = controller as? MediaBrowser ?: return
            browser.search(query, null).await()
            val results = browser.getSearchResult(query, 0, 1, null).await().value
            if (results.isNullOrEmpty()) throw NoSuchElementException("No matching audiobook available")
        }
        val item = MediaItem.Builder().setRequestMetadata(
            MediaItem.RequestMetadata.Builder().setSearchQuery(query).build(),
        ).build()
        controller?.apply {
            setMediaItem(item)
            prepare()
            play()
        }
    }

    suspend fun ensureConnected() {
        connect(startService = true)
    }

    private suspend fun connect(startService: Boolean) {
        if (controller != null) return
        if (startService) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AudiobookPlaybackService::class.java),
            )
        }
        val token = SessionToken(
            context,
            ComponentName(context, AudiobookPlaybackService::class.java),
        )
        val future = MediaBrowser.Builder(context, token).buildAsync()
        val c = future.await()
        controller = c
        if (!listenerAttached) {
            c.addListener(listener)
            listenerAttached = true
        }
        publish(c)
    }

    private fun publish(c: MediaController, syncPositionTicker: Boolean = true) {
        if (stoppedSession) {
            _playerState.update { PlayerState() }
            if (syncPositionTicker) updatePlaybackPositionTicker(false)
            return
        }

        val item = c.currentMediaItem
        val metadata = item?.mediaMetadata
        val activeLibraryItemId = playbackLibraryItemIdFromMediaId(item?.mediaId)
        val activeEpisodeId = playbackEpisodeIdFromMediaId(item?.mediaId)

        _playerState.update { prev ->
            val scopedPrev = playerStateForActiveItem(prev, activeLibraryItemId, activeEpisodeId)
            val libraryItemId = activeLibraryItemId ?: scopedPrev.libraryItemId
            val episodeId = activeEpisodeId ?: scopedPrev.episodeId
            val sessionActive = libraryItemId != null && item != null
            val positionMs = if (sessionActive) cumulativePositionMs(c) else 0L
            val chapters = if (sessionActive) scopedPrev.chapters else emptyList()
            val chapterTitle = if (sessionActive && chapters.isNotEmpty()) {
                chapters.displayTitleAtTimelineSec(positionMs / 1000.0)
            } else null
            val metadataMatches = activeLibraryItemId == libraryItemId

            PlayerState(
                isPlaying = c.isPlaying,
                positionMs = positionMs,
                durationMs = if (sessionActive) cumulativeDurationMs(c) else 0L,
                title = if (sessionActive) {
                    if (metadataMatches) metadata?.title?.toString().nonBlankOrNull() ?: scopedPrev.title.nonBlankOrNull()
                    else scopedPrev.title.nonBlankOrNull()
                } else null,
                author = if (sessionActive) {
                    if (metadataMatches) metadata?.artist?.toString().nonBlankOrNull() ?: scopedPrev.author.nonBlankOrNull()
                    else scopedPrev.author.nonBlankOrNull()
                } else null,
                coverPath = if (sessionActive) {
                    if (metadataMatches) metadata?.artworkUri?.toString().nonBlankOrNull() ?: scopedPrev.coverPath.nonBlankOrNull()
                    else scopedPrev.coverPath.nonBlankOrNull()
                } else null,
                libraryItemId = libraryItemId,
                episodeId = episodeId,
                libraryId = scopedPrev.libraryId,
                catalogDurationSec = scopedPrev.catalogDurationSec,
                seriesName = if (sessionActive) scopedPrev.seriesName else null,
                seriesSequence = if (sessionActive) scopedPrev.seriesSequence else null,
                seriesId = if (sessionActive) scopedPrev.seriesId else null,
                chapters = chapters,
                currentChapterTitle = chapterTitle,
                playbackSpeed = if (sessionActive) normalizePlaybackSpeed(c.playbackParameters.speed) else 1f,
            )
        }

        if (activeLibraryItemId != null) {
            hydrateActiveItemFromCache(activeLibraryItemId)
        }
        if (syncPositionTicker) {
            updatePlaybackPositionTicker(c.isPlaying)
        }
    }

    private fun hydrateActiveItemFromCache(libraryItemId: String) {
        val current = _playerState.value
        if (
            current.libraryItemId != libraryItemId ||
            (activeItemHydrationId == libraryItemId && activeItemHydrationJob?.isActive == true) ||
            (activeItemHydrationId == libraryItemId && current.libraryId != null && current.catalogDurationSec != null)
        ) {
            return
        }
        activeItemHydrationJob?.cancel()
        activeItemHydrationId = libraryItemId
        activeItemHydrationJob = applicationScope.launch(Dispatchers.IO) {
            val detail = itemRepository.getCachedBookDetailOrNull(libraryItemId)
            val catalogRow = if (detail == null) itemRepository.getById(libraryItemId) else null
            withContext(Dispatchers.Main.immediate) {
                _playerState.update { prev ->
                    if (prev.libraryItemId != libraryItemId) {
                        prev
                    } else if (detail != null) {
                        prev.copy(
                            libraryId = detail.libraryId,
                            title = prev.title.nonBlankOrNull() ?: detail.title,
                            author = prev.author.nonBlankOrNull() ?: detail.author,
                            catalogDurationSec = prev.catalogDurationSec ?: detail.durationSec,
                            seriesName = prev.seriesName.nonBlankOrNull() ?: detail.seriesName,
                            seriesSequence = prev.seriesSequence.nonBlankOrNull() ?: detail.seriesSequence,
                            seriesId = prev.seriesId.nonBlankOrNull() ?: detail.seriesId,
                            chapters = if (prev.episodeId == null) detail.chapters else prev.chapters,
                            currentChapterTitle = if (prev.episodeId == null) {
                                detail.chapters.displayTitleAtTimelineSec(prev.positionMs / 1000.0)
                            } else {
                                prev.currentChapterTitle
                            },
                        )
                    } else if (catalogRow != null) {
                        prev.copy(
                            libraryId = catalogRow.libraryId,
                            title = prev.title.nonBlankOrNull() ?: catalogRow.title,
                            author = prev.author.nonBlankOrNull() ?: catalogRow.author,
                            seriesName = prev.seriesName.nonBlankOrNull() ?: catalogRow.seriesName,
                            seriesId = prev.seriesId.nonBlankOrNull() ?: catalogRow.seriesId,
                        )
                    } else {
                        prev
                    }
                }
            }
        }
    }

    private fun updatePlaybackPositionTicker(playing: Boolean) {
        if (!playing) {
            positionTicker?.cancel()
            positionTicker = null
            return
        }
        if (positionTicker?.isActive == true) return
        positionTicker = applicationScope.launch {
            while (isActive) {
                delay(500)
                val keepTicking = withContext(Dispatchers.Main.immediate) {
                    val ctl = controller ?: return@withContext false
                    if (!ctl.isPlaying) return@withContext false
                    publish(ctl, syncPositionTicker = false)
                    true
                }
                if (!keepTicking) break
            }
        }
    }

    private fun cumulativePositionMs(player: Player): Long {
        if (player.mediaItemCount <= 1) return player.currentPosition.coerceAtLeast(0L)
        return cumulativePositionMs(
            currentIndex = player.currentMediaItemIndex,
            currentPositionMs = player.currentPosition,
            itemDurationsMs = itemDurationsMs(player),
        )
    }

    private fun cumulativeDurationMs(player: Player): Long {
        if (player.mediaItemCount <= 1) {
            val d = if (player.duration > 0) player.duration else 0L
            val catalogDurMs = (_playerState.value.catalogDurationSec?.let { (it * 1000).toLong() }) ?: 0L
            return maxOf(d, catalogDurMs)
        }
        val total = itemDurationsMs(player).filterKnownDurations()?.sum() ?: 0L
        val catalogDurMs = (_playerState.value.catalogDurationSec?.let { (it * 1000).toLong() }) ?: 0L
        return maxOf(total, catalogDurMs)
    }

    private fun itemDurationsMs(player: Player): List<Long> {
        val timeline = player.currentTimeline
        val itemCount = player.mediaItemCount
        return (0 until itemCount).map { index ->
            val timelineDurationMs = if (!timeline.isEmpty && index < timeline.windowCount) {
                timeline.getWindow(index, timelineWindow)
                timelineWindow.durationMs
            } else {
                C.TIME_UNSET
            }
            if (timelineDurationMs != C.TIME_UNSET) timelineDurationMs
            else currentTracks.getOrNull(index)?.durationSec?.let { (it * 1000).toLong() } ?: C.TIME_UNSET
        }
    }

    suspend fun playUrl(url: String, libraryItemId: String? = null) {
        playPlaylist(listOf(PlayableTrack(url)), libraryItemId, null, null)
    }

    suspend fun playPlaylist(
        tracks: List<PlayableTrack>,
        libraryItemId: String? = null,
        libraryId: String? = null,
        title: String? = null,
        author: String? = null,
        coverUrl: String? = null,
        startPositionMs: Long = 0L,
        catalogDurationSec: Double? = null,
        seriesName: String? = null,
        seriesSequence: String? = null,
        seriesId: String? = null,
        chapters: List<BookChapter> = emptyList(),
        progressRepository: ProgressRepository? = null,
        episodeId: String? = null,
    ) {
        if (tracks.isEmpty()) return
        ensureConnected()
        val c = controller ?: return
        stoppedSession = false

        val startPosition = MediaItemPosition(index = 0, positionMs = startPositionMs)

        if (libraryItemId != null) {
            _playerState.update {
                it.copy(
                    libraryItemId = libraryItemId,
                    episodeId = episodeId,
                    libraryId = libraryId,
                    title = title,
                    author = author,
                    coverPath = coverUrl,
                    catalogDurationSec = catalogDurationSec,
                    seriesName = seriesName,
                    seriesSequence = seriesSequence,
                    seriesId = seriesId,
                    chapters = chapters,
                    currentChapterTitle = chapters.displayTitleAtTimelineSec(startPositionMs / 1000.0),
                )
            }
            currentTracks = tracks
            playbackResumeStore.save(
                libraryId = libraryId,
                libraryItemId = libraryItemId,
                windowIndex = startPosition.index,
                positionMs = startPositionMs,
            )

            if (progressRepository != null) {
                val existing = progressRepository.getMediaProgress(libraryItemId, episodeId)
                if (existing?.startedAt == null) {
                    progressRepository.updateLocalProgress(
                        libraryItemId = libraryItemId,
                        currentTimeSec = startPositionMs / 1000.0,
                        durationSec = catalogDurationSec ?: existing?.durationSec ?: 0.0,
                        markDirty = true,
                        libraryId = libraryId,
                        episodeId = episodeId,
                        startedAt = System.currentTimeMillis(),
                    )
                }
            }
        }

        val artworkUri = libraryItemId?.let { playbackArtworkUri(context, coverCacheRepository, it) }
            ?: coverUrl?.let { android.net.Uri.parse(it) }

        val items = if (libraryItemId != null && episodeId.isNullOrBlank()) {
            listOf(
                MediaItem.Builder()
                    .setMediaId(playbackItemMediaId(libraryItemId))
                    .setMediaMetadata(
                        androidx.media3.common.MediaMetadata.Builder()
                            .setTitle(title)
                            .setArtist(author)
                            .apply { if (artworkUri != null) setArtworkUri(artworkUri) }
                            .build(),
                    )
                    .build(),
            )
        } else {
            tracks.mapIndexed { index, track ->
                MediaItem.Builder()
                    .apply { playbackMediaId(libraryItemId, index, episodeId)?.let(::setMediaId) }
                    .setUri(track.url)
                    .setMediaMetadata(
                        androidx.media3.common.MediaMetadata.Builder()
                            .setTitle(title)
                            .setArtist(author)
                            .apply { if (artworkUri != null) setArtworkUri(artworkUri) }
                            .build(),
                    )
                    .build()
            }
        }

        suppressSeekHistoryBriefly()
        c.setMediaItems(items, startPosition.index, startPosition.positionMs)
        c.prepare()
        c.play()
        if (libraryItemId != null) {
            recordHistoryEvent(
                libraryItemId = libraryItemId,
                eventType = PlaybackHistoryEventType.PLAY,
                positionSec = startPositionMs / 1000.0,
            )
        }
    }

    suspend fun persistProgressIfPlaying(progressRepository: ProgressRepository) {
        val snap = withContext(Dispatchers.Main.immediate) {
            val c = controller ?: return@withContext null
            val state = _playerState.value
            val id = state.libraryItemId ?: return@withContext null
            val libId = state.libraryId
            val episodeId = state.episodeId
            val catalogDurSec = state.catalogDurationSec ?: 0.0
            val playerDurMs = cumulativeDurationMs(c)
            val catalogDurMs = (catalogDurSec * 1000.0).toLong().coerceAtLeast(0L)
            val durMs = maxOf(playerDurMs, catalogDurMs)
            if (durMs <= 0L) return@withContext null
            val positionMs = cumulativePositionMs(c)
            LocalProgressSnapshot(
                libraryItemId = id,
                episodeId = episodeId,
                libraryId = libId,
                currentTimeSec = positionMs / 1000.0,
                durationSec = durMs / 1000.0,
                windowIndex = c.currentMediaItemIndex,
                positionMs = positionMs,
            )
        } ?: return

        progressRepository.updateLocalProgress(
            libraryItemId = snap.libraryItemId,
            currentTimeSec = snap.currentTimeSec,
            durationSec = snap.durationSec,
            markDirty = true,
            libraryId = snap.libraryId,
            episodeId = snap.episodeId,
        )
        playbackResumeStore.save(
            libraryId = snap.libraryId,
            libraryItemId = snap.libraryItemId,
            windowIndex = snap.windowIndex,
            positionMs = snap.positionMs,
        )
        scheduler.enqueueProgressPush()
    }

    suspend fun pause(progressRepository: ProgressRepository? = null) {
        if (progressRepository != null) persistProgressIfPlaying(progressRepository)
        controller?.pause()
    }

    suspend fun resume() {
        ensureConnected()
        val c = controller ?: return
        if (c.playbackState == Player.STATE_ENDED) c.seekTo(0L)
        c.play()
    }

    suspend fun skipForward(progressRepository: ProgressRepository? = null) {
        controller?.let { c ->
            val seekMs = preferences.seekForwardSeconds.first() * 1000L
            seekTo(cumulativePositionMs(c) + seekMs, progressRepository)
        }
    }

    suspend fun skipBack(progressRepository: ProgressRepository? = null) {
        controller?.let { c ->
            val seekMs = preferences.seekBackSeconds.first() * 1000L
            seekTo(cumulativePositionMs(c) - seekMs, progressRepository)
        }
    }

    suspend fun nextChapter(progressRepository: ProgressRepository? = null) {
        val chapters = _playerState.value.chapters
        if (chapters.isNotEmpty()) {
            val currentPosSec = _playerState.value.positionMs / 1000.0
            val nextChapter = chapters.sortedBy { it.startSec }.firstOrNull { it.startSec > currentPosSec + 1.0 }
            if (nextChapter != null) {
                seekTo((nextChapter.startSec * 1000).toLong(), progressRepository)
                return
            }
        }
        controller?.seekToNextMediaItem()
        if (progressRepository != null) persistProgressIfPlaying(progressRepository)
    }

    suspend fun previousChapter(progressRepository: ProgressRepository? = null) {
        val chapters = _playerState.value.chapters
        if (chapters.isNotEmpty()) {
            val currentPosSec = _playerState.value.positionMs / 1000.0
            val sorted = chapters.sortedBy { it.startSec }
            val currentChapter = sorted.lastOrNull { it.startSec <= currentPosSec + 0.1 }
            if (currentChapter != null) {
                val index = sorted.indexOf(currentChapter)
                if (currentPosSec - currentChapter.startSec > 3.0) {
                    seekTo((currentChapter.startSec * 1000).toLong(), progressRepository)
                } else if (index > 0) {
                    seekTo((sorted[index - 1].startSec * 1000).toLong(), progressRepository)
                } else {
                    seekTo(0, progressRepository)
                }
                return
            }
        }
        controller?.seekToPreviousMediaItem()
        if (progressRepository != null) persistProgressIfPlaying(progressRepository)
    }

    suspend fun stop(progressRepository: ProgressRepository? = null) {
        val pauseSnapshot = _playerState.value
        if (pauseSnapshot.libraryItemId != null) {
            recordHistoryEvent(
                libraryItemId = pauseSnapshot.libraryItemId,
                eventType = PlaybackHistoryEventType.PAUSE,
                positionSec = pauseSnapshot.positionMs / 1000.0,
            )
        }
        if (progressRepository != null) persistProgressIfPlaying(progressRepository)
        positionTicker?.cancel()
        positionTicker = null
        currentTracks = emptyList()
        activeItemHydrationJob?.cancel()
        activeItemHydrationJob = null
        activeItemHydrationId = null
        stoppedSession = true
        _playerState.update { PlayerState() }
        controller?.stop()
        controller?.clearMediaItems()
    }

    suspend fun seekTo(ms: Long, progressRepository: ProgressRepository? = null) {
        controller?.let { c ->
            val totalDuration = cumulativeDurationMs(c)
            val targetPos = ms.coerceIn(0, totalDuration)
            val targetPosition = mediaItemPositionForBookPosition(targetPos, itemDurationsMs(c))
            suppressSeekHistoryBriefly()
            c.seekTo(targetPosition.index, targetPosition.positionMs)
            _playerState.value.libraryItemId?.let { id ->
                recordHistoryEvent(id, PlaybackHistoryEventType.SEEK, targetPos / 1000.0)
            }
        }
        if (progressRepository != null) persistProgressIfPlaying(progressRepository)
    }

    suspend fun setPlaybackSpeed(speed: Float) {
        val normalized = normalizePlaybackSpeed(speed)
        withContext(Dispatchers.Main.immediate) {
            controller?.setPlaybackSpeed(normalized)
            _playerState.update { it.copy(playbackSpeed = normalized) }
        }
    }

    fun release() {
        positionTicker?.cancel()
        positionTicker = null
        activeItemHydrationId = null
        stoppedSession = true
        _playerState.update { PlayerState() }
        controller?.removeListener(listener)
        controller?.release()
        controller = null
        listenerAttached = false
    }

    fun refresh() {
        controller?.let { publish(it) }
    }

    fun setVolume(volume: Float) {
        mainExecutor.execute { controller?.volume = volume.coerceIn(0f, 1f) }
    }

    suspend fun pauseSilent() {
        withContext(Dispatchers.Main) {
            controller?.pause()
        }
    }

    private fun recordPlaybackStateChange(c: MediaController, isPlaying: Boolean) {
        val previous = _playerState.value
        val id = previous.libraryItemId ?: return
        if (previous.isPlaying == isPlaying) return
        val eventType = if (isPlaying) PlaybackHistoryEventType.PLAY else PlaybackHistoryEventType.PAUSE
        val positionSec = cumulativePositionMs(c) / 1000.0
        recordHistoryEvent(id, eventType, positionSec)
    }

    private fun recordSeekIfNeeded(c: MediaController, reason: Int) {
        if (System.currentTimeMillis() < suppressSeekHistoryUntilEpochMs) return
        if (reason != Player.DISCONTINUITY_REASON_SEEK && reason != Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) return
        val id = _playerState.value.libraryItemId ?: return
        recordHistoryEvent(id, PlaybackHistoryEventType.SEEK, cumulativePositionMs(c) / 1000.0)
    }

    private fun suppressSeekHistoryBriefly() {
        suppressSeekHistoryUntilEpochMs = System.currentTimeMillis() + SeekHistorySuppressionWindowMs
    }

    private fun recordHistoryEvent(
        libraryItemId: String,
        eventType: PlaybackHistoryEventType,
        positionSec: Double,
    ) {
        val now = System.currentTimeMillis()
        val last = lastRecordedHistoryEvent
        if (
            last != null &&
            last.libraryItemId == libraryItemId &&
            last.eventType == eventType &&
            kotlin.math.abs(last.positionSec - positionSec) < HistoryPositionToleranceSec &&
            now - last.recordedAtEpochMs < HistoryDuplicateWindowMs
        ) {
            return
        }
        lastRecordedHistoryEvent = RecordedHistoryEvent(libraryItemId, eventType, positionSec, now)
        applicationScope.launch {
            playbackHistoryRepository.recordEvent(libraryItemId, eventType, positionSec)
        }
    }

    private companion object {
        const val SeekHistorySuppressionWindowMs = 1_000L
        const val HistoryDuplicateWindowMs = 1_000L
        const val HistoryPositionToleranceSec = 0.25
    }
}

data class PlayerState(
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val title: String? = null,
    val author: String? = null,
    val coverPath: String? = null,
    val libraryItemId: String? = null,
    val episodeId: String? = null,
    val libraryId: String? = null,
    val catalogDurationSec: Double? = null,
    val seriesName: String? = null,
    val seriesSequence: String? = null,
    val seriesId: String? = null,
    val chapters: List<BookChapter> = emptyList(),
    val currentChapterTitle: String? = null,
    val playbackSpeed: Float = 1f,
)

internal fun playerStateForActiveItem(
    previous: PlayerState,
    activeLibraryItemId: String?,
    activeEpisodeId: String? = null,
): PlayerState {
    if (activeLibraryItemId == null) return previous
    if (activeLibraryItemId == previous.libraryItemId && activeEpisodeId == previous.episodeId) return previous
    return PlayerState(libraryItemId = activeLibraryItemId, episodeId = activeEpisodeId)
}

private fun normalizePlaybackSpeed(speed: Float): Float =
    ((speed.coerceIn(0.5f, 3.0f) * 10f).roundToInt() / 10f)

private fun String?.nonBlankOrNull(): String? = this?.takeIf { it.isNotBlank() }

private data class LocalProgressSnapshot(
    val libraryItemId: String,
    val episodeId: String?,
    val libraryId: String?,
    val currentTimeSec: Double,
    val durationSec: Double,
    val windowIndex: Int,
    val positionMs: Long,
)

private data class RecordedHistoryEvent(
    val libraryItemId: String,
    val eventType: PlaybackHistoryEventType,
    val positionSec: Double,
    val recordedAtEpochMs: Long,
)
