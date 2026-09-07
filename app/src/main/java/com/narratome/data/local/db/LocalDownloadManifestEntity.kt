package com.narratome.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_download_manifest",
    indices = [
        Index(value = ["libraryItemId"]),
        Index(value = ["libraryItemId", "episodeId"]),
    ],
)
data class LocalDownloadManifestEntity(
    @PrimaryKey val downloadKey: String,
    val libraryItemId: String,
    val episodeId: String? = null,
    val expectedPartCount: Int,
    val completedAtEpochMs: Long,
)
