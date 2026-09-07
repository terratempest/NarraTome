package com.narratome.data.remote.dto

import android.os.Build
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

internal val ExpandedItemJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

@Serializable
data class ExpandedLibraryItemDto(
    val id: String = "",
    val libraryId: String? = null,
    val mediaType: String? = null,
    val updatedAt: Long? = null,
    val media: ExpandedItemMediaDto? = null,
    val userMediaProgress: JsonObject? = null,
)

@Serializable
data class ExpandedItemMediaDto(
    val metadata: ExpandedItemMetadataDto? = null,
    val coverPath: String? = null,
    val duration: Double? = null,
    val chapters: List<ExpandedChapterDto>? = null,
    val episodes: List<ExpandedPodcastEpisodeDto>? = null,
)

@Serializable
data class BookSeriesRefDto(
    val id: String? = null,
    val name: String? = null,
    val sequence: JsonElement? = null,
)

@Serializable
data class ExpandedItemMetadataDto(
    val title: String? = null,
    val authorName: String? = null,
    val author: String? = null,
    val description: String? = null,
    val seriesName: String? = null,
    val seriesSequence: JsonElement? = null,
    val series: List<BookSeriesRefDto>? = null,
    val narratorName: String? = null,
    val narrators: JsonArray? = null,
    val genres: JsonArray? = null,
    val publishedYear: JsonElement? = null,
    val releaseDate: String? = null,
)

@Serializable
data class ExpandedPodcastEpisodeDto(
    val libraryItemId: String? = null,
    val id: String = "",
    val index: Int? = null,
    val season: String? = null,
    val episode: String? = null,
    val episodeType: String? = null,
    val title: String? = null,
    val subtitle: String? = null,
    val description: String? = null,
    val pubDate: String? = null,
    val publishedAt: Long? = null,
    val addedAt: Long? = null,
    val updatedAt: Long? = null,
    val duration: Double? = null,
    val audioTrack: JsonObject? = null,
    val audioFile: JsonObject? = null,
    val userMediaProgress: JsonObject? = null,
)

private fun JsonPrimitive.asFlexibleString(): String? =
    contentOrNull?.takeIf { it.isNotBlank() }
        ?: longOrNull?.toString()
        ?: doubleOrNull?.toString()

private fun JsonElement?.asProgressSeconds(): Double {
    val p = this as? JsonPrimitive ?: return 0.0
    return p.doubleOrNull
        ?: p.longOrNull?.toDouble()
        ?: p.content.toDoubleOrNull()
        ?: 0.0
}

private fun JsonElement?.asProgressLastUpdateEpochMs(): Long? {
    val p = this as? JsonPrimitive ?: return null
    p.longOrNull?.let { return it }
    p.doubleOrNull?.let { return it.toLong() }
    val s = p.contentOrNull ?: return null
    s.toLongOrNull()?.let { return it }
    s.substringBefore('.').toLongOrNull()?.let { return it }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        runCatching { java.time.Instant.parse(s).toEpochMilli() }.getOrNull()?.let { return it }
    }
    return parseLastUpdateIsoWithSimpleDateFormat(s)
}

@Suppress("DEPRECATION")
private fun parseLastUpdateIsoWithSimpleDateFormat(s: String): Long? {
    val patterns = arrayOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSSX",
        "yyyy-MM-dd'T'HH:mm:ssX",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
    )
    for (pattern in patterns) {
        try {
            val df = SimpleDateFormat(pattern, Locale.US)
            df.timeZone = TimeZone.getTimeZone("UTC")
            df.parse(s)?.time?.let { return it }
        } catch (_: ParseException) {
            continue
        }
    }
    return null
}

@Serializable
data class ExpandedChapterDto(
    val title: String? = null,
    val start: Double? = null,
    val end: Double? = null,
)

fun ExpandedLibraryItemDto.toBookDetailParsed(defaultLibraryId: String): BookDetailParsed? {
    if (id.isBlank()) return null
    val type = mediaType?.takeIf { it.isNotBlank() } ?: "book"
    val m = media ?: return null
    return if (type.equals("podcast", ignoreCase = true)) {
        toPodcastDetailParsed(defaultLibraryId, m)
    } else {
        toBookDetailParsed(defaultLibraryId, m)
    }
}

private fun ExpandedLibraryItemDto.toBookDetailParsed(defaultLibraryId: String, m: ExpandedItemMediaDto): BookDetailParsed? {
    val meta = m.metadata
    val title = meta?.title?.takeIf { it.isNotBlank() } ?: "Unknown"
    val author = meta?.authorName?.takeIf { it.isNotBlank() }
    val description = meta?.description?.takeIf { it.isNotBlank() }?.stripHtml()
    val coverPath = m.coverPath?.takeIf { it.isNotBlank() }
    val duration = m.duration ?: 0.0

    val seriesName = meta?.seriesName?.takeIf { it.isNotBlank() }
    val seriesSequence = (meta?.seriesSequence as? JsonPrimitive)?.asFlexibleString()
    val seriesId = resolveSeriesIdFromMetadata(meta?.series, seriesName, seriesSequence)

    val narrators = meta?.narratorName?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
        ?: meta?.narrators?.mapNotNull { it.jsonPrimitive.contentOrNull }
        ?: emptyList()

    val genres = meta?.genres?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
    val publishedYear = (meta?.publishedYear as? JsonPrimitive)?.asFlexibleString()

    val chapters = m.chapters.orEmpty().map { ch ->
        val start = ch.start ?: 0.0
        ChapterParsed(ch.title?.takeIf { it.isNotBlank() } ?: "", start, ch.end ?: start)
    }

    val progress = userMediaProgress
    val current = progress?.get("currentTime")?.asProgressSeconds() ?: 0.0
    val lastUp = progress?.get("lastUpdate")?.asProgressLastUpdateEpochMs()
    val finishedAt = progress?.get("finishedAt")?.asProgressLastUpdateEpochMs()

    return BookDetailParsed(
        id = id,
        libraryId = libraryId?.takeIf { it.isNotBlank() } ?: defaultLibraryId,
        title = title,
        author = author,
        seriesName = seriesName,
        seriesSequence = seriesSequence,
        seriesId = seriesId,
        description = description,
        coverPath = coverPath,
        durationSec = duration,
        narrators = narrators,
        genres = genres,
        publishedYear = publishedYear,
        chapters = chapters,
        currentTimeSec = current,
        serverProgressLastUpdate = lastUp,
        finishedAt = finishedAt,
        serverUpdatedAtEpochMs = updatedAt,
        mediaType = "book",
    )
}

