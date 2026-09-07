package com.narratome.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.narratome.data.local.preferences.AppPreferencesRepository
import com.narratome.data.repository.CatalogSyncCoordinator
import com.narratome.data.repository.MeSyncCoordinator
import com.narratome.data.repository.SelectedLibraryRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class SyncPullWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val meSyncCoordinator: MeSyncCoordinator,
    private val catalogSyncCoordinator: CatalogSyncCoordinator,
    private val selectedLibraryRepository: SelectedLibraryRepository,
    private val preferences: AppPreferencesRepository,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = try {
        val policy = preferences.syncConflictPolicy.first()
        meSyncCoordinator.syncFromServer(policy, force = true)
        runCatching {
            val libraryId = selectedLibraryRepository.resolveSelectedLibraryId(online = true) ?: return@runCatching
            catalogSyncCoordinator.syncCatalogNow(libraryId).getOrThrow()
        }
        Result.success()
    } catch (_: Exception) {
        Result.retry()
    }
}
