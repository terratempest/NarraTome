package com.narratome.domain.model

data class BookChapter(
    val title: String,
    val startSec: Double,
    val endSec: Double,
)

data class PodcastEpisode(
    val id: String,
    val libraryItemId: String,
    val index: Int?,
    val season: String?,
    val episode: String?,
    val title: String,
    val subtitle: String?,
    val description: String?,
    val pubDate: String?,
    val publishedAt: Long?,
    val addedAt: Long?,
    val updatedAt: Long?,
    val durationSec: Double,
    val currentTimeSec: Double = 0.0,
    val serverProgressLastUpdate: Long? = null,
    val finishedAt: Long? = null,
) {
    val progress: Double?
        get() = durationSec.takeIf { it > 0.0 }?.let { (currentTimeSec / it).coerceIn(0.0, 1.0) }
}

data class BookDetail(
    val id: String,
    val libraryId: String,
    val title: String,
    val author: String?,
    val seriesName: String?,
    val seriesSequence: String?,
    /** Library series entity id when the expanded item includes series links. */
    val seriesId: String?,
    val description: String?,
    val coverPath: String?,
    val durationSec: Double,
    val narrators: List<String>,
    val genres: List<String>,
    val publishedYear: String?,
    val chapters: List<BookChapter>,
    val currentTimeSec: Double,
    val serverProgressLastUpdate: Long?,
    val finishedAt: Long? = null,
    val progress: Double? = null,
    val serverUpdatedAtEpochMs: Long = 0L,
    val mediaType: String = MEDIA_TYPE_BOOK,
    val episodes: List<PodcastEpisode> = emptyList(),
) {
    val isPodcast: Boolean get() = mediaType.equals(MEDIA_TYPE_PODCAST, ignoreCase = true)

    companion object {
        const val MEDIA_TYPE_BOOK = "book"
        const val MEDIA_TYPE_PODCAST = "podcast"
    }
}

/** Trimmed title, or `Chapter {sortedIndex+1}` when the server sent an empty title. */
fun BookChapter.displayTitle(sortedIndexZeroBased: Int): String {
    val t = title.trim()
    return if (t.isNotEmpty()) t else "Chapter ${sortedIndexZeroBased + 1}"
}

fun List<BookChapter>.sortedByStart(): List<BookChapter> = sortedBy { it.startSec }

/** Display label for the chapter active at [positionSec] on the book timeline. */
fun List<BookChapter>.displayTitleAtTimelineSec(positionSec: Double): String? {
    val sorted = sortedByStart()
    val ch = sorted.lastOrNull { it.startSec <= positionSec.coerceAtLeast(0.0) } ?: return null
    val idx = sorted.indexOf(ch).coerceAtLeast(0)
    return ch.displayTitle(idx)
}
