package com.narratome.domain.model

data class AuthorSummary(
    val id: String,
    val name: String,
    val description: String?,
    val bookCount: Int,
)

data class CollectionSummary(
    val id: String,
    val name: String,
    val description: String?,
    val bookCount: Int,
    val coverItemIds: List<String> = emptyList(),
)

data class SeriesSummary(
    val id: String,
    val name: String,
    val bookCount: Int,
)
