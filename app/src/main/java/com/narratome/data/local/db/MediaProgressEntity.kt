package com.narratome.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media_progress",
    indices = [
        Index(value = ["localDirty"]),
        Index(value = ["lastInteractionTime"]),
        Index(value = ["libraryId", "lastInteractionTime"]),
        Index(value = ["libraryItemId"]),
        Index(value = ["libraryItemId", "episodeId"]),
    ],
)
data class MediaProgressEntity(
    @PrimaryKey val progressKey: String,
    val libraryItemId: String,
    val currentTimeSec: Double,
    val durationSec: Double,
    val lastKnownServerLastUpdate: Long?,
    val localDirty: Boolean,
    val localRevision: Long,
    val libraryId: String? = null,
    val episodeId: String? = null,
    val hideFromContinueListening: Boolean = false,
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    val lastInteractionTime: Long? = null,
)
