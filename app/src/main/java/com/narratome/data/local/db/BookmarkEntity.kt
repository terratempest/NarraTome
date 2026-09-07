package com.narratome.data.local.db

import androidx.room.Entity

@Entity(
    tableName = "bookmarks",
    primaryKeys = ["libraryItemId", "timeSec"],
)
data class BookmarkEntity(
    val libraryItemId: String,
    val timeSec: Double,
    val title: String,
    val dirty: Boolean,
    val tombstone: Boolean,
    val serverCreatedAt: Long?,
)
