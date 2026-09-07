package com.narratome.data.repository

import com.narratome.data.local.db.BookmarkDao
import com.narratome.data.remote.AudiobookshelfApi
import com.narratome.data.remote.dto.AudioBookmarkDto
import com.narratome.data.remote.dto.toEntity
import com.narratome.domain.bookmark.BookmarkConflictRules
import com.narratome.domain.model.SyncConflictPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pulls [AudiobookshelfApi.me] and merges server progress and bookmarks according to [SyncConflictPolicy].
 * Background pulls treat [SyncConflictPolicy.ALWAYS_ASK] like [SyncConflictPolicy.PREFER_SERVER] for bookmarks
 * when local rows are dirty (no UI); progress skips dirty rows in that case.
 * Future UI can use [BookmarkConflictRules.SAME_POSITION_THRESHOLD_SEC] for bookmark prompts.
 */
@Singleton
class MeSyncRepository @Inject constructor(
    private val api: AudiobookshelfApi,
    private val progressRepository: ProgressRepository,
    private val bookmarkDao: BookmarkDao,
) {

    suspend fun syncFromServer(policy: SyncConflictPolicy) = withContext(Dispatchers.IO) {
        val me = api.me()
        progressRepository.mergeMediaProgressFromMe(me.mediaProgress, policy)
        mergeBookmarks(me.bookmarks, policy)
    }

    private suspend fun mergeBookmarks(remote: List<AudioBookmarkDto>, policy: SyncConflictPolicy) {
        val byItem = remote.groupBy { it.libraryItemId }
        for ((libraryItemId, list) in byItem) {
            val dirtyCount = bookmarkDao.countDirtyForItem(libraryItemId)
            if (dirtyCount == 0) {
                bookmarkDao.clearForItem(libraryItemId)
                bookmarkDao.upsertAll(list.map { it.toEntity() })
                continue
            }
            when (policy) {
                SyncConflictPolicy.PREFER_SERVER -> {
                    bookmarkDao.clearForItem(libraryItemId)
                    bookmarkDao.upsertAll(list.map { it.toEntity() })
                }
                SyncConflictPolicy.PREFER_LOCAL -> Unit
                SyncConflictPolicy.ALWAYS_ASK -> {
                    bookmarkDao.clearForItem(libraryItemId)
                    bookmarkDao.upsertAll(list.map { it.toEntity() })
                }
            }
        }
    }
}
