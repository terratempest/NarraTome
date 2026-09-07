package com.narratome.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<BookmarkEntity>)

    @Query("SELECT * FROM bookmarks WHERE libraryItemId = :libraryItemId AND tombstone = 0 ORDER BY timeSec ASC")
    suspend fun listForItem(libraryItemId: String): List<BookmarkEntity>

    @Query("SELECT * FROM bookmarks WHERE libraryItemId = :libraryItemId AND tombstone = 0 ORDER BY timeSec ASC")
    fun observeForItem(libraryItemId: String): Flow<List<BookmarkEntity>>

    @Query("DELETE FROM bookmarks WHERE libraryItemId = :libraryItemId")
    suspend fun clearForItem(libraryItemId: String)

    @Query("DELETE FROM bookmarks WHERE libraryItemId = :libraryItemId AND timeSec = :timeSec")
    suspend fun delete(libraryItemId: String, timeSec: Double)

    @Query("SELECT COUNT(*) FROM bookmarks WHERE libraryItemId = :libraryItemId AND dirty = 1")
    suspend fun countDirtyForItem(libraryItemId: String): Int
}
