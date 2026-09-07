package com.narratome.data.local.preferences

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlaybackNotificationRetentionTest {

    @Test
    fun normalizePlaybackNotificationRetentionMinutes_usesDefaultForMissingOrInvalidValues() {
        assertThat(normalizePlaybackNotificationRetentionMinutes(null)).isEqualTo(10)
        assertThat(normalizePlaybackNotificationRetentionMinutes(0)).isEqualTo(10)
        assertThat(normalizePlaybackNotificationRetentionMinutes(11)).isEqualTo(10)
    }

    @Test
    fun normalizePlaybackNotificationRetentionMinutes_keepsAllowedValues() {
        assertThat(normalizePlaybackNotificationRetentionMinutes(1)).isEqualTo(1)
        assertThat(normalizePlaybackNotificationRetentionMinutes(5)).isEqualTo(5)
        assertThat(normalizePlaybackNotificationRetentionMinutes(10)).isEqualTo(10)
    }
}
