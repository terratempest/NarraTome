package com.narratome.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmark_outbox")
data class BookmarkOutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val libraryItemId: String,
    /** CREATE or DELETE */
    val op: String,
    val timeSec: Double,
    val title: String?,
    val attempts: Int = 0,
    val lastError: String?,
    val createdAtEpochMs: Long,
)
