package com.narratome.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/**
 * GET `/api/libraries/{id}/search` — Audiobookshelf returns grouped hits; we only need a
 * [JsonObject] root for [com.narratome.data.repository.LibraryRepository.extractBookResults].
 */
@Serializable
data class LibrarySearchResponseDto(
    val book: List<JsonElement> = emptyList(),
    val narrators: List<JsonElement> = emptyList(),
    val tags: List<JsonElement> = emptyList(),
    val genres: List<JsonElement> = emptyList(),
    val series: List<JsonElement> = emptyList(),
    val authors: List<JsonElement> = emptyList(),
) {
    fun asRootJsonObject(): JsonObject = buildJsonObject {
        put("book", JsonArray(book))
        put("narrators", JsonArray(narrators))
        put("tags", JsonArray(tags))
        put("genres", JsonArray(genres))
        put("series", JsonArray(series))
        put("authors", JsonArray(authors))
    }
}
