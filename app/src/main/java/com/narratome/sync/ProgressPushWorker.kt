package com.narratome.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.narratome.data.repository.ItemRepository
import com.narratome.data.repository.ProgressRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class ProgressPushWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val progressRepository: ProgressRepository,
    private val itemRepository: ItemRepository,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val pushResult = progressRepository.pushDirtyToServer()
            
            // Refresh only items whose push actually succeeded.
            pushResult.successfulItems.forEach { (itemId, libraryId) ->
                if (libraryId != null) {
                    itemRepository.refreshItemFromServer(itemId, libraryId)
                }
            }

            if (pushResult.hasFailures) Result.retry() else Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
