package com.narratome.data.remote.dto

import kotlinx.serialization.Serializable

/** GET `/api/libraries/{libraryId}/series/{seriesId}` — subset of `series.toOldJSON()`. */
@Serializable
data class LibrarySeriesDetailDto(
    val id: String = "",
    val name: String? = null,
    val description: String? = null,
    val libraryId: String? = null,
)
