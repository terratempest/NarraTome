package com.narratome.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local-first catalog row: minified fields for lists/sorting; [payloadJson] holds
 * [com.narratome.data.local.dto.BookDetailCacheDto] JSON when [hydrated] is true.
 */
@Entity(
    tableName = "catalog_items",
    indices = [
        Index(value = ["libraryId"]),
        Index(value = ["libraryId", "serverUpdatedAtEpochMs"]),
        Index(value = ["libraryId", "addedAtEpochMs"]),
        Index(value = ["libraryId", "isDownloaded"]),
        Index(value = ["libraryId", "title"]),
        Index(value = ["libraryId", "isDownloaded", "title"]),
        Index(value = ["libraryId", "isDownloaded", "addedAtEpochMs"]),
        Index(value = ["libraryId", "seriesId"]),
        Index(value = ["libraryId", "isDownloaded", "seriesId"]),
        Index(value = ["libraryId", "author"]),
        Index(value = ["libraryId", "isDownloaded", "author"]),
        Index(value = ["libraryId", "mediaType"]),
    ],
)
data class CatalogItemEntity(
    @PrimaryKey val libraryItemId: String,
    val libraryId: String,
    /** Server library-item change signal from minified list (e.g. `updatedAt` ms). */
    val serverUpdatedAtEpochMs: Long,
    val title: String,
    val author: String?,
    val seriesName: String?,
    val seriesId: String?,
    val addedAtEpochMs: Long?,
    val coverPath: String?,
    val progress: Float?,
    /** `book` or `podcast` from Audiobookshelf. */
    val mediaType: String = "book",
    /** True after successful `GET /api/items/{id}?expanded=1` parse and DTO persist. */
    val hydrated: Boolean,
    /** kotlinx-serialized [com.narratome.data.local.dto.BookDetailCacheDto] when [hydrated]. */
    val payloadJson: String?,
    val localRowUpdatedAtEpochMs: Long = System.currentTimeMillis(),
    /** True when a completed local audio download exists ([local_download_manifest] + parts). */
    val isDownloaded: Boolean = false,
)
