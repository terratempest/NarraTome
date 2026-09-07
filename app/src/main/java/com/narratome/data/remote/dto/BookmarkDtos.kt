package com.narratome.data.remote.dto

import com.narratome.data.local.db.BookmarkEntity
import kotlinx.serialization.Serializable

@Serializable
data class MeDto(
    val bookmarks: List<AudioBookmarkDto> = emptyList(),
    val mediaProgress: List<MediaProgressMeDto> = emptyList(),
)

@Serializable
data class MediaProgressMeDto(
    val libraryItemId: String? = null,
    val id: String? = null,
    val libraryId: String? = null,
    val episodeId: String? = null,
    val hideFromContinueListening: Boolean? = null,
    val duration: Double? = null,
    val currentTime: Double? = null,
    val progress: Double? = null,
    val lastUpdate: Long? = null,
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
) {
    fun resolvedLibraryItemId(): String? = libraryItemId ?: id
}

@Serializable
data class AudioBookmarkDto(
    val libraryItemId: String,
    val title: String,
    val time: Int,
    val createdAt: Long,
)

@Serializable
data class BookmarkUpsertRequestDto(
    val time: Int,
    val title: String,
)

fun AudioBookmarkDto.toEntity(): BookmarkEntity =
    BookmarkEntity(
        libraryItemId = libraryItemId,
        timeSec = time.toDouble(),
        title = title,
        dirty = false,
        tombstone = false,
        serverCreatedAt = createdAt,
    )
