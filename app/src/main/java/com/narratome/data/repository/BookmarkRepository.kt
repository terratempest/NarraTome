package com.narratome.data.repository

import com.narratome.data.local.db.BookmarkDao
import com.narratome.data.local.db.BookmarkEntity
import com.narratome.data.local.db.BookmarkOutboxDao
import com.narratome.data.local.db.BookmarkOutboxEntity
import com.narratome.data.remote.AudiobookshelfApi
import com.narratome.data.remote.dto.BookmarkUpsertRequestDto
import com.narratome.data.remote.dto.toEntity
import com.narratome.sync.BookmarkSyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookmarkRepository @Inject constructor(
    private val api: AudiobookshelfApi,
    private val bookmarkDao: BookmarkDao,
    private val outboxDao: BookmarkOutboxDao,
) {

    fun observeBookmarks(libraryItemId: String): Flow<List<BookmarkEntity>> =
        bookmarkDao.observeForItem(libraryItemId)

    suspend fun refreshFromServerForItem(libraryItemId: String) = withContext(Dispatchers.IO) {
        runCatching {
            val me = api.me()
            val forItem = me.bookmarks.filter { it.libraryItemId == libraryItemId }.map { it.toEntity() }
            bookmarkDao.clearForItem(libraryItemId)
            if (forItem.isNotEmpty()) {
                bookmarkDao.upsertAll(forItem)
            }
        }
    }

    suspend fun createBookmark(
        libraryItemId: String,
        timeSec: Double,
        title: String,
    ): Result<BookmarkEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val time = timeSec.roundToInt().coerceAtLeast(0)
            try {
                val created = api.createBookmark(
                    libraryItemId,
                    BookmarkUpsertRequestDto(time = time, title = title),
                )
                val entity = created.toEntity()
                bookmarkDao.upsertAll(listOf(entity))
                entity
            } catch (e: Exception) {
                val keyTime = time.toDouble()
                outboxDao.insert(
                    BookmarkOutboxEntity(
                        libraryItemId = libraryItemId,
                        op = BookmarkSyncWorker.OP_CREATE,
                        timeSec = keyTime,
                        title = title,
                        lastError = null,
                        createdAtEpochMs = System.currentTimeMillis(),
                    ),
                )
                bookmarkDao.upsertAll(
                    listOf(
                        BookmarkEntity(
                            libraryItemId = libraryItemId,
                            timeSec = keyTime,
                            title = title,
                            dirty = true,
                            tombstone = false,
                            serverCreatedAt = null,
                        ),
                    ),
                )
                error("Saved locally; will sync when online (${e.message})")
            }
        }
    }

    suspend fun deleteBookmark(libraryItemId: String, timeSec: Double): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val time = timeSec.roundToInt().coerceAtLeast(0)
                try {
                    val resp = api.deleteBookmark(libraryItemId, time)
                    if (!resp.isSuccessful) {
                        error("Delete bookmark failed (${resp.code()})")
                    }
                    val key = time.toDouble()
                    bookmarkDao.delete(libraryItemId, key)
                } catch (e: Exception) {
                    outboxDao.insert(
                        BookmarkOutboxEntity(
                            libraryItemId = libraryItemId,
                            op = BookmarkSyncWorker.OP_DELETE,
                            timeSec = time.toDouble(),
                            title = null,
                            lastError = null,
                            createdAtEpochMs = System.currentTimeMillis(),
                        ),
                    )
                    throw e
                }
            }
        }
}
