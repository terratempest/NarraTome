package com.narratome.sync

import android.content.Context
import com.narratome.data.repository.DownloadRepository
import com.narratome.data.repository.MediaNetworkPolicy
import com.narratome.data.repository.mediaProgressKey
import com.narratome.di.ApplicationScope
import com.narratome.domain.download.DownloadJobState
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Keeps the existing call sites; WorkManager owns transfer lifetime and network scheduling. */
object DownloadForegroundService {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Entry { fun downloads(): DownloadCoordinator }

    private fun coordinator(context: Context) =
        EntryPointAccessors.fromApplication(context.applicationContext, Entry::class.java).downloads()

    fun startDownload(context: Context, libraryItemId: String, title: String?, episodeId: String? = null) =
        coordinator(context).start(libraryItemId, title, episodeId)

    fun retryDownload(context: Context, libraryItemId: String, title: String?, episodeId: String? = null) =
        coordinator(context).start(libraryItemId, title, episodeId)

    fun cancelDownload(context: Context, libraryItemId: String? = null, episodeId: String? = null) =
        coordinator(context).cancel(libraryItemId, episodeId)
}

class DownloadCancelReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: android.content.Intent) {
        val item = intent.getStringExtra("item") ?: return
        val pending = goAsync()
        DownloadForegroundService.cancelDownload(context, item, intent.getStringExtra("episode"))
            .invokeOnCompletion { pending.finish() }
    }
}

@Singleton
class DownloadCoordinator @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: DownloadRepository,
    private val policy: MediaNetworkPolicy,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    init {
        scope.launch {
            policy.state.collect { network ->
                    if (network.unrestricted) {
                        val jobs = repository.observeAllDownloadJobs().first()
                        jobs.filter { it.state == DownloadJobState.PAUSED_NETWORK }.forEach {
                            repository.withDownloadLock(it.downloadKey) {
                                if (repository.queueNetworkPausedDownload(it.downloadKey)) {
                                    policy.dismiss(DownloadWorker.actionKey(it.downloadKey))
                                    DownloadWorker.enqueue(context, it.libraryItemId, it.episodeId, it.title)
                                }
                            }
                        }
                    }
                }
        }
    }

    fun start(itemId: String, title: String?, episodeId: String?) {
        scope.launch {
            policy.awaitReady()
            val key = mediaProgressKey(itemId, episodeId)
            val existing = repository.observeDownloadJob(itemId, episodeId).first()
            if (existing?.state in setOf(DownloadJobState.QUEUED, DownloadJobState.RUNNING)) return@launch
            repository.withDownloadLock(key) {
            val action = DownloadWorker.actionKey(key)
            policy.revoke(action)
            repository.enqueueDownloadJob(itemId, title, episodeId)
            if (!policy.allowed(action)) {
                repository.setDownloadJobState(key, DownloadJobState.PAUSED_NETWORK)
                DownloadWorker.enqueue(context, itemId, episodeId, title, unmetered = true,
                    meteredAllowed = policy.state.value.alwaysAllow)
            }
            policy.request(action, title ?: itemId) {
                scope.launch {
                    repository.withDownloadLock(key) {
                    repository.setDownloadJobState(key, DownloadJobState.QUEUED)
                    DownloadWorker.enqueue(context, itemId, episodeId, title)
                    }
                }
            }
            }
        }
    }

    fun cancel(itemId: String?, episodeId: String?) = scope.launch {
            val jobs = repository.observeAllDownloadJobs().first()
            jobs.filter { it.state in setOf(DownloadJobState.QUEUED, DownloadJobState.RUNNING, DownloadJobState.PAUSED_NETWORK) &&
                (itemId == null || it.downloadKey == mediaProgressKey(itemId, episodeId)) }.forEach {
                policy.revoke(DownloadWorker.actionKey(it.downloadKey))
                repository.setDownloadJobState(it.downloadKey, DownloadJobState.CANCELLED)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    androidx.work.WorkManager.getInstance(context)
                        .cancelUniqueWork(DownloadWorker.workName(it.downloadKey)).result.get()
                }
            }
    }
}
