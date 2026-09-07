package com.narratome.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.narratome.sync.ProgressPushWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackgroundWorkScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val wm get() = WorkManager.getInstance(context)

    fun schedulePeriodicMaintenance() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        wm.enqueueUniquePeriodicWork(
            WORK_SYNC_PULL,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncPullWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build(),
        )
        wm.enqueueUniquePeriodicWork(
            WORK_BOOKMARK_SYNC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<BookmarkSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build(),
        )
    }

    fun enqueueImmediatePullAndBookmarks() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        wm.enqueueUniqueWork(
            "sync_pull_immediate",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<SyncPullWorker>()
                .setConstraints(constraints)
                .build(),
        )
        wm.enqueueUniqueWork(
            "bookmark_sync_immediate",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<BookmarkSyncWorker>()
                .setConstraints(constraints)
                .build(),
        )
    }

    fun enqueueProgressPush() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        wm.enqueueUniqueWork(
            "progress_push",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ProgressPushWorker>()
                .setConstraints(constraints)
                .build(),
        )
    }

    fun enqueueSyncWork() {
        wm.enqueue(OneTimeWorkRequestBuilder<ProgressPushWorker>().build())
    }

    companion object {
        private const val WORK_SYNC_PULL = "sync_pull_periodic"
        private const val WORK_BOOKMARK_SYNC = "bookmark_sync_periodic"
    }
}
