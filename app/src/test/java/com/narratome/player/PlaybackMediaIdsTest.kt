package com.narratome.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlaybackMediaIdsTest {

    @Test
    fun playbackLibraryItemIdFromMediaId_extractsPlainItemId() {
        assertThat(playbackLibraryItemIdFromMediaId("item:abc")).isEqualTo("abc")
    }

    @Test
    fun playbackLibraryItemIdFromMediaId_extractsItemIdBeforeTrackSuffix() {
        assertThat(playbackLibraryItemIdFromMediaId("item:abc#0")).isEqualTo("abc")
    }

    @Test
    fun playbackLibraryItemIdFromMediaId_rejectsInvalidIds() {
        assertThat(playbackLibraryItemIdFromMediaId(null)).isNull()
        assertThat(playbackLibraryItemIdFromMediaId("")).isNull()
        assertThat(playbackLibraryItemIdFromMediaId("abc")).isNull()
        assertThat(playbackLibraryItemIdFromMediaId("item:")).isNull()
    }

    @Test
    fun playbackMediaId_buildsStableTrackId() {
        assertThat(playbackMediaId("abc", 2)).isEqualTo("item:abc#2")
    }
}
