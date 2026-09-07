package com.narratome.data.local.dto

import com.narratome.domain.model.BookChapter
import com.narratome.domain.model.BookDetail
import com.narratome.domain.model.PodcastEpisode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BookDetailCacheDto(
    val id: String,
    val libraryId: String,
    val title: String,
    val author: String?,
    val seriesName: String?,
    val seriesSequence: String?,
    val seriesId: String? = null,
    val description: String?,
    val coverPath: String?,
    val durationSec: Double,
    val narrators: List<String>,
    val genres: List<String>,
    val publishedYear: String?,
    val chapters: List<BookChapterCacheDto>,
    val currentTimeSec: Double,
    val serverProgressLastUpdate: Long?,
    val finishedAt: Long? = null,
    val progress: Double? = null,
    val serverUpdatedAtEpochMs: Long = 0L,
    val mediaType: String = BookDetail.MEDIA_TYPE_BOOK,
    val episodes: List<PodcastEpisodeCacheDto> = emptyList(),
)

@Serializable
data class BookChapterCacheDto(
    val title: String,
    val startSec: Double,
    val endSec: Double,
)

@Serializable
data class PodcastEpisodeCacheDto(
    val id: String,
    val libraryItemId: String,
    val index: Int? = null,
    val season: String? = null,
    val episode: String? = null,
    val title: String,
    val subtitle: String? = null,
    val description: String? = null,
    val pubDate: String? = null,
    val publishedAt: Long? = null,
    val addedAt: Long? = null,
    val updatedAt: Long? = null,
    val durationSec: Double = 0.0,
    val currentTimeSec: Double = 0.0,
    val serverProgressLastUpdate: Long? = null,
    val finishedAt: Long? = null,
)

fun BookDetail.toCacheDto(): BookDetailCacheDto =
    BookDetailCacheDto(
        id = id,
        libraryId = libraryId,
        title = title,
        author = author,
        seriesName = seriesName,
        seriesSequence = seriesSequence,
        seriesId = seriesId,
        description = description,
        coverPath = coverPath,
        durationSec = durationSec,
        narrators = narrators,
        genres = genres,
        publishedYear = publishedYear,
        chapters = chapters.map {
            BookChapterCacheDto(title = it.title, startSec = it.startSec, endSec = it.endSec)
        },
        currentTimeSec = currentTimeSec,
        serverProgressLastUpdate = serverProgressLastUpdate,
        finishedAt = finishedAt,
        progress = progress,
        serverUpdatedAtEpochMs = serverUpdatedAtEpochMs,
        mediaType = mediaType,
        episodes = episodes.map { it.toCacheDto() },
    )

private fun PodcastEpisode.toCacheDto(): PodcastEpisodeCacheDto =
    PodcastEpisodeCacheDto(
        id = id,
        libraryItemId = libraryItemId,
        index = index,
        season = season,
        episode = episode,
        title = title,
        subtitle = subtitle,
        description = description,
        pubDate = pubDate,
        publishedAt = publishedAt,
        addedAt = addedAt,
        updatedAt = updatedAt,
        durationSec = durationSec,
        currentTimeSec = currentTimeSec,
        serverProgressLastUpdate = serverProgressLastUpdate,
        finishedAt = finishedAt,
    )

fun BookDetailCacheDto.toDomain(): BookDetail =
    BookDetail(
        id = id,
        libraryId = libraryId,
        title = title,
        author = author,
        seriesName = seriesName,
        seriesSequence = seriesSequence,
        seriesId = seriesId,
        description = description,
        coverPath = coverPath,
        durationSec = durationSec,
        narrators = narrators,
        genres = genres,
        publishedYear = publishedYear,
        chapters = chapters.map { BookChapter(it.title, it.startSec, it.endSec) },
        currentTimeSec = currentTimeSec,
        serverProgressLastUpdate = serverProgressLastUpdate,
        finishedAt = finishedAt,
        progress = progress,
        serverUpdatedAtEpochMs = serverUpdatedAtEpochMs,
        mediaType = mediaType,
        episodes = episodes.map { it.toDomain() },
    )

private fun PodcastEpisodeCacheDto.toDomain(): PodcastEpisode =
    PodcastEpisode(
        id = id,
        libraryItemId = libraryItemId,
        index = index,
        season = season,
        episode = episode,
        title = title,
        subtitle = subtitle,
        description = description,
        pubDate = pubDate,
        publishedAt = publishedAt,
        addedAt = addedAt,
        updatedAt = updatedAt,
        durationSec = durationSec,
        currentTimeSec = currentTimeSec,
        serverProgressLastUpdate = serverProgressLastUpdate,
        finishedAt = finishedAt,
    )

fun decodeBookDetailFromCatalogPayload(payloadJson: String?, json: Json): BookDetail? {
    if (payloadJson.isNullOrBlank()) return null
    return runCatching {
        json.decodeFromString(BookDetailCacheDto.serializer(), payloadJson).toDomain()
    }.getOrNull()
}
