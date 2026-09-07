package com.narratome.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object AppDatabaseMigrations {

    /**
     * No SQL changes — same schema as v7. Keeps v7 installs when moving to v8.
     * Installs on DB versions below 7 use [DatabaseModule] destructive fallback (no historical schemas).
     */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Intentionally empty.
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `library_browse_list_cache` (" +
                    "`libraryId` TEXT NOT NULL, `kind` TEXT NOT NULL, `query` TEXT NOT NULL, " +
                    "`payloadJson` TEXT NOT NULL, `updatedAtEpochMs` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`libraryId`, `kind`, `query`))",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_library_browse_list_cache_libraryId_kind` " +
                    "ON `library_browse_list_cache` (`libraryId`, `kind`)",
            )
        }
    }

    /**
     * Local-first catalog: replace `library_items` + `book_metadata_cache` with `catalog_items` + `catalog_sync_state`.
     * Preserves progress, bookmarks, downloads, browse list cache.
     */
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `catalog_items` (" +
                    "`libraryItemId` TEXT NOT NULL, `libraryId` TEXT NOT NULL, " +
                    "`serverUpdatedAtEpochMs` INTEGER NOT NULL, `title` TEXT NOT NULL, " +
                    "`author` TEXT, `seriesName` TEXT, `seriesId` TEXT, `addedAtEpochMs` INTEGER, " +
                    "`coverPath` TEXT, `progress` REAL, `hydrated` INTEGER NOT NULL, " +
                    "`payloadJson` TEXT, `localRowUpdatedAtEpochMs` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`libraryItemId`))",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_catalog_items_libraryId` ON `catalog_items` (`libraryId`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_catalog_items_libraryId_serverUpdatedAtEpochMs` " +
                    "ON `catalog_items` (`libraryId`, `serverUpdatedAtEpochMs`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_catalog_items_libraryId_addedAtEpochMs` " +
                    "ON `catalog_items` (`libraryId`, `addedAtEpochMs`)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `catalog_sync_state` (" +
                    "`libraryId` TEXT NOT NULL, `hasCompletedFullSync` INTEGER NOT NULL, " +
                    "`lastSuccessfulFullSyncAtEpochMs` INTEGER, `lastDeltaSyncAtEpochMs` INTEGER, " +
                    "`lastSyncStartedAtEpochMs` INTEGER, `lastSyncCompletedAtEpochMs` INTEGER, " +
                    "`lastSyncPhase` TEXT, `lastSyncError` TEXT, PRIMARY KEY(`libraryId`))",
            )
            db.execSQL("DROP TABLE IF EXISTS `library_items`")
            db.execSQL("DROP TABLE IF EXISTS `book_metadata_cache`")
        }
    }

    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE catalog_items ADD COLUMN isDownloaded INTEGER NOT NULL DEFAULT 0",
            )
            db.execSQL(
                "UPDATE catalog_items SET isDownloaded = 1 WHERE libraryItemId IN " +
                    "(SELECT libraryItemId FROM local_download_manifest)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_catalog_items_libraryId_isDownloaded` " +
                    "ON `catalog_items` (`libraryId`, `isDownloaded`)",
            )
        }
    }

    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE media_progress ADD COLUMN lastInteractionTime INTEGER",
            )
        }
    }

    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE download_jobs ADD COLUMN bytesDownloadedTotal INTEGER NOT NULL DEFAULT 0",
            )
            db.execSQL(
                "ALTER TABLE download_jobs ADD COLUMN bytesTotal INTEGER NOT NULL DEFAULT 0",
            )
            db.execSQL(
                "ALTER TABLE download_jobs ADD COLUMN startedAtEpochMs INTEGER NOT NULL DEFAULT 0",
            )
            db.execSQL(
                "UPDATE download_jobs SET startedAtEpochMs = updatedAtEpochMs WHERE startedAtEpochMs = 0",
            )
        }
    }

    val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_catalog_items_libraryId_title` " +
                    "ON `catalog_items` (`libraryId`, `title`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_catalog_items_libraryId_isDownloaded_title` " +
                    "ON `catalog_items` (`libraryId`, `isDownloaded`, `title`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_catalog_items_libraryId_isDownloaded_addedAtEpochMs` " +
                    "ON `catalog_items` (`libraryId`, `isDownloaded`, `addedAtEpochMs`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_catalog_items_libraryId_seriesId` " +
                    "ON `catalog_items` (`libraryId`, `seriesId`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_catalog_items_libraryId_isDownloaded_seriesId` " +
                    "ON `catalog_items` (`libraryId`, `isDownloaded`, `seriesId`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_catalog_items_libraryId_author` " +
                    "ON `catalog_items` (`libraryId`, `author`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_catalog_items_libraryId_isDownloaded_author` " +
                    "ON `catalog_items` (`libraryId`, `author`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_media_progress_localDirty` " +
                    "ON `media_progress` (`localDirty`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_media_progress_lastInteractionTime` " +
                    "ON `media_progress` (`lastInteractionTime`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_media_progress_libraryId_lastInteractionTime` " +
                    "ON `media_progress` (`libraryId`, `lastInteractionTime`)",
            )
        }
    }

    val MIGRATION_16_17 = object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `playback_history` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`libraryItemId` TEXT NOT NULL, " +
                    "`eventType` TEXT NOT NULL, " +
                    "`positionSec` REAL NOT NULL, " +
                    "`occurredAtEpochMs` INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_playback_history_libraryItemId_occurredAtEpochMs` " +
                    "ON `playback_history` (`libraryItemId`, `occurredAtEpochMs`)",
            )
        }
    }

    val MIGRATION_17_18 = object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE catalog_items ADD COLUMN mediaType TEXT NOT NULL DEFAULT 'book'",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_catalog_items_libraryId_mediaType` " +
                    "ON `catalog_items` (`libraryId`, `mediaType`)",
            )
        }
    }

    val MIGRATION_18_19 = object : Migration(18, 19) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `media_progress_new` (" +
                    "`progressKey` TEXT NOT NULL, " +
                    "`libraryItemId` TEXT NOT NULL, " +
                    "`currentTimeSec` REAL NOT NULL, " +
                    "`durationSec` REAL NOT NULL, " +
                    "`lastKnownServerLastUpdate` INTEGER, " +
                    "`localDirty` INTEGER NOT NULL, " +
                    "`localRevision` INTEGER NOT NULL, " +
                    "`libraryId` TEXT, " +
                    "`episodeId` TEXT, " +
                    "`hideFromContinueListening` INTEGER NOT NULL DEFAULT 0, " +
                    "`startedAt` INTEGER, " +
                    "`finishedAt` INTEGER, " +
                    "`lastInteractionTime` INTEGER, " +
                    "PRIMARY KEY(`progressKey`))",
            )
            db.execSQL(
                "INSERT OR REPLACE INTO `media_progress_new` (" +
                    "progressKey, libraryItemId, currentTimeSec, durationSec, lastKnownServerLastUpdate, " +
                    "localDirty, localRevision, libraryId, episodeId, hideFromContinueListening, " +
                    "startedAt, finishedAt, lastInteractionTime) " +
                    "SELECT libraryItemId, libraryItemId, currentTimeSec, durationSec, lastKnownServerLastUpdate, " +
                    "localDirty, localRevision, libraryId, episodeId, hideFromContinueListening, " +
                    "startedAt, finishedAt, lastInteractionTime FROM `media_progress`",
            )
            db.execSQL("DROP TABLE `media_progress`")
            db.execSQL("ALTER TABLE `media_progress_new` RENAME TO `media_progress`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_progress_localDirty` ON `media_progress` (`localDirty`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_progress_lastInteractionTime` ON `media_progress` (`lastInteractionTime`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_progress_libraryId_lastInteractionTime` ON `media_progress` (`libraryId`, `lastInteractionTime`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_progress_libraryItemId` ON `media_progress` (`libraryItemId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_progress_libraryItemId_episodeId` ON `media_progress` (`libraryItemId`, `episodeId`)")
        }
    }

    val MIGRATION_19_20 = object : Migration(19, 20) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `download_jobs_new` (" +
                    "`downloadKey` TEXT NOT NULL, " +
                    "`libraryItemId` TEXT NOT NULL, " +
                    "`episodeId` TEXT, " +
                    "`title` TEXT, " +
                    "`state` TEXT NOT NULL, " +
                    "`currentPartIndex` INTEGER NOT NULL, " +
                    "`totalParts` INTEGER NOT NULL, " +
                    "`bytesDownloadedThisPart` INTEGER NOT NULL, " +
                    "`bytesTotalThisPart` INTEGER NOT NULL, " +
                    "`bytesDownloadedTotal` INTEGER NOT NULL, " +
                    "`bytesTotal` INTEGER NOT NULL, " +
                    "`lastError` TEXT, " +
                    "`startedAtEpochMs` INTEGER NOT NULL, " +
                    "`updatedAtEpochMs` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`downloadKey`))",
            )
            db.execSQL(
                "INSERT OR REPLACE INTO `download_jobs_new` (" +
                    "downloadKey, libraryItemId, episodeId, title, state, currentPartIndex, totalParts, " +
                    "bytesDownloadedThisPart, bytesTotalThisPart, bytesDownloadedTotal, bytesTotal, lastError, " +
                    "startedAtEpochMs, updatedAtEpochMs) " +
                    "SELECT libraryItemId, libraryItemId, NULL, title, state, currentPartIndex, totalParts, " +
                    "bytesDownloadedThisPart, bytesTotalThisPart, bytesDownloadedTotal, bytesTotal, lastError, " +
                    "startedAtEpochMs, updatedAtEpochMs FROM `download_jobs`",
            )
            db.execSQL("DROP TABLE `download_jobs`")
            db.execSQL("ALTER TABLE `download_jobs_new` RENAME TO `download_jobs`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_download_jobs_libraryItemId` ON `download_jobs` (`libraryItemId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_download_jobs_libraryItemId_episodeId` ON `download_jobs` (`libraryItemId`, `episodeId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_download_jobs_state` ON `download_jobs` (`state`)")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `local_download_manifest_new` (" +
                    "`downloadKey` TEXT NOT NULL, " +
                    "`libraryItemId` TEXT NOT NULL, " +
                    "`episodeId` TEXT, " +
                    "`expectedPartCount` INTEGER NOT NULL, " +
                    "`completedAtEpochMs` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`downloadKey`))",
            )
            db.execSQL(
                "INSERT OR REPLACE INTO `local_download_manifest_new` (" +
                    "downloadKey, libraryItemId, episodeId, expectedPartCount, completedAtEpochMs) " +
                    "SELECT libraryItemId, libraryItemId, NULL, expectedPartCount, completedAtEpochMs FROM `local_download_manifest`",
            )
            db.execSQL("DROP TABLE `local_download_manifest`")
            db.execSQL("ALTER TABLE `local_download_manifest_new` RENAME TO `local_download_manifest`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_local_download_manifest_libraryItemId` ON `local_download_manifest` (`libraryItemId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_local_download_manifest_libraryItemId_episodeId` ON `local_download_manifest` (`libraryItemId`, `episodeId`)")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `local_download_parts_new` (" +
                    "`downloadKey` TEXT NOT NULL, " +
                    "`libraryItemId` TEXT NOT NULL, " +
                    "`episodeId` TEXT, " +
                    "`partIndex` INTEGER NOT NULL, " +
                    "`fileName` TEXT NOT NULL, " +
                    "`createdAtEpochMs` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`downloadKey`, `partIndex`))",
            )
            db.execSQL(
                "INSERT OR REPLACE INTO `local_download_parts_new` (" +
                    "downloadKey, libraryItemId, episodeId, partIndex, fileName, createdAtEpochMs) " +
                    "SELECT libraryItemId, libraryItemId, NULL, partIndex, fileName, createdAtEpochMs FROM `local_download_parts`",
            )
            db.execSQL("DROP TABLE `local_download_parts`")
            db.execSQL("ALTER TABLE `local_download_parts_new` RENAME TO `local_download_parts`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_local_download_parts_libraryItemId` ON `local_download_parts` (`libraryItemId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_local_download_parts_libraryItemId_episodeId` ON `local_download_parts` (`libraryItemId`, `episodeId`)")
        }
    }

    val ALL: Array<Migration> = arrayOf(
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10,
        MIGRATION_12_13,
        MIGRATION_13_14,
        MIGRATION_14_15,
        MIGRATION_15_16,
        MIGRATION_16_17,
        MIGRATION_17_18,
        MIGRATION_18_19,
        MIGRATION_19_20,
    )
}
