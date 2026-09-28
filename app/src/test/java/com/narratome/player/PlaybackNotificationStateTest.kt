package com.narratome.player

import androidx.media3.common.Player
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlaybackNotificationStateTest {

    @Test
    fun networkParkedItem_isExposedAsPausedUntilItIsResumedOrCleared() {
        assertThat(
            playbackStateForRetainedPause(
                playbackState = Player.STATE_IDLE,
                hasCurrentMediaItem = true,
                playWhenReady = false,
                retainedPausedSession = true,
            ),
        ).isEqualTo(Player.STATE_READY)
        assertThat(
            playbackStateForRetainedPause(
                playbackState = Player.STATE_IDLE,
                hasCurrentMediaItem = false,
                playWhenReady = false,
                retainedPausedSession = true,
            ),
        ).isEqualTo(Player.STATE_IDLE)
        assertThat(
            playbackStateForRetainedPause(
                playbackState = Player.STATE_IDLE,
                hasCurrentMediaItem = true,
                playWhenReady = true,
                retainedPausedSession = true,
            ),
        ).isEqualTo(Player.STATE_IDLE)
        assertThat(
            playbackStateForRetainedPause(
                playbackState = Player.STATE_IDLE,
                hasCurrentMediaItem = true,
                playWhenReady = false,
                retainedPausedSession = false,
            ),
        ).isEqualTo(Player.STATE_IDLE)
    }
}
