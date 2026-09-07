package com.narratome.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * A generic response for library browsing endpoints (series, collections, authors).
 * Audiobookshelf sometimes uses "results" (paginated) and sometimes a named key (legacy).
 */
@Serializable
data class GenericLibraryBrowseResponseDto(
    val results: List<JsonElement> = emptyList(),
    val series: List<JsonElement> = emptyList(),
    val collections: List<JsonElement> = emptyList(),
    val authors: List<JsonElement> = emptyList(),
) {
    fun items(): List<JsonElement> {
        return results.ifEmpty {
            series.ifEmpty {
                collections.ifEmpty {
                    authors
                }
            }
        }
    }
}
