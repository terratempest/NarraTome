package com.narratome.player

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import com.narratome.domain.model.BookChapter

internal data class BookPlaybackPart(
    val mediaItem: MediaItem,
    val durationMs: Long,
)

/** One externally visible audiobook item backed by one or more internal audio files. */
internal data class BookPlaybackRecipe(
    val mediaItem: MediaItem,
    val parts: List<BookPlaybackPart>,
    /** Expanded library duration; preferred over temporary source placeholder values. */
    val canonicalDurationMs: Long?,
    val hasPlaceholderDurations: Boolean,
    val chapters: List<BookChapter> = emptyList(),
)

internal fun placeholderDurationsMs(
    trackDurationsMs: List<Long>,
): List<Long> {
    return trackDurationsMs.map { durationMs ->
        if (durationMs == C.TIME_UNSET) 1L else durationMs
    }
}
