package com.narratome.player

import androidx.media3.common.C

data class MediaItemPosition(
    val index: Int,
    val positionMs: Long,
)

fun cumulativePositionMs(
    currentIndex: Int,
    currentPositionMs: Long,
    itemDurationsMs: List<Long>,
): Long {
    val prefix = itemDurationsMs
        .take(currentIndex.coerceAtLeast(0))
        .filterKnownDurations()
        ?: return currentPositionMs.coerceAtLeast(0L)
    return prefix.sum() + currentPositionMs.coerceAtLeast(0L)
}

fun mediaItemPositionForBookPosition(
    bookPositionMs: Long,
    itemDurationsMs: List<Long>,
): MediaItemPosition {
    var remainingMs = bookPositionMs.coerceAtLeast(0L)
    if (itemDurationsMs.isEmpty()) return MediaItemPosition(0, remainingMs)

    for (index in itemDurationsMs.indices) {
        val durationMs = itemDurationsMs[index]
        if (durationMs == C.TIME_UNSET || durationMs <= 0L) {
            return MediaItemPosition(index, remainingMs)
        }
        if (remainingMs < durationMs || index == itemDurationsMs.lastIndex) {
            return MediaItemPosition(index, remainingMs.coerceAtMost(durationMs))
        }
        remainingMs -= durationMs
    }

    return MediaItemPosition(itemDurationsMs.lastIndex, remainingMs)
}

fun List<Long>.filterKnownDurations(): List<Long>? =
    takeIf { durations -> durations.all { it != C.TIME_UNSET && it > 0L } }
