package com.narratome.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "download_jobs",
    indices = [
        Index(value = ["libraryItemId"]),
        Index(value = ["libraryItemId", "episodeId"]),
        Index(value = ["state"]),
    ],
)
data class DownloadJobEntity(
    @PrimaryKey val downloadKey: String,
    val libraryItemId: String,
    val episodeId: String? = null,
    val title: String?,
    val state: String,
    val currentPartIndex: Int,
    val totalParts: Int,
    val bytesDownloadedThisPart: Long,
    val bytesTotalThisPart: Long,
    val bytesDownloadedTotal: Long,
    val bytesTotal: Long,
    val lastError: String?,
    val startedAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)
