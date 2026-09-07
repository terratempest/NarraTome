package com.narratome.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalDownloadDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertManifest(entity: LocalDownloadManifestEntity)

    @Query("SELECT * FROM local_download_manifest WHERE downloadKey = :downloadKey LIMIT 1")
    suspend fun getManifest(downloadKey: String): LocalDownloadManifestEntity?

    @Query("SELECT * FROM local_download_manifest WHERE libraryItemId = :libraryItemId ORDER BY completedAtEpochMs DESC")
    suspend fun listManifestsForItem(libraryItemId: String): List<LocalDownloadManifestEntity>

    @Query("SELECT COUNT(*) FROM local_download_manifest WHERE libraryItemId = :libraryItemId")
    suspend fun countManifestsForItem(libraryItemId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPart(entity: LocalDownloadPartEntity)

    @Query("SELECT * FROM local_download_parts WHERE downloadKey = :downloadKey ORDER BY partIndex ASC")
    suspend fun listParts(downloadKey: String): List<LocalDownloadPartEntity>

    @Query("SELECT * FROM local_download_parts WHERE downloadKey = :downloadKey ORDER BY partIndex ASC")
    fun observeParts(downloadKey: String): Flow<List<LocalDownloadPartEntity>>

    @Query("SELECT * FROM local_download_parts WHERE libraryItemId = :libraryItemId ORDER BY partIndex ASC")
    fun observePartsForItem(libraryItemId: String): Flow<List<LocalDownloadPartEntity>>

    @Query("DELETE FROM local_download_parts WHERE downloadKey = :downloadKey")
    suspend fun deleteParts(downloadKey: String)

    @Query("DELETE FROM local_download_parts WHERE libraryItemId = :libraryItemId")
    suspend fun deletePartsForItem(libraryItemId: String)

    @Query("SELECT * FROM local_download_manifest ORDER BY completedAtEpochMs DESC")
    fun observeAllManifests(): Flow<List<LocalDownloadManifestEntity>>

    @Query("DELETE FROM local_download_manifest WHERE downloadKey = :downloadKey")
    suspend fun deleteManifest(downloadKey: String)

    @Query("DELETE FROM local_download_manifest WHERE libraryItemId = :libraryItemId")
    suspend fun deleteManifestsForItem(libraryItemId: String)
}
