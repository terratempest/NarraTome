package com.narratome.data.local.db

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "local_download_parts",
    primaryKeys = ["downloadKey", "partIndex"],
    indices = [
        Index(value = ["libraryItemId"]),
        Index(value = ["libraryItemId", "episodeId"]),
    ],
)
data class LocalDownloadPartEntity(
    val downloadKey: String,
    val libraryItemId: String,
    val episodeId: String? = null,
    val partIndex: Int,
    val fileName: String,
    val createdAtEpochMs: Long,
)
