package com.narratome.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LibraryBrowseListCacheDao {

    @Query(
        "SELECT * FROM library_browse_list_cache WHERE libraryId = :libraryId AND kind = :kind AND query = :query LIMIT 1",
    )
    suspend fun getRow(libraryId: String, kind: String, query: String): LibraryBrowseListCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: LibraryBrowseListCacheEntity)
}
