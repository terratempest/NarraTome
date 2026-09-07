package com.narratome.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "catalog_sync_state")
data class CatalogSyncStateEntity(
    @PrimaryKey val libraryId: String,
    val hasCompletedFullSync: Boolean = false,
    val lastSuccessfulFullSyncAtEpochMs: Long? = null,
    val lastDeltaSyncAtEpochMs: Long? = null,
    val lastSyncStartedAtEpochMs: Long? = null,
    val lastSyncCompletedAtEpochMs: Long? = null,
    val lastSyncPhase: String? = null,
    val lastSyncError: String? = null,
)
