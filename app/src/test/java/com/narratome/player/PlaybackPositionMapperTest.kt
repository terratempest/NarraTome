package com.narratome.player

import androidx.media3.common.C
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlaybackPositionMapperTest {

    @Test
    fun mediaItemPositionForBookPosition_mapsCumulativePositionIntoItemOffset() {
        val result = mediaItemPositionForBookPosition(
            bookPositionMs = 185_000L,
            itemDurationsMs = listOf(60_000L, 120_000L, 90_000L),
        )

        assertThat(result).isEqualTo(MediaItemPosition(index = 2, positionMs = 5_000L))
    }

    @Test
    fun mediaItemPositionForBookPosition_clampsPastEndToFinalItem() {
        val result = mediaItemPositionForBookPosition(
            bookPositionMs = 300_000L,
            itemDurationsMs = listOf(60_000L, 120_000L, 90_000L),
        )

        assertThat(result).isEqualTo(MediaItemPosition(index = 2, positionMs = 90_000L))
    }

    @Test
    fun mediaItemPositionForBookPosition_stopsAtFirstUnknownDuration() {
        val result = mediaItemPositionForBookPosition(
            bookPositionMs = 185_000L,
            itemDurationsMs = listOf(C.TIME_UNSET, C.TIME_UNSET, C.TIME_UNSET),
        )

        assertThat(result).isEqualTo(MediaItemPosition(index = 0, positionMs = 185_000L))
    }

    @Test
    fun cumulativePositionMs_addsKnownDurationsBeforeCurrentItem() {
        val result = cumulativePositionMs(
            currentIndex = 2,
            currentPositionMs = 3_000L,
            itemDurationsMs = listOf(60_000L, 120_000L, 90_000L),
        )

        assertThat(result).isEqualTo(183_000L)
    }

    @Test
    fun filterKnownDurations_rejectsUnsetDurations() {
        val result = listOf(60_000L, C.TIME_UNSET, 90_000L).filterKnownDurations()

        assertThat(result).isNull()
    }
}
