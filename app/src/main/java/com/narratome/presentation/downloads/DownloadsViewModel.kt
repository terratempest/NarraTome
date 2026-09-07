package com.narratome.presentation.downloads

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.narratome.data.local.db.DownloadJobEntity
import com.narratome.data.repository.CoverCacheRepository
import com.narratome.data.repository.DownloadRepository
import com.narratome.data.repository.ItemRepository
import com.narratome.data.repository.ProgressRepository
import com.narratome.domain.download.DownloadJobState
import com.narratome.sync.DownloadForegroundService
import com.narratome.util.formatByteCount
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private data class ItemEnrichment(
    val libraryId: String?,
    val coverModel: Any?,
    val catalogTitle: String?,
)

data class ActiveDownloadRow(
    val job: DownloadJobEntity,
    val libraryId: String?,
    val coverModel: Any?,
    /** Total progress or partial bytes on disk; null if unknown. */
    val sizeSubtitle: String?,
)

data class FailedDownloadRow(
    val job: DownloadJobEntity,
    val libraryId: String?,
    val coverModel: Any?,
    val sizeSubtitle: String?,
)

data class CompletedDownloadItem(
    val libraryItemId: String,
    val libraryId: String?,
    val title: String,
    val coverModel: Any?,
    val completedAtEpochMs: Long,
    val partCount: Int,
    val localSizeLabel: String?,
)

data class DownloadsUiState(
    val activeJobs: List<ActiveDownloadRow> = emptyList(),
    val failedJobs: List<FailedDownloadRow> = emptyList(),
    val completed: List<CompletedDownloadItem> = emptyList(),
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val downloadRepository: DownloadRepository,
    private val itemRepository: ItemRepository,
    private val coverCacheRepository: CoverCacheRepository,
    private val progressRepository: ProgressRepository,
) : ViewModel() {

    private suspend fun enrichItem(itemId: String): ItemEnrichment {
        val cat = itemRepository.getById(itemId)
        val libraryId = cat?.libraryId ?: progressRepository.getMediaProgress(itemId)?.libraryId
        val cover = coverCacheRepository.localCoverFile(itemId)
        return ItemEnrichment(libraryId, cover, cat?.title)
    }

    private suspend fun activeSizeSubtitle(job: DownloadJobEntity): String? {
        return when (job.state) {
            DownloadJobState.RUNNING, DownloadJobState.PAUSED_NETWORK -> when {
                job.bytesTotal > 0L ->
                    "${formatByteCount(job.bytesDownloadedTotal)} / ${formatByteCount(job.bytesTotal)}"
                else -> {
                    val onDisk = downloadRepository.sumOnDiskPartBytes(job.libraryItemId)
                    if (onDisk > 0L) "${formatByteCount(onDisk)} on device" else null
                }
            }
            DownloadJobState.QUEUED -> {
                val onDisk = downloadRepository.sumOnDiskPartBytes(job.libraryItemId)
                if (onDisk > 0L) "${formatByteCount(onDisk)} on device" else null
            }
            else -> null
        }
    }

    private suspend fun failedSizeSubtitle(itemId: String): String? {
        val onDisk = downloadRepository.sumOnDiskPartBytes(itemId)
        return if (onDisk > 0L) "${formatByteCount(onDisk)} on device" else null
    }

    val uiState = combine(
        downloadRepository.observeAllDownloadJobs(),
        downloadRepository.observeAllDownloadManifests(),
        coverCacheRepository.coverRevision,
    ) { jobs, manifests, revision ->
        jobs to manifests to revision
    }.map { (pair, revision) ->
        val (jobs, manifests) = pair
        val activeStates = setOf(DownloadJobState.QUEUED, DownloadJobState.RUNNING, DownloadJobState.PAUSED_NETWORK)
        val failedStates = setOf(DownloadJobState.FAILED, DownloadJobState.CANCELLED)
        val completedJobIds = jobs
            .filter { it.state == DownloadJobState.COMPLETED }
            .map { it.libraryItemId }
            .toSet()

        val activeRows = jobs
            .filter { it.state in activeStates }
            .sortedBy { it.startedAtEpochMs }
            .map { job ->
            val e = enrichItem(job.libraryItemId)
            ActiveDownloadRow(job, e.libraryId, e.coverModel, activeSizeSubtitle(job))
        }

        val failedRows = jobs.filter { it.state in failedStates }.map { job ->
            val e = enrichItem(job.libraryItemId)
            FailedDownloadRow(job, e.libraryId, e.coverModel, failedSizeSubtitle(job.libraryItemId))
        }

        val completedIds = (completedJobIds + manifests.map { it.libraryItemId }).toSet()
        val completedItems = completedIds.map { id ->
            val manifest = manifests.find { it.libraryItemId == id }
            val e = enrichItem(id)
            val title = e.catalogTitle
                ?: jobs.find { it.libraryItemId == id }?.title
                ?: id
            val bytesComplete = downloadRepository.getLocalDownloadTotalBytesIfComplete(id)
            CompletedDownloadItem(
                libraryItemId = id,
                libraryId = e.libraryId,
                title = title,
                coverModel = e.coverModel,
                completedAtEpochMs = manifest?.completedAtEpochMs ?: 0L,
                partCount = manifest?.expectedPartCount ?: 0,
                localSizeLabel = bytesComplete?.let { formatByteCount(it) },
            )
        }.sortedByDescending { it.completedAtEpochMs }

        DownloadsUiState(
            activeJobs = activeRows,
            failedJobs = failedRows,
            completed = completedItems,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        DownloadsUiState(),
    )

    fun cancelDownload(libraryItemId: String, episodeId: String? = null) {
        DownloadForegroundService.cancelDownload(appContext, libraryItemId, episodeId)
    }

    fun retryDownload(libraryItemId: String, title: String?, episodeId: String? = null) {
        DownloadForegroundService.retryDownload(appContext, libraryItemId, title, episodeId)
    }

    fun clearJob(libraryItemId: String, episodeId: String? = null) {
        viewModelScope.launch { downloadRepository.deleteDownloadJobRecord(libraryItemId, episodeId) }
    }

    fun deleteDownload(libraryItemId: String) {
        viewModelScope.launch { downloadRepository.deleteDownload(libraryItemId) }
    }
}
