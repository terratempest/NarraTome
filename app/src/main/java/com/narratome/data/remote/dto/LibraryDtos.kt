package com.narratome.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class LibrariesResponseDto(
    val libraries: List<LibraryDto> = emptyList(),
)

@Serializable
data class LibraryDto(
    val id: String,
    val name: String? = null,
    val mediaType: String? = null,
)

@Serializable
data class LibraryItemsResponseDto(
    val results: List<JsonElement> = emptyList(),
    val total: Long? = null,
)

fun JsonElement.toBookSummaryOrNull(defaultLibraryId: String): BookItemParsed? =
    toMediaSummaryOrNull(defaultLibraryId)

fun JsonElement.toMediaSummaryOrNull(defaultLibraryId: String): BookItemParsed? {
    val obj = this as? JsonObject ?: return null
    val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return null
    val libraryId = obj["libraryId"]?.jsonPrimitive?.contentOrNull ?: defaultLibraryId
    val mediaType = obj["mediaType"]?.jsonPrimitive?.contentOrNull ?: return null
    if (!mediaType.equals("book", ignoreCase = true) && !mediaType.equals("podcast", ignoreCase = true)) {
        return null
    }
    val media = obj["media"]?.jsonObject ?: return null
    val meta = media["metadata"]?.jsonObject ?: JsonObject(emptyMap())
    val title = meta["title"]?.jsonPrimitive?.contentOrNull ?: "Unknown"
    val author = if (mediaType.equals("podcast", ignoreCase = true)) {
        meta["author"]?.jsonPrimitive?.contentOrNull
    } else {
        meta["authorName"]?.jsonPrimitive?.contentOrNull
            ?: meta["authors"]?.jsonArray?.firstOrNull()?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
    }
    val coverPath = media["coverPath"]?.jsonPrimitive?.contentOrNull

    val progressObj = obj["userMediaProgress"]?.jsonObject
    val progress = if (progressObj != null) {
        val current = progressObj["currentTime"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        val duration = progressObj["duration"]?.jsonPrimitive?.doubleOrNull ?: 1.0
        (current / duration).toFloat().coerceIn(0f, 1f)
    } else null

    return BookItemParsed(id, libraryId, title, author, mediaType, coverPath, progress)
}

data class BookItemParsed(
    val id: String,
    val libraryId: String,
    val title: String,
    val author: String?,
    val mediaType: String,
    val coverPath: String?,
    val progress: Float?,
)

fun JsonElement.parseBookDetail(defaultLibraryId: String): BookDetailParsed? =
    parseBookDetailFromDto(defaultLibraryId)

data class ChapterParsed(
    val title: String,
    val startSec: Double,
    val endSec: Double,
)

data class PodcastEpisodeParsed(
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
    val currentTimeSec: Double,
    val serverProgressLastUpdate: Long?,
    val finishedAt: Long?,
)

data class BookDetailParsed(
    val id: String,
    val libraryId: String,
    val title: String,
    val author: String?,
    val seriesName: String?,
    val seriesSequence: String?,
    val seriesId: String?,
    val description: String?,
    val coverPath: String?,
    val durationSec: Double,
    val narrators: List<String>,
    val genres: List<String>,
    val publishedYear: String?,
    val chapters: List<ChapterParsed>,
    val currentTimeSec: Double,
    val serverProgressLastUpdate: Long?,
    val finishedAt: Long?,
    val serverUpdatedAtEpochMs: Long?,
    val mediaType: String = "book",
    val episodes: List<PodcastEpisodeParsed> = emptyList(),
)
