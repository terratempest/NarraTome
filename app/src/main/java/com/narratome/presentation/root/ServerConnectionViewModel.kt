package com.narratome.presentation.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.narratome.data.repository.CatalogSyncRunner
import com.narratome.data.repository.ServerReachabilityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ServerConnectionViewModel @Inject constructor(
    reachability: ServerReachabilityRepository,
    catalogSyncRunner: CatalogSyncRunner,
    private val networkPolicy: com.narratome.data.repository.MediaNetworkPolicy,
    downloads: com.narratome.sync.DownloadCoordinator,
) : ViewModel() {
    val meteredActions = networkPolicy.pending
    val mediaNetwork = networkPolicy.state
    fun acknowledgeMetered(key: String) = networkPolicy.acknowledge(key)
    fun dismissMetered(key: String) = networkPolicy.dismiss(key)

    val serverReachable = reachability.serverReachable.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        false,
    )

    val catalogSyncRunning = catalogSyncRunner.syncRunning.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        false,
    )

    init {
        viewModelScope.launch {
            while (isActive) {
                delay(30_000L)
                reachability.refresh()
            }
        }
    }
}
