package com.narratome.di

import android.content.Context
import androidx.room.Room
import com.narratome.data.local.db.AppDatabase
import com.narratome.data.local.db.AppDatabaseMigrations
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "audiobook.db")
            .addMigrations(*AppDatabaseMigrations.ALL)
            // Only v7→v8 is exported; older APK DBs (v1–v6) have no upgrade path — recreate cleanly.
            .fallbackToDestructiveMigrationFrom(
                /* dropAllTables */ true,
                1, 2, 3, 4, 5, 6,
            )
            .build()

    @Provides
    fun provideCatalogDao(db: AppDatabase) = db.catalogDao()

    @Provides
    fun provideProgressDao(db: AppDatabase) = db.progressDao()

    @Provides
    fun provideBookmarkDao(db: AppDatabase) = db.bookmarkDao()

    @Provides
    fun provideLocalDownloadDao(db: AppDatabase) = db.localDownloadDao()

    @Provides
    fun provideDownloadJobDao(db: AppDatabase) = db.downloadJobDao()

    @Provides
    fun provideBookmarkOutboxDao(db: AppDatabase) = db.bookmarkOutboxDao()

    @Provides
    fun provideLibraryBrowseListCacheDao(db: AppDatabase) = db.libraryBrowseListCacheDao()

    @Provides
    fun providePlaybackHistoryDao(db: AppDatabase) = db.playbackHistoryDao()
}
