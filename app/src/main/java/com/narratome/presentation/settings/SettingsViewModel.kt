package com.narratome.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.narratome.BuildConfig
import com.narratome.data.local.preferences.AppPreferencesRepository
import com.narratome.data.repository.CoverCacheRepository
import com.narratome.data.repository.DownloadRepository
import com.narratome.data.repository.ServerReachabilityRepository
import com.narratome.domain.model.EndpointMode
import com.narratome.domain.model.SyncConflictPolicy
import com.narratome.util.formatByteCount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val policy: SyncConflictPolicy = SyncConflictPolicy.ALWAYS_ASK,
    val endpointMode: EndpointMode = EndpointMode.AUTO,
    val primaryUrl: String = "",
    val secondaryUrl: String = "",
    val selectedLibraryId: String? = null,
    val serverReachable: Boolean = false,
    val alwaysAllowMetered: Boolean = false,
    val downloadMaxParallelParts: Int = 4,
    val downloadMaxParallelBooks: Int = 2,
    val downloadStrictCleanup: Boolean = false,
    val downloadVerifySizes: Boolean = true,
    val seekBackSeconds: Int = 30,
    val seekForwardSeconds: Int = 30,
    val playbackNotificationRetentionMinutes: Int = 10,
    val sleepTimerFadeSeconds: Int = 60,
    val sleepTimerShakeToExtend: Boolean = true,
    val activeDownloadCount: Int = 0,
    val failedDownloadCount: Int = 0,
    val coverCacheFiles: Int = 0,
    val coverCacheSizeLabel: String = "0 B",
    val appVersion: String = BuildConfig.VERSION_NAME,
    val message: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: AppPreferencesRepository,
    private val reachability: ServerReachabilityRepository,
    private val downloadRepository: DownloadRepository,
    private val coverCacheRepository: CoverCacheRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(SettingsUiState())
    val ui: StateFlow<SettingsUiState> = _ui.asStateFlow()

    init {
        collectPreferenceState()
        collectRuntimeState()
        refreshCoverCacheStats()
    }

    private fun collectPreferenceState() {
        viewModelScope.launch {
            preferences.syncConflictPolicy.collect { p -> _ui.update { it.copy(policy = p) } }
        }
        viewModelScope.launch {
            preferences.endpointMode.collect { v -> _ui.update { it.copy(endpointMode = v) } }
        }
        viewModelScope.launch {
            preferences.primaryUrl.collect { v -> _ui.update { it.copy(primaryUrl = v) } }
        }
        viewModelScope.launch {
            preferences.secondaryUrl.collect { v -> _ui.update { it.copy(secondaryUrl = v) } }
        }
        viewModelScope.launch {
            preferences.selectedLibraryId.collect { v -> _ui.update { it.copy(selectedLibraryId = v) } }
        }
        viewModelScope.launch {
            preferences.alwaysAllowMetered.collect { v -> _ui.update { it.copy(alwaysAllowMetered = v) } }
        }
        viewModelScope.launch {
            preferences.downloadMaxParallelParts.collect { v -> _ui.update { it.copy(downloadMaxParallelParts = v) } }
        }
        viewModelScope.launch {
            preferences.downloadMaxParallelBooks.collect { v -> _ui.update { it.copy(downloadMaxParallelBooks = v) } }
        }
        viewModelScope.launch {
            preferences.downloadStrictCleanup.collect { v -> _ui.update { it.copy(downloadStrictCleanup = v) } }
        }
        viewModelScope.launch {
            preferences.downloadVerifySizes.collect { v -> _ui.update { it.copy(downloadVerifySizes = v) } }
        }
        viewModelScope.launch {
            preferences.seekBackSeconds.collect { v -> _ui.update { it.copy(seekBackSeconds = v) } }
        }
        viewModelScope.launch {
            preferences.seekForwardSeconds.collect { v -> _ui.update { it.copy(seekForwardSeconds = v) } }
        }
        viewModelScope.launch {
            preferences.playbackNotificationRetentionMinutes.collect { v ->
                _ui.update { it.copy(playbackNotificationRetentionMinutes = v) }
            }
        }
        viewModelScope.launch {
            preferences.sleepTimerFadeSeconds.collect { v -> _ui.update { it.copy(sleepTimerFadeSeconds = v) } }
        }
        viewModelScope.launch {
            preferences.sleepTimerShakeToExtend.collect { v -> _ui.update { it.copy(sleepTimerShakeToExtend = v) } }
        }
    }

    private fun collectRuntimeState() {
        viewModelScope.launch {
            reachability.serverReachable.collect { v -> _ui.update { it.copy(serverReachable = v) } }
        }
        viewModelScope.launch {
            downloadRepository.observeActiveDownloadCount()
                .collect { v -> _ui.update { it.copy(activeDownloadCount = v) } }
        }
        viewModelScope.launch {
            downloadRepository.observeFailedDownloadCount()
                .collect { v -> _ui.update { it.copy(failedDownloadCount = v) } }
        }
    }

    fun setPolicy(policy: SyncConflictPolicy) {
        viewModelScope.launch {
            preferences.setSyncConflictPolicy(policy)
        }
    }

    fun setAlwaysAllowMetered(v: Boolean) {
        viewModelScope.launch { preferences.setAlwaysAllowMetered(v) }
    }


    fun setDownloadMaxParallelParts(v: Int) {
        viewModelScope.launch { preferences.setDownloadMaxParallelParts(v) }
    }

    fun setDownloadMaxParallelBooks(v: Int) {
        viewModelScope.launch { preferences.setDownloadMaxParallelBooks(v) }
    }

    fun setDownloadStrictCleanup(v: Boolean) {
        viewModelScope.launch { preferences.setDownloadStrictCleanup(v) }
    }

    fun setDownloadVerifySizes(v: Boolean) {
        viewModelScope.launch { preferences.setDownloadVerifySizes(v) }
    }

    fun setSeekBackSeconds(v: Int) {
        viewModelScope.launch { preferences.setSeekBackSeconds(v) }
    }

    fun setSeekForwardSeconds(v: Int) {
        viewModelScope.launch { preferences.setSeekForwardSeconds(v) }
    }

    fun setPlaybackNotificationRetentionMinutes(v: Int) {
        viewModelScope.launch { preferences.setPlaybackNotificationRetentionMinutes(v) }
    }

    fun setSleepTimerFadeSeconds(v: Int) {
        viewModelScope.launch { preferences.setSleepTimerFadeSeconds(v) }
    }

    fun setSleepTimerShakeToExtend(v: Boolean) {
        viewModelScope.launch { preferences.setSleepTimerShakeToExtend(v) }
    }

    fun clearCoverCache() {
        viewModelScope.launch {
            val cleared = coverCacheRepository.clearCoverCache()
            _ui.update {
                it.copy(
                    coverCacheFiles = 0,
                    coverCacheSizeLabel = "0 B",
                    message = "Cleared ${cleared.fileCount} cached cover files (${formatByteCount(cleared.bytes)}).",
                )
            }
        }
    }

    fun clearFailedDownloadRecords() {
        viewModelScope.launch {
            val count = downloadRepository.clearFailedDownloadRecords()
            _ui.update { it.copy(message = "Cleared $count failed or cancelled download records.") }
        }
    }

    fun refreshDiagnostics() {
        viewModelScope.launch {
            reachability.refresh()
            refreshCoverCacheStats()
            _ui.update { it.copy(message = "Diagnostics refreshed.") }
        }
    }

    fun clearMessage() {
        _ui.update { it.copy(message = null) }
    }

    private fun refreshCoverCacheStats() {
        viewModelScope.launch {
            val stats = coverCacheRepository.coverCacheStats()
            _ui.update {
                it.copy(
                    coverCacheFiles = stats.fileCount,
                    coverCacheSizeLabel = formatByteCount(stats.bytes),
                )
            }
        }
    }
}
