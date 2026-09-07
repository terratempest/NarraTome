package com.narratome.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        MediaProgressEntity::class,
        BookmarkEntity::class,
        LocalDownloadManifestEntity::class,
        LocalDownloadPartEntity::class,
        DownloadJobEntity::class,
        BookmarkOutboxEntity::class,
        CatalogItemEntity::class,
        CatalogSyncStateEntity::class,
        LibraryBrowseListCacheEntity::class,
        PlaybackHistoryEntity::class,
    ],
    version = 20,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun progressDao(): ProgressDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun localDownloadDao(): LocalDownloadDao
    abstract fun downloadJobDao(): DownloadJobDao
    abstract fun bookmarkOutboxDao(): BookmarkOutboxDao
    abstract fun catalogDao(): CatalogDao
    abstract fun libraryBrowseListCacheDao(): LibraryBrowseListCacheDao
    abstract fun playbackHistoryDao(): PlaybackHistoryDao
}
