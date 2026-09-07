package com.narratome.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playback_history",
    indices = [
        Index(value = ["libraryItemId", "occurredAtEpochMs"]),
    ],
)
data class PlaybackHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val libraryItemId: String,
    val eventType: String,
    val positionSec: Double,
    val occurredAtEpochMs: Long,
)
