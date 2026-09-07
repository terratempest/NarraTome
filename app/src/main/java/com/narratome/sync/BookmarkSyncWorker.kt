package com.narratome.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.narratome.data.local.db.BookmarkDao
import com.narratome.data.local.db.BookmarkOutboxDao
import com.narratome.data.remote.AudiobookshelfApi
import com.narratome.data.remote.dto.BookmarkUpsertRequestDto
import com.narratome.data.remote.dto.toEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlin.math.roundToInt

@HiltWorker
class BookmarkSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val api: AudiobookshelfApi,
    private val outboxDao: BookmarkOutboxDao,
    private val bookmarkDao: BookmarkDao,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = try {
        val pending = outboxDao.listPending(40)
        for (row in pending) {
            runCatching {
                when (row.op) {
                    OP_CREATE -> {
                        val title = row.title ?: return@runCatching
                        val time = row.timeSec.roundToInt().coerceAtLeast(0)
                        val created = api.createBookmark(
                            row.libraryItemId,
                            BookmarkUpsertRequestDto(time = time, title = title),
                        )
                        bookmarkDao.upsertAll(listOf(created.toEntity()))
                        outboxDao.deleteById(row.id)
                    }
                    OP_DELETE -> {
                        val time = row.timeSec.roundToInt().coerceAtLeast(0)
                        val resp = api.deleteBookmark(row.libraryItemId, time)
                        if (!resp.isSuccessful) error("Delete failed ${resp.code()}")
                        bookmarkDao.delete(row.libraryItemId, row.timeSec)
                        outboxDao.deleteById(row.id)
                    }
                    else -> outboxDao.deleteById(row.id)
                }
            }.onFailure { e ->
                outboxDao.markAttempt(row.id, row.attempts + 1, e.message)
            }
        }
        Result.success()
    } catch (_: Exception) {
        Result.retry()
    }

    companion object {
        const val OP_CREATE = "CREATE"
        const val OP_DELETE = "DELETE"
    }
}
