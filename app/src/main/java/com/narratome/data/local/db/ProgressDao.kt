package com.narratome.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MediaProgressEntity)

    @Query("SELECT * FROM media_progress WHERE progressKey = :progressKey LIMIT 1")
    suspend fun get(progressKey: String): MediaProgressEntity?

    @Query("SELECT * FROM media_progress WHERE progressKey = :progressKey LIMIT 1")
    fun observe(progressKey: String): Flow<MediaProgressEntity?>

    @Query("SELECT * FROM media_progress WHERE localDirty = 1")
    suspend fun getAllDirty(): List<MediaProgressEntity>

    @Query("SELECT * FROM media_progress")
    fun getAll(): Flow<List<MediaProgressEntity>>

    @Query(
        """
        SELECT mp.libraryItemId AS libraryItemId,
               li.libraryId AS libraryId,
               CASE
                   WHEN mp.episodeId IS NOT NULL AND TRIM(mp.episodeId) != '' THEN li.title
                   ELSE li.title
               END AS title,
               li.author AS author,
               mp.currentTimeSec AS currentTimeSec,
               mp.durationSec AS durationSec,
               mp.episodeId AS episodeId
        FROM media_progress mp
        INNER JOIN catalog_items li ON li.libraryItemId = mp.libraryItemId
        WHERE mp.durationSec > 0
          AND mp.currentTimeSec > 30
          AND mp.currentTimeSec < mp.durationSec - 15
          AND mp.finishedAt IS NULL
          AND mp.hideFromContinueListening = 0
          AND (:downloadedOnly = 0 OR li.isDownloaded = 1)
        ORDER BY mp.lastInteractionTime DESC, mp.lastKnownServerLastUpdate DESC
        LIMIT :limit
        """,
    )
    fun observeContinueListening(limit: Int, downloadedOnly: Boolean): Flow<List<ContinueListenRow>>

    @Query(
        """
        SELECT mp.libraryItemId AS libraryItemId,
               li.libraryId AS libraryId,
               li.title AS title,
               li.author AS author,
               mp.currentTimeSec AS currentTimeSec,
               mp.durationSec AS durationSec,
               mp.episodeId AS episodeId
        FROM media_progress mp
        INNER JOIN catalog_items li ON li.libraryItemId = mp.libraryItemId
        WHERE li.libraryId = :libraryId
          AND mp.durationSec > 0
          AND mp.currentTimeSec > 30
          AND mp.currentTimeSec < mp.durationSec - 15
          AND mp.finishedAt IS NULL
          AND mp.hideFromContinueListening = 0
          AND (:downloadedOnly = 0 OR li.isDownloaded = 1)
        ORDER BY mp.lastInteractionTime DESC, mp.lastKnownServerLastUpdate DESC
        LIMIT :limit
        """,
    )
    fun observeContinueListeningForLibrary(
        libraryId: String,
        limit: Int,
        downloadedOnly: Boolean,
    ): Flow<List<ContinueListenRow>>

    @Query(
        """
        SELECT mp.libraryItemId AS libraryItemId,
               li.libraryId AS libraryId,
               li.title AS title,
               li.author AS author,
               mp.currentTimeSec AS currentTimeSec,
               mp.durationSec AS durationSec,
               mp.episodeId AS episodeId
        FROM media_progress mp
        INNER JOIN catalog_items li ON li.libraryItemId = mp.libraryItemId
        WHERE mp.durationSec > 0
          AND mp.currentTimeSec > 30
          AND mp.currentTimeSec < mp.durationSec - 15
          AND mp.finishedAt IS NULL
          AND mp.hideFromContinueListening = 0
          AND (:downloadedOnly = 0 OR li.isDownloaded = 1)
        ORDER BY mp.lastInteractionTime DESC, mp.lastKnownServerLastUpdate DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun listContinueListening(
        limit: Int,
        downloadedOnly: Boolean,
        offset: Int = 0,
    ): List<ContinueListenRow>

    @Query(
        """
        SELECT mp.libraryItemId AS libraryItemId,
               li.libraryId AS libraryId,
               li.title AS title,
               li.author AS author,
               mp.currentTimeSec AS currentTimeSec,
               mp.durationSec AS durationSec,
               mp.episodeId AS episodeId
        FROM media_progress mp
        INNER JOIN catalog_items li ON li.libraryItemId = mp.libraryItemId
        WHERE li.libraryId = :libraryId
          AND mp.durationSec > 0
          AND mp.currentTimeSec > 30
          AND mp.currentTimeSec < mp.durationSec - 15
          AND mp.finishedAt IS NULL
          AND mp.hideFromContinueListening = 0
          AND (:downloadedOnly = 0 OR li.isDownloaded = 1)
        ORDER BY mp.lastInteractionTime DESC, mp.lastKnownServerLastUpdate DESC
        LIMIT :limit
        """,
    )
    suspend fun listContinueListeningForLibrary(
        libraryId: String,
        limit: Int,
        downloadedOnly: Boolean,
    ): List<ContinueListenRow>

    @Query(
        """
        WITH series_books AS (
            SELECT
                c.libraryItemId,
                c.libraryId,
                c.title,
                c.author,
                ifnull(mp.currentTimeSec, 0) AS currentTimeSec,
                ifnull(mp.durationSec, 0) AS durationSec,
                CASE
                    WHEN c.seriesId IS NOT NULL AND TRIM(c.seriesId) != ''
                    THEN c.libraryId || ':' || c.seriesId
                    ELSE c.libraryId || ':' || TRIM(SUBSTR(c.seriesName, 1, INSTR(c.seriesName, '#') - 1))
                END AS seriesKey,
                TRIM(SUBSTR(c.seriesName, 1, INSTR(c.seriesName, '#') - 1)) AS seriesNameClean,
                CAST(TRIM(SUBSTR(c.seriesName, INSTR(c.seriesName, '#') + 1)) AS REAL) AS seriesNumber,
                mp.finishedAt,
                COALESCE(mp.lastInteractionTime, mp.finishedAt, mp.lastKnownServerLastUpdate, 0) AS progressUpdatedAt
            FROM catalog_items c
            LEFT JOIN media_progress mp ON mp.progressKey = c.libraryItemId
            WHERE c.mediaType = 'book'
              AND c.seriesName IS NOT NULL
              AND c.seriesName != ''
              AND INSTR(c.seriesName, '#') > 0
              AND TRIM(SUBSTR(c.seriesName, INSTR(c.seriesName, '#') + 1)) GLOB '*[0-9]*'
              AND TRIM(SUBSTR(c.seriesName, INSTR(c.seriesName, '#') + 1)) NOT GLOB '*[^0-9.]*'
              AND (:downloadedOnly = 0 OR c.isDownloaded = 1)
        ),
        progress_books AS (
            SELECT *
            FROM series_books
            WHERE finishedAt IS NOT NULL
               OR (durationSec > 0 AND currentTimeSec > 30 AND currentTimeSec < durationSec - 15)
        ),
        last_progress AS (
            SELECT seriesKey, MAX(seriesNumber) AS lastSeriesNumber
            FROM progress_books
            GROUP BY seriesKey
        ),
        last_books AS (
            SELECT pb.*
            FROM progress_books pb
            JOIN last_progress lp
              ON pb.seriesKey = lp.seriesKey
             AND pb.seriesNumber = lp.lastSeriesNumber
        ),
        recommended AS (
            SELECT
                next.libraryItemId,
                next.libraryId,
                next.title,
                next.author,
                next.currentTimeSec,
                next.durationSec,
                lb.progressUpdatedAt
            FROM last_books lb
            JOIN series_books next
              ON next.seriesKey = lb.seriesKey
             AND next.seriesNumber > lb.seriesNumber
            WHERE lb.finishedAt IS NOT NULL
              AND NOT EXISTS (
                  SELECT 1
                  FROM series_books closer
                  WHERE closer.seriesKey = lb.seriesKey
                    AND closer.seriesNumber > lb.seriesNumber
                    AND closer.seriesNumber < next.seriesNumber
              )
            UNION ALL
            SELECT
                lb.libraryItemId,
                lb.libraryId,
                lb.title,
                lb.author,
                lb.currentTimeSec,
                lb.durationSec,
                lb.progressUpdatedAt
            FROM last_books lb
            WHERE lb.finishedAt IS NULL
        )
        SELECT libraryItemId, libraryId, title, author, currentTimeSec, durationSec, NULL AS episodeId
        FROM recommended
        ORDER BY progressUpdatedAt DESC, title COLLATE NOCASE ASC
        LIMIT :limit
        """,
    )
    fun observeContinueSeries(limit: Int, downloadedOnly: Boolean): Flow<List<ContinueListenRow>>

    @Query(
        """
        WITH series_books AS (
            SELECT
                c.libraryItemId,
                c.libraryId,
                c.title,
                c.author,
                ifnull(mp.currentTimeSec, 0) AS currentTimeSec,
                ifnull(mp.durationSec, 0) AS durationSec,
                CASE
                    WHEN c.seriesId IS NOT NULL AND TRIM(c.seriesId) != ''
                    THEN c.libraryId || ':' || c.seriesId
                    ELSE c.libraryId || ':' || TRIM(SUBSTR(c.seriesName, 1, INSTR(c.seriesName, '#') - 1))
                END AS seriesKey,
                TRIM(SUBSTR(c.seriesName, 1, INSTR(c.seriesName, '#') - 1)) AS seriesNameClean,
                CAST(TRIM(SUBSTR(c.seriesName, INSTR(c.seriesName, '#') + 1)) AS REAL) AS seriesNumber,
                mp.finishedAt,
                COALESCE(mp.lastInteractionTime, mp.finishedAt, mp.lastKnownServerLastUpdate, 0) AS progressUpdatedAt
            FROM catalog_items c
            LEFT JOIN media_progress mp ON mp.progressKey = c.libraryItemId
            WHERE c.mediaType = 'book'
              AND c.libraryId = :libraryId
              AND c.seriesName IS NOT NULL
              AND c.seriesName != ''
              AND INSTR(c.seriesName, '#') > 0
              AND TRIM(SUBSTR(c.seriesName, INSTR(c.seriesName, '#') + 1)) GLOB '*[0-9]*'
              AND TRIM(SUBSTR(c.seriesName, INSTR(c.seriesName, '#') + 1)) NOT GLOB '*[^0-9.]*'
              AND (:downloadedOnly = 0 OR c.isDownloaded = 1)
        ),
        progress_books AS (
            SELECT *
            FROM series_books
            WHERE finishedAt IS NOT NULL
               OR (durationSec > 0 AND currentTimeSec > 30 AND currentTimeSec < durationSec - 15)
        ),
        last_progress AS (
            SELECT seriesKey, MAX(seriesNumber) AS lastSeriesNumber
            FROM progress_books
            GROUP BY seriesKey
        ),
        last_books AS (
            SELECT pb.*
            FROM progress_books pb
            JOIN last_progress lp
              ON pb.seriesKey = lp.seriesKey
             AND pb.seriesNumber = lp.lastSeriesNumber
        ),
        recommended AS (
            SELECT
                next.libraryItemId,
                next.libraryId,
                next.title,
                next.author,
                next.currentTimeSec,
                next.durationSec,
                lb.progressUpdatedAt
            FROM last_books lb
            JOIN series_books next
              ON next.seriesKey = lb.seriesKey
             AND next.seriesNumber > lb.seriesNumber
            WHERE lb.finishedAt IS NOT NULL
              AND NOT EXISTS (
                  SELECT 1
                  FROM series_books closer
                  WHERE closer.seriesKey = lb.seriesKey
                    AND closer.seriesNumber > lb.seriesNumber
                    AND closer.seriesNumber < next.seriesNumber
              )
            UNION ALL
            SELECT
                lb.libraryItemId,
                lb.libraryId,
                lb.title,
                lb.author,
                lb.currentTimeSec,
                lb.durationSec,
                lb.progressUpdatedAt
            FROM last_books lb
            WHERE lb.finishedAt IS NULL
        )
        SELECT libraryItemId, libraryId, title, author, currentTimeSec, durationSec, NULL AS episodeId
        FROM recommended
        ORDER BY progressUpdatedAt DESC, title COLLATE NOCASE ASC
        LIMIT :limit
        """,
    )
    fun observeContinueSeriesForLibrary(
        libraryId: String,
        limit: Int,
        downloadedOnly: Boolean,
    ): Flow<List<ContinueListenRow>>

    @Query(
        """
        WITH series_books AS (
            SELECT
                c.libraryItemId,
                c.libraryId,
                c.title,
                c.author,
                ifnull(mp.currentTimeSec, 0) AS currentTimeSec,
                ifnull(mp.durationSec, 0) AS durationSec,
                CASE
                    WHEN c.seriesId IS NOT NULL AND TRIM(c.seriesId) != ''
                    THEN c.libraryId || ':' || c.seriesId
                    ELSE c.libraryId || ':' || TRIM(SUBSTR(c.seriesName, 1, INSTR(c.seriesName, '#') - 1))
                END AS seriesKey,
                TRIM(SUBSTR(c.seriesName, 1, INSTR(c.seriesName, '#') - 1)) AS seriesNameClean,
                CAST(TRIM(SUBSTR(c.seriesName, INSTR(c.seriesName, '#') + 1)) AS REAL) AS seriesNumber,
                mp.finishedAt,
                COALESCE(mp.lastInteractionTime, mp.finishedAt, mp.lastKnownServerLastUpdate, 0) AS progressUpdatedAt
            FROM catalog_items c
            LEFT JOIN media_progress mp ON mp.progressKey = c.libraryItemId
            WHERE c.mediaType = 'book'
              AND c.seriesName IS NOT NULL
              AND c.seriesName != ''
              AND INSTR(c.seriesName, '#') > 0
              AND TRIM(SUBSTR(c.seriesName, INSTR(c.seriesName, '#') + 1)) GLOB '*[0-9]*'
              AND TRIM(SUBSTR(c.seriesName, INSTR(c.seriesName, '#') + 1)) NOT GLOB '*[^0-9.]*'
              AND (:downloadedOnly = 0 OR c.isDownloaded = 1)
        ),
        progress_books AS (
            SELECT *
            FROM series_books
            WHERE finishedAt IS NOT NULL
               OR (durationSec > 0 AND currentTimeSec > 30 AND currentTimeSec < durationSec - 15)
        ),
        last_progress AS (
            SELECT seriesKey, MAX(seriesNumber) AS lastSeriesNumber
            FROM progress_books
            GROUP BY seriesKey
        ),
        last_books AS (
            SELECT pb.*
            FROM progress_books pb
            JOIN last_progress lp
              ON pb.seriesKey = lp.seriesKey
             AND pb.seriesNumber = lp.lastSeriesNumber
        ),
        recommended AS (
            SELECT
                next.libraryItemId,
                next.libraryId,
                next.title,
                next.author,
                next.currentTimeSec,
                next.durationSec,
                lb.progressUpdatedAt
            FROM last_books lb
            JOIN series_books next
              ON next.seriesKey = lb.seriesKey
             AND next.seriesNumber > lb.seriesNumber
            WHERE lb.finishedAt IS NOT NULL
              AND NOT EXISTS (
                  SELECT 1
                  FROM series_books closer
                  WHERE closer.seriesKey = lb.seriesKey
                    AND closer.seriesNumber > lb.seriesNumber
                    AND closer.seriesNumber < next.seriesNumber
              )
            UNION ALL
            SELECT
                lb.libraryItemId,
                lb.libraryId,
                lb.title,
                lb.author,
                lb.currentTimeSec,
                lb.durationSec,
                lb.progressUpdatedAt
            FROM last_books lb
            WHERE lb.finishedAt IS NULL
        )
        SELECT libraryItemId, libraryId, title, author, currentTimeSec, durationSec, NULL AS episodeId
        FROM recommended
        ORDER BY progressUpdatedAt DESC, title COLLATE NOCASE ASC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun listContinueSeries(
        limit: Int,
        downloadedOnly: Boolean,
        offset: Int = 0,
    ): List<ContinueListenRow>

    @Query(
        """
        WITH series_books AS (
            SELECT
                c.libraryItemId,
                c.libraryId,
                c.title,
                c.author,
                ifnull(mp.currentTimeSec, 0) AS currentTimeSec,
                ifnull(mp.durationSec, 0) AS durationSec,
                CASE
                    WHEN c.seriesId IS NOT NULL AND TRIM(c.seriesId) != ''
                    THEN c.libraryId || ':' || c.seriesId
                    ELSE c.libraryId || ':' || TRIM(SUBSTR(c.seriesName, 1, INSTR(c.seriesName, '#') - 1))
                END AS seriesKey,
                TRIM(SUBSTR(c.seriesName, 1, INSTR(c.seriesName, '#') - 1)) AS seriesNameClean,
                CAST(TRIM(SUBSTR(c.seriesName, INSTR(c.seriesName, '#') + 1)) AS REAL) AS seriesNumber,
                mp.finishedAt,
                COALESCE(mp.lastInteractionTime, mp.finishedAt, mp.lastKnownServerLastUpdate, 0) AS progressUpdatedAt
            FROM catalog_items c
            LEFT JOIN media_progress mp ON mp.progressKey = c.libraryItemId
            WHERE c.mediaType = 'book'
              AND c.libraryId = :libraryId
              AND c.seriesName IS NOT NULL
              AND c.seriesName != ''
              AND INSTR(c.seriesName, '#') > 0
              AND TRIM(SUBSTR(c.seriesName, INSTR(c.seriesName, '#') + 1)) GLOB '*[0-9]*'
              AND TRIM(SUBSTR(c.seriesName, INSTR(c.seriesName, '#') + 1)) NOT GLOB '*[^0-9.]*'
              AND (:downloadedOnly = 0 OR c.isDownloaded = 1)
        ),
        progress_books AS (
            SELECT *
            FROM series_books
            WHERE finishedAt IS NOT NULL
               OR (durationSec > 0 AND currentTimeSec > 30 AND currentTimeSec < durationSec - 15)
        ),
        last_progress AS (
            SELECT seriesKey, MAX(seriesNumber) AS lastSeriesNumber
            FROM progress_books
            GROUP BY seriesKey
        ),
        last_books AS (
            SELECT pb.*
            FROM progress_books pb
            JOIN last_progress lp
              ON pb.seriesKey = lp.seriesKey
             AND pb.seriesNumber = lp.lastSeriesNumber
        ),
        recommended AS (
            SELECT
                next.libraryItemId,
                next.libraryId,
                next.title,
                next.author,
                next.currentTimeSec,
                next.durationSec,
                lb.progressUpdatedAt
            FROM last_books lb
            JOIN series_books next
              ON next.seriesKey = lb.seriesKey
             AND next.seriesNumber > lb.seriesNumber
            WHERE lb.finishedAt IS NOT NULL
              AND NOT EXISTS (
                  SELECT 1
                  FROM series_books closer
                  WHERE closer.seriesKey = lb.seriesKey
                    AND closer.seriesNumber > lb.seriesNumber
                    AND closer.seriesNumber < next.seriesNumber
              )
            UNION ALL
            SELECT
                lb.libraryItemId,
                lb.libraryId,
                lb.title,
                lb.author,
                lb.currentTimeSec,
                lb.durationSec,
                lb.progressUpdatedAt
            FROM last_books lb
            WHERE lb.finishedAt IS NULL
        )
        SELECT libraryItemId, libraryId, title, author, currentTimeSec, durationSec, NULL AS episodeId
        FROM recommended
        ORDER BY progressUpdatedAt DESC, title COLLATE NOCASE ASC
        LIMIT :limit
        """,
    )
    suspend fun listContinueSeriesForLibrary(
        libraryId: String,
        limit: Int,
        downloadedOnly: Boolean,
    ): List<ContinueListenRow>

    @Query("SELECT * FROM media_progress ORDER BY lastInteractionTime DESC LIMIT 1")
    suspend fun getLastPlayed(): MediaProgressEntity?
}

data class ContinueListenRow(
    val libraryItemId: String,
    val libraryId: String,
    val title: String,
    val author: String?,
    val currentTimeSec: Double,
    val durationSec: Double,
    val episodeId: String? = null,
)
