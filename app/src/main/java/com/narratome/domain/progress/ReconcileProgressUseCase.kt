package com.narratome.domain.progress

import com.narratome.data.local.preferences.AppPreferencesRepository
import com.narratome.data.repository.ProgressReconcileOutcome
import com.narratome.data.repository.ProgressRepository
import com.narratome.domain.model.BookDetail
import kotlinx.coroutines.flow.first
import javax.inject.Inject

data class ReconciledBookDetail(
    val detail: BookDetail,
    val progressConflict: ProgressReconcileOutcome.ChooseProgress? = null
)

class ReconcileProgressUseCase @Inject constructor(
    private val progressRepository: ProgressRepository,
    private val preferences: AppPreferencesRepository,
) {
    suspend operator fun invoke(itemId: String, serverDetail: BookDetail): ReconciledBookDetail {
        val policy = preferences.syncConflictPolicy.first()
        val outcome = progressRepository.reconcileDetailOpened(
            libraryItemId = itemId,
            serverTimeSec = serverDetail.currentTimeSec,
            serverDurationSec = serverDetail.durationSec,
            serverLastUpdate = serverDetail.serverProgressLastUpdate,
            policy = policy,
            libraryId = serverDetail.libraryId,
            finishedAt = serverDetail.finishedAt,
        )
        val localRow = progressRepository.getMediaProgress(itemId)
        val displayDetail = when (outcome) {
            is ProgressReconcileOutcome.KeptLocalDirty ->
                localRow?.let { lr ->
                    serverDetail.copy(
                        currentTimeSec = lr.currentTimeSec,
                        durationSec = if (lr.durationSec > 0) lr.durationSec else serverDetail.durationSec,
                        finishedAt = lr.finishedAt,
                    )
                } ?: serverDetail
            is ProgressReconcileOutcome.ChooseProgress ->
                serverDetail.copy(currentTimeSec = outcome.localSec, finishedAt = localRow?.finishedAt)
            else -> serverDetail
        }
        val conflict = outcome as? ProgressReconcileOutcome.ChooseProgress
        return ReconciledBookDetail(displayDetail, conflict)
    }
}