private fun ExpandedLibraryItemDto.toPodcastDetailParsed(defaultLibraryId: String, m: ExpandedItemMediaDto): BookDetailParsed? {
    val meta = m.metadata
    val title = meta?.title?.takeIf { it.isNotBlank() } ?: "Unknown Podcast"
    val author = meta?.author?.takeIf { it.isNotBlank() } ?: meta?.authorName?.takeIf { it.isNotBlank() }
    val description = meta?.description?.takeIf { it.isNotBlank() }?.stripHtml()
    val coverPath = m.coverPath?.takeIf { it.isNotBlank() }
    val genres = meta?.genres?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
    val episodes = m.episodes.orEmpty()
        .mapNotNull { it.toPodcastEpisodeParsed(fallbackLibraryItemId = id) }
        .sortedWith(
            compareByDescending<PodcastEpisodeParsed> { it.publishedAt ?: it.addedAt ?: it.updatedAt ?: 0L }
                .thenBy { it.index ?: Int.MAX_VALUE },
        )
    val totalDuration = episodes.sumOf { it.durationSec }.takeIf { it > 0.0 } ?: (m.duration ?: 0.0)

    return BookDetailParsed(
        id = id,
        libraryId = libraryId?.takeIf { it.isNotBlank() } ?: defaultLibraryId,
        title = title,
        author = author,
        seriesName = null,
        seriesSequence = null,
        seriesId = null,
        description = description,
        coverPath = coverPath,
        durationSec = totalDuration,
        narrators = emptyList(),
        genres = genres,
        publishedYear = meta?.releaseDate?.take(4),
        chapters = emptyList(),
        currentTimeSec = 0.0,
        serverProgressLastUpdate = null,
        finishedAt = null,
        serverUpdatedAtEpochMs = updatedAt,
        mediaType = "podcast",
        episodes = episodes,
    )
}

private fun ExpandedPodcastEpisodeDto.toPodcastEpisodeParsed(fallbackLibraryItemId: String): PodcastEpisodeParsed? {
    if (id.isBlank()) return null
    val progress = userMediaProgress
    val duration = duration
        ?: progress?.get("duration")?.asProgressSeconds()
        ?: audioTrack?.get("duration")?.asProgressSeconds()
        ?: audioFile?.get("duration")?.asProgressSeconds()
    val current = progress?.get("currentTime")?.asProgressSeconds() ?: 0.0
    return PodcastEpisodeParsed(
        id = id,
        libraryItemId = libraryItemId?.takeIf { it.isNotBlank() } ?: fallbackLibraryItemId,
        index = index,
        season = season?.takeIf { it.isNotBlank() },
        episode = episode?.takeIf { it.isNotBlank() },
        title = title?.takeIf { it.isNotBlank() } ?: "Episode ${index ?: ""}".trim(),
        subtitle = subtitle?.takeIf { it.isNotBlank() },
        description = description?.takeIf { it.isNotBlank() }?.stripHtml(),
        pubDate = pubDate?.takeIf { it.isNotBlank() },
        publishedAt = publishedAt,
        addedAt = addedAt,
        updatedAt = updatedAt,
        durationSec = duration ?: 0.0,
        currentTimeSec = current,
        serverProgressLastUpdate = progress?.get("lastUpdate")?.asProgressLastUpdateEpochMs(),
        finishedAt = progress?.get("finishedAt")?.asProgressLastUpdateEpochMs(),
    )
}

private fun String.stripHtml(): String = replace(Regex("<[^>]*>"), "").trim()

internal fun resolveSeriesIdFromMetadata(
    series: List<BookSeriesRefDto>?,
    seriesName: String?,
    seriesSequence: String?,
): String? {
    val list = series?.filter { !it.id.isNullOrBlank() } ?: return null
    if (list.isEmpty()) return null
    if (list.size == 1) return list[0].id

    fun seqString(e: BookSeriesRefDto): String? =
        (e.sequence as? JsonPrimitive)?.asFlexibleString()?.trim()?.lowercase()

    val wantName = seriesName?.trim()?.lowercase()
    val wantSeq = seriesSequence?.trim()?.lowercase()

    if (wantName != null) {
        val named = list.filter { it.name?.trim()?.equals(wantName, ignoreCase = true) == true }
        if (named.size == 1) return named[0].id
        if (named.isNotEmpty() && wantSeq != null) {
            val bySeq = named.find { seqString(it) == wantSeq }
            if (bySeq != null) return bySeq.id
        }
        if (named.isNotEmpty()) return named[0].id
    }
    return null
}

fun JsonElement.parseBookDetailFromDto(defaultLibraryId: String): BookDetailParsed? {
    val obj = this as? JsonObject ?: return null
    val dto = runCatching {
        ExpandedItemJson.decodeFromJsonElement(ExpandedLibraryItemDto.serializer(), obj)
    }.getOrNull() ?: return null
    return dto.toBookDetailParsed(defaultLibraryId)
}
