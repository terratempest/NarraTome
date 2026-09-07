package com.narratome.player

import androidx.media3.common.C
import androidx.media3.common.MediaItem

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
)

internal fun placeholderDurationsMs(
    trackDurationsMs: List<Long>,
    bookDurationMs: Long,
): List<Long> {
    val knownDurationMs = trackDurationsMs.filter { it != C.TIME_UNSET }.sum()
    val unknownCount = trackDurationsMs.count { it == C.TIME_UNSET }
    val fallbackPerPartMs = if (unknownCount == 0) {
        0L
    } else {
        ((bookDurationMs - knownDurationMs).coerceAtLeast(unknownCount.toLong()) / unknownCount)
            .coerceAtLeast(1L)
    }

    return trackDurationsMs.map { durationMs ->
        if (durationMs == C.TIME_UNSET) fallbackPerPartMs else durationMs
    }
}
