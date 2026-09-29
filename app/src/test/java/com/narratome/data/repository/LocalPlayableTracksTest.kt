package com.narratome.data.repository

import com.google.common.truth.Truth.assertThat
import com.narratome.domain.model.PlayableTrack
import org.junit.Test

class LocalPlayableTracksTest {

    @Test
    fun hasKnownTrackDurations_rejectsUnresolvedPart() {
        assertThat(
            hasKnownTrackDurations(
                listOf(
                    PlayableTrack("file:///one", 10.0),
                    PlayableTrack("file:///two", null),
                ),
            ),
        ).isFalse()
    }
}
