package com.narratome.presentation.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.narratome.data.local.db.BookmarkEntity
import com.narratome.data.repository.BookmarkRepository
import com.narratome.data.repository.CoverCacheRepository
import com.narratome.data.repository.DownloadRepository
import com.narratome.data.repository.ProgressRepository
import com.narratome.player.PlaybackConnector
import com.narratome.player.PlayerState
import com.narratome.player.SleepTimerManager
import com.narratome.player.SleepTimerOption
import com.narratome.player.SleepTimerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MiniPlayerViewModel @Inject constructor(
    private val playback: PlaybackConnector,
    private val progressRepository: ProgressRepository,
    private val sleepTimer: SleepTimerManager,
    private val downloadRepository: DownloadRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val coverCacheRepository: CoverCacheRepository,
) : ViewModel() {

    val playerState = playback.playerState.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        PlayerState(),
    )

    val sleepTimerState = sleepTimer.state.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        SleepTimerState(),
    )

    val activeDownloadCount = downloadRepository.observeActiveDownloadCount().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        0,
    )

    val bookmarksForActiveItem = playback.playerState
        .flatMapLatest { p ->
            val id = p.libraryItemId
            if (id == null) flowOf(emptyList<BookmarkEntity>())
            else bookmarkRepository.observeBookmarks(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<BookmarkEntity>())

    val coverRevision: StateFlow<Long> = coverCacheRepository.coverRevision
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    fun resolveCoverModel(itemId: String): Any? = coverCacheRepository.coverModelForItem(itemId)

    init {
        viewModelScope.launch {
            playback.attachToSession()
        }
        viewModelScope.launch {
            while (isActive) {
                delay(25_000)
                if (playback.playerState.value.libraryItemId != null) {
                    playback.persistProgressIfPlaying(progressRepository)
                }
            }
        }
    }

    fun pause() {
        viewModelScope.launch { playback.pause(progressRepository) }
    }

    fun resume() {
        viewModelScope.launch { playback.resume() }
    }

    fun skipForward() {
        viewModelScope.launch { playback.skipForward(progressRepository) }
    }

    fun skipBack() {
        viewModelScope.launch { playback.skipBack(progressRepository) }
    }

    fun nextChapter() {
        viewModelScope.launch { playback.nextChapter(progressRepository) }
    }

    fun previousChapter() {
        viewModelScope.launch { playback.previousChapter(progressRepository) }
    }

    /** Await this from UI (e.g. full player stop) so state clears before the sheet dismisses. */
    suspend fun stop() {
        playback.stop(progressRepository)
        sleepTimer.cancel()
    }

    fun seekTo(ms: Long) {
        viewModelScope.launch { playback.seekTo(ms, progressRepository) }
    }

    fun setSpeed(speed: Float) {
        viewModelScope.launch { playback.setPlaybackSpeed(speed) }
    }

    fun startSleepTimer(option: SleepTimerOption) {
        sleepTimer.start(option)
    }

    fun cancelSleepTimer() {
        sleepTimer.cancel()
    }

    fun createBookmarkAtCurrentPosition(title: String) {
        val id = playerState.value.libraryItemId ?: return
        val timeSec = playerState.value.positionMs / 1000.0
        viewModelScope.launch {
            bookmarkRepository.createBookmark(id, timeSec, title)
        }
    }

    fun deleteBookmark(timeSec: Double) {
        val id = playerState.value.libraryItemId ?: return
        viewModelScope.launch {
            bookmarkRepository.deleteBookmark(id, timeSec)
        }
    }
}
