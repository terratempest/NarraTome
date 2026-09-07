package com.narratome.presentation.root

import androidx.lifecycle.ViewModel
import com.narratome.player.PlaybackResumeStore
import com.narratome.player.PlaybackResumeSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ContinueRouteViewModel @Inject constructor(
    private val playbackResumeStore: PlaybackResumeStore,
) : ViewModel() {

    suspend fun readResumeSnapshot(): PlaybackResumeSnapshot? =
        playbackResumeStore.readSnapshot()
}
