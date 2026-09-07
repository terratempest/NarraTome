package com.narratome.data.local.dto

import com.narratome.domain.model.AuthorSummary
import com.narratome.domain.model.CollectionSummary
import com.narratome.domain.model.LibraryItemSummary
import com.narratome.domain.model.SeriesSummary
import kotlinx.serialization.Serializable

@Serializable
data class AuthorSummaryCacheDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val bookCount: Int = 0,
)

fun AuthorSummary.toCacheDto() = AuthorSummaryCacheDto(id, name, description, bookCount)
fun AuthorSummaryCacheDto.toDomain() = AuthorSummary(id, name, description, bookCount)

@Serializable
data class SeriesSummaryCacheDto(
    val id: String,
    val name: String,
    val bookCount: Int = 0,
)

fun SeriesSummary.toCacheDto() = SeriesSummaryCacheDto(id, name, bookCount)
fun SeriesSummaryCacheDto.toDomain() = SeriesSummary(id, name, bookCount)

@Serializable
data class CollectionSummaryCacheDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val bookCount: Int = 0,
    val coverItemIds: List<String> = emptyList(),
)

fun CollectionSummary.toCacheDto() = CollectionSummaryCacheDto(id, name, description, bookCount, coverItemIds)
fun CollectionSummaryCacheDto.toDomain() = CollectionSummary(id, name, description, bookCount, coverItemIds)

@Serializable
data class LibraryItemSummaryCacheDto(
    val id: String,
    val libraryId: String,
    val title: String,
    val author: String? = null,
    val mediaType: String,
    val coverPath: String? = null,
    val progress: Float? = null,
)

fun LibraryItemSummary.toCacheDto() = LibraryItemSummaryCacheDto(
    id, libraryId, title, author, mediaType, coverPath, progress,
)

fun LibraryItemSummaryCacheDto.toDomain() = LibraryItemSummary(
    id, libraryId, title, author, mediaType, coverPath, progress,
)
