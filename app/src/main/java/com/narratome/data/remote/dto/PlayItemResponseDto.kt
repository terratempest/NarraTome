package com.narratome.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * Subset of the Audiobookshelf POST /api/items/{id}/play response used for URL extraction.
 * Extra fields are ignored by the Retrofit Json converter.
 */
@Serializable
data class PlayItemResponseDto(
    val id: String? = null,
    val audioTracks: JsonArray? = null,
    val libraryItem: JsonObject? = null,
)
