package com.narratome.data.local.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.sqlite.db.SupportSQLiteQuery
import com.narratome.domain.model.LibraryItemSummary
import com.narratome.domain.model.SeriesSummary
import kotlinx.coroutines.flow.Flow

data class AuthorNameCountRow(
    val author: String,
    val bookCount: Int,
)

data class GroupedLibraryItemIdRow(
    val groupId: String,
    val libraryItemId: String,
)

@Dao
interface CatalogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItem(item: CatalogItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CatalogItemEntity>)

    @Query("UPDATE catalog_items SET isDownloaded = :v WHERE libraryItemId = :id")
    suspend fun setDownloaded(id: String, v: Boolean)

    @Query("SELECT * FROM catalog_items WHERE libraryId = :libraryId ORDER BY title COLLATE NOCASE ASC")
    fun observeItems(libraryId: String): Flow<List<CatalogItemEntity>>

    @RawQuery(observedEntities = [CatalogItemEntity::class, MediaProgressEntity::class])
    fun pagingSource(query: SupportSQLiteQuery): PagingSource<Int, CatalogItemEntity>

    @Query("SELECT * FROM catalog_items WHERE libraryItemId = :id LIMIT 1")
    suspend fun getById(id: String): CatalogItemEntity?

    @Query("SELECT * FROM catalog_items WHERE libraryItemId IN (:ids)")
    suspend fun listByIds(ids: List<String>): List<CatalogItemEntity>

    @Query("SELECT * FROM catalog_items WHERE libraryItemId = :id LIMIT 1")
    fun observeById(id: String): Flow<CatalogItemEntity?>

    @Query(
        """
        SELECT * FROM catalog_items WHERE libraryId = :libraryId
          AND (:downloadedOnly = 0 OR isDownloaded = 1)
        ORDER BY addedAtEpochMs DESC LIMIT :limit
        """,
    )
    fun observeRecentByAdded(libraryId: String, limit: Int, downloadedOnly: Boolean): Flow<List<CatalogItemEntity>>

    @Query(
        """
        SELECT * FROM catalog_items WHERE libraryId = :libraryId
          AND (:downloadedOnly = 0 OR isDownloaded = 1)
        ORDER BY addedAtEpochMs DESC LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun listRecentByAdded(
        libraryId: String,
        limit: Int,
        downloadedOnly: Boolean,
        offset: Int = 0,
    ): List<CatalogItemEntity>

    @Query(
        """
        SELECT * FROM catalog_items WHERE libraryId = :libraryId
          AND (:downloadedOnly = 0 OR isDownloaded = 1)
        ORDER BY title COLLATE NOCASE ASC LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun listForLibrary(
        libraryId: String,
        limit: Int,
        downloadedOnly: Boolean,
        offset: Int = 0,
    ): List<CatalogItemEntity>

    @Query("SELECT libraryItemId FROM catalog_items WHERE libraryId = :libraryId")
    suspend fun listAllIdsForLibrary(libraryId: String): List<String>

    @Query(
        """
        SELECT libraryItemId FROM catalog_items
        WHERE libraryId = :libraryId
          AND (hydrated = 0 OR payloadJson IS NULL OR TRIM(payloadJson) = '')
        """,
    )
    suspend fun listPendingHydrationIds(libraryId: String): List<String>

    @Query("SELECT libraryId FROM catalog_items ORDER BY localRowUpdatedAtEpochMs DESC LIMIT 1")
    suspend fun firstLibraryIdOrNull(): String?

    @Query("SELECT DISTINCT libraryId FROM catalog_items ORDER BY libraryId COLLATE NOCASE ASC")
    suspend fun listDistinctLibraryIds(): List<String>

    @Query("SELECT DISTINCT libraryId FROM catalog_items WHERE isDownloaded = 1 ORDER BY libraryId")
    suspend fun listDownloadedLibraryIds(): List<String>

    @Query(
        """
        SELECT
            seriesId AS id,
            TRIM(SUBSTR(seriesName, 1, INSTR(seriesName, '#') - 1)) AS name,
            COUNT(*) AS bookCount
        FROM catalog_items
        WHERE seriesName IS NOT NULL
          AND seriesName != ""
          AND INSTR(seriesName, '#') > 0
          AND libraryId = :libraryId
          AND (:seriesId = "" OR seriesId = :seriesId)
          AND (:downloadedOnly = 0 OR isDownloaded = 1)
        GROUP BY name
        ORDER BY name
        """,
    )
    suspend fun getSeriesSummary(libraryId: String, seriesId: String, downloadedOnly: Boolean): List<SeriesSummary>

    @Query(
        """
        SELECT
        	libraryItemId AS id,
        	libraryId,
        	title,
        	author,
            "book" as mediaType,
        	coverPath,
        	progress
        FROM catalog_items
        WHERE seriesId = :seriesId
          AND libraryId = :libraryid
          AND (:downloadedOnly = 0 OR isDownloaded = 1)
        ORDER BY
            CASE
                WHEN INSTR(seriesName, '#') > 0
                 AND TRIM(SUBSTR(seriesName, INSTR(seriesName, '#') + 1)) GLOB '*[0-9]*'
                 AND TRIM(SUBSTR(seriesName, INSTR(seriesName, '#') + 1)) NOT GLOB '*[^0-9.]*'
                THEN 0
                ELSE 1
            END,
            CAST(TRIM(SUBSTR(seriesName, INSTR(seriesName, '#') + 1)) AS REAL) ASC,
            title COLLATE NOCASE ASC
        """,
    )
    suspend fun getBookSeriesDetails(libraryid: String, seriesId: String, downloadedOnly: Boolean): List<LibraryItemSummary>

    @Query(
        """
        SELECT seriesId AS groupId, libraryItemId
        FROM catalog_items
        WHERE libraryId = :libraryId
          AND seriesId IS NOT NULL
          AND TRIM(seriesId) != ''
          AND (:downloadedOnly = 0 OR isDownloaded = 1)
        ORDER BY
            seriesId COLLATE NOCASE ASC,
            CASE
                WHEN INSTR(seriesName, '#') > 0
                 AND TRIM(SUBSTR(seriesName, INSTR(seriesName, '#') + 1)) GLOB '*[0-9]*'
                 AND TRIM(SUBSTR(seriesName, INSTR(seriesName, '#') + 1)) NOT GLOB '*[^0-9.]*'
                THEN 0
                ELSE 1
            END,
            CAST(TRIM(SUBSTR(seriesName, INSTR(seriesName, '#') + 1)) AS REAL) ASC,
            title COLLATE NOCASE ASC
        """,
    )
    suspend fun listSeriesCoverCandidateIds(libraryId: String, downloadedOnly: Boolean): List<GroupedLibraryItemIdRow>

    @Query(
        """
            SELECT * 
            FROM catalog_items 
            WHERE libraryId = :libraryId AND ifnull(seriesId,'') = :seriesId
              AND (:downloadedOnly = 0 OR isDownloaded = 1)
            ORDER BY
                CASE
                    WHEN INSTR(seriesName, '#') > 0
                     AND TRIM(SUBSTR(seriesName, INSTR(seriesName, '#') + 1)) GLOB '*[0-9]*'
                     AND TRIM(SUBSTR(seriesName, INSTR(seriesName, '#') + 1)) NOT GLOB '*[^0-9.]*'
                    THEN 0
                    ELSE 1
                END,
                CAST(TRIM(SUBSTR(seriesName, INSTR(seriesName, '#') + 1)) AS REAL) ASC,
                title COLLATE NOCASE ASC
            """,
    )
    fun observeItemsInSeries(libraryId: String, seriesId: String, downloadedOnly: Boolean): Flow<List<CatalogItemEntity>>

    @Query(
        """
            SELECT * 
            FROM catalog_items 
            WHERE libraryId = :libraryId AND author = :author
              AND (:downloadedOnly = 0 OR isDownloaded = 1)
            ORDER BY title COLLATE NOCASE ASC
            """,
    )
    fun observeItemsByAuthor(libraryId: String, author: String, downloadedOnly: Boolean): Flow<List<CatalogItemEntity>>

    @Query(
        """
        SELECT author AS groupId, libraryItemId
        FROM catalog_items
        WHERE libraryId = :libraryId
          AND author IS NOT NULL
          AND TRIM(author) != ''
          AND (:downloadedOnly = 0 OR isDownloaded = 1)
        ORDER BY author COLLATE NOCASE ASC, addedAtEpochMs DESC, title COLLATE NOCASE ASC
        """,
    )
    suspend fun listAuthorCoverCandidateIds(libraryId: String, downloadedOnly: Boolean): List<GroupedLibraryItemIdRow>

    @Query("DELETE FROM catalog_items WHERE libraryId = :libraryId AND libraryItemId = :id")
    suspend fun deleteItem(libraryId: String, id: String)

    @Query("DELETE FROM catalog_items WHERE libraryId = :libraryId AND libraryItemId IN (:ids)")
    suspend fun deleteItemsByIds(libraryId: String, ids: List<String>)

    @Query("DELETE FROM catalog_items WHERE libraryId = :libraryId")
    suspend fun clearLibrary(libraryId: String)

    @Query(
        """
        SELECT * FROM catalog_items WHERE libraryId = :libraryId AND (
          instr(lower(title), lower(:ql)) > 0
          OR instr(lower(ifnull(author,'')), lower(:ql)) > 0
          OR instr(lower(ifnull(seriesName,'')), lower(:ql)) > 0
        )
          AND (:downloadedOnly = 0 OR isDownloaded = 1)
        ORDER BY title COLLATE NOCASE ASC LIMIT :limit
        """,
    )
    suspend fun searchLocalSubstring(libraryId: String, ql: String, limit: Int, downloadedOnly: Boolean): List<CatalogItemEntity>

    @Query(
        """
        SELECT author AS author, COUNT(*) AS bookCount
        FROM catalog_items
        WHERE libraryId = :libraryId AND isDownloaded = 1
          AND author IS NOT NULL AND TRIM(author) != ''
        GROUP BY author
        ORDER BY author COLLATE NOCASE ASC
        """,
    )
    suspend fun listDownloadedAuthorCounts(libraryId: String): List<AuthorNameCountRow>

    @Query("SELECT * FROM catalog_sync_state WHERE libraryId = :libraryId LIMIT 1")
    fun observeSyncState(libraryId: String): Flow<CatalogSyncStateEntity?>

    @Query("SELECT * FROM catalog_sync_state WHERE libraryId = :libraryId LIMIT 1")
    suspend fun getSyncState(libraryId: String): CatalogSyncStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSyncState(state: CatalogSyncStateEntity)


    @Transaction
    suspend fun deleteItemsNotIn(libraryId: String, ids: List<String>) {
        if (ids.isEmpty()) {
            clearLibrary(libraryId)
            return
        }
        val keep = ids.toSet()
        val stale = listAllIdsForLibrary(libraryId).filter { it !in keep }
        for (chunk in stale.chunked(500)) {
            deleteItemsByIds(libraryId, chunk)
        }
    }
}
