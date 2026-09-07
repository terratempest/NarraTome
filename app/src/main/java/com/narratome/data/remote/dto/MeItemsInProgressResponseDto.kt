package com.narratome.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Response for [com.narratome.data.remote.AudiobookshelfApi.meItemsInProgress].
 * Each element is a minified library item JSON (same shape as library list items).
 */
@Serializable
data class MeItemsInProgressResponseDto(
    val libraryItems: List<JsonElement> = emptyList(),
)
