package com.narratome.player

import androidx.media3.common.Player

/**
 * A network-parked item is still a resumable paused session even though ExoPlayer is idle.
 * Keep Media3's session-facing state paused until the item is resumed or cleared.
 */
internal fun playbackStateForRetainedPause(
    playbackState: Int,
    hasCurrentMediaItem: Boolean,
    playWhenReady: Boolean,
    retainedPausedSession: Boolean,
): Int =
    if (
        retainedPausedSession &&
        hasCurrentMediaItem &&
        !playWhenReady &&
        playbackState == Player.STATE_IDLE
    ) {
        Player.STATE_READY
    } else {
        playbackState
    }
