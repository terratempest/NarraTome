package com.narratome.player

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BookPlaybackRecipeTest {

    @Test
    fun placeholderDurationsMs_preservesKnownPartDurations() {
        val durations = placeholderDurationsMs(
            trackDurationsMs = listOf(60_000L, C.TIME_UNSET, 90_000L),
            bookDurationMs = 210_000L,
        )

        assertThat(durations).isEqualTo(listOf(60_000L, 60_000L, 90_000L))
    }

    @Test
    fun recipe_keepsOneOuterBookItemAndOrderedParts() {
        val recipe = BookPlaybackRecipe(
            mediaItem = MediaItem.Builder().setMediaId("item:book-1").build(),
            parts = listOf(
                BookPlaybackPart(MediaItem.Builder().setMediaId("part-1").build(), 60_000L),
                BookPlaybackPart(MediaItem.Builder().setMediaId("part-2").build(), 120_000L),
            ),
            canonicalDurationMs = 180_000L,
            hasPlaceholderDurations = false,
        )

        assertThat(recipe.mediaItem.mediaId).isEqualTo("item:book-1")
        assertThat(recipe.parts.map { it.mediaItem.mediaId }).isEqualTo(listOf("part-1", "part-2"))
        assertThat(recipe.canonicalDurationMs).isEqualTo(180_000L)
    }
}
