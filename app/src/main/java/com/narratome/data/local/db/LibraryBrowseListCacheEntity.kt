package com.narratome.data.local.db

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "library_browse_list_cache",
    primaryKeys = ["libraryId", "kind", "query"],
    indices = [Index(value = ["libraryId", "kind"])],
)
data class LibraryBrowseListCacheEntity(
    val libraryId: String,
    val kind: String,
    /** Empty for authors/series/collections; search query text for search. */
    val query: String,
    val payloadJson: String,
    val updatedAtEpochMs: Long,
)
