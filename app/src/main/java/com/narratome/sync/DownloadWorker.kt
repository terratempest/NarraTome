package com.narratome.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.narratome.MainActivity
import com.narratome.R
import com.narratome.data.local.preferences.AppPreferencesRepository
import com.narratome.data.repository.DownloadRepository
import com.narratome.data.repository.MediaNetworkPolicy
import com.narratome.data.repository.mediaProgressKey
import com.narratome.domain.download.DownloadJobState
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val repository: DownloadRepository,
    private val preferences: AppPreferencesRepository,
    private val policy: MediaNetworkPolicy,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        policy.awaitReady()
        val item = inputData.getString("item") ?: return Result.failure()
        val episode = inputData.getString("episode")
        val title = inputData.getString("title")
        val key = mediaProgressKey(item, episode)
        val action = actionKey(key)
        return repository.withDownloadLock(key) {
            val job = repository.observeDownloadJob(item, episode).first()
            if (job == null || job.state !in setOf(DownloadJobState.QUEUED, DownloadJobState.RUNNING, DownloadJobState.PAUSED_NETWORK)) {
                return@withDownloadLock Result.success()
            }
            var acquired = false
            var succeeded = false
            val runningContext = currentCoroutineContext()
            var networkPaused = false
            fun shouldPause(): Boolean {
                if (!policy.allowed(action)) networkPaused = true
                return networkPaused
            }
            try {
                if (!shouldPause()) {
                    setForeground(getForegroundInfo())
                    val maxBooks = preferences.downloadMaxParallelBooks.first()
                    while (!acquired && !shouldPause()) {
                        acquired = slots.withLock {
                            if (runningBooks < maxBooks) { runningBooks++; true } else false
                        }
                        if (!acquired) delay(250)
                    }
                    if (!shouldPause()) {
                        succeeded = repository.downloadLibraryItem(
                            libraryItemId = item, title = title, episodeId = episode,
                            isCancelled = { isStopped || !runningContext.isActive || shouldPause() },
                            isNetworkPaused = { networkPaused },
                            strictCleanup = preferences.downloadStrictCleanup.first(),
                            maxParallelParts = preferences.downloadMaxParallelParts.first(),
                            verifySizesFromApi = preferences.downloadVerifySizes.first(),
                            resumeFromParts = true,
                        ).isSuccess
                    }
                }
                if (networkPaused) {
                    repository.setDownloadJobState(key, DownloadJobState.PAUSED_NETWORK)
                    if (!isStopped) enqueue(applicationContext, item, episode, title,
                        unmetered = !policy.state.value.unrestricted, append = true,
                        meteredAllowed = policy.state.value.alwaysAllow)
                } else {
                    policy.revoke(action)
                }
                if (networkPaused || succeeded) Result.success() else Result.failure()
            } finally {
                withContext(NonCancellable) {
                    if (acquired) slots.withLock { runningBooks-- }
                    if (isStopped && repository.observeDownloadJob(item, episode).first()?.state == DownloadJobState.RUNNING) {
                        repository.setDownloadJobState(key, DownloadJobState.PAUSED_NETWORK)
                    }
                }
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel("downloads", applicationContext.getString(R.string.download_channel_name), NotificationManager.IMPORTANCE_LOW))
        }
        val open = PendingIntent.getActivity(applicationContext, 0,
            Intent(applicationContext, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val cancel = PendingIntent.getBroadcast(applicationContext, 0,
            Intent(applicationContext, DownloadCancelReceiver::class.java)
                .setData(android.net.Uri.parse("narratome:cancel/${id}"))
                .putExtra("item", inputData.getString("item"))
                .putExtra("episode", inputData.getString("episode")),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(applicationContext, "downloads")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(inputData.getString("title") ?: applicationContext.getString(R.string.download_channel_name))
            .setContentText(applicationContext.getString(R.string.download_notification_title))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, applicationContext.getString(R.string.download_cancel), cancel)
            .setContentIntent(open).setOngoing(true).setProgress(0, 0, true).build()
        val notificationId = 7200 + (id.hashCode() and 0xffff)
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(notificationId, notification)
    }

    companion object {
        private val slots = Mutex()
        private var runningBooks = 0
        fun actionKey(key: String) = "download:$key"
        fun workName(key: String) = "media-download:$key"
        fun enqueue(context: Context, item: String, episode: String?, title: String?, unmetered: Boolean = false,
                    append: Boolean = false, meteredAllowed: Boolean = false) {
            // Active work uses the shared policy, including local Wi-Fi without validated internet.
            val networkType = if (!unmetered) NetworkType.NOT_REQUIRED
                else if (meteredAllowed) NetworkType.CONNECTED else NetworkType.UNMETERED
            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(workDataOf("item" to item, "episode" to episode, "title" to title))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(networkType).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(workName(mediaProgressKey(item, episode)),
                if (append) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE, request)
        }
    }
}
