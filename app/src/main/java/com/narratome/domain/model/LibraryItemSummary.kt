package com.narratome.domain.model

data class LibraryItemSummary(
    val id: String,
    val libraryId: String,
    val title: String,
    val author: String?,
    val mediaType: String,
    val coverPath: String?,
    val progress: Float? = null,
)
