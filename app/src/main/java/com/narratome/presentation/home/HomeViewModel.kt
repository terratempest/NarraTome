package com.narratome.presentation.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.narratome.R
import com.narratome.data.repository.CatalogSyncCoordinator
import com.narratome.data.repository.BookProgressUi
import com.narratome.data.repository.CoverCacheRepository
import com.narratome.data.repository.ItemRepository
import com.narratome.data.repository.MeSyncCoordinator
import com.narratome.data.repository.ProgressRepository
import com.narratome.data.repository.SelectedLibraryRepository
import com.narratome.data.repository.ServerReachabilityRepository
import com.narratome.data.repository.toLibraryItemSummary
import com.narratome.domain.model.LibraryItemSummary
import com.narratome.domain.model.SyncConflictPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeState(
    val recentlyPlayed: List<LibraryItemSummary> = emptyList(),
    val continueSeries: List<LibraryItemSummary> = emptyList(),
    val recentlyAdded: List<LibraryItemSummary> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val itemRepository: ItemRepository,
    private val catalogSyncCoordinator: CatalogSyncCoordinator,
    private val meSyncCoordinator: MeSyncCoordinator,
    private val progressRepository: ProgressRepository,
    private val coverCacheRepository: CoverCacheRepository,
    private val selectedLibraryRepository: SelectedLibraryRepository,
    private val serverReachabilityRepository: ServerReachabilityRepository,
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    private val _manualError = MutableStateFlow<String?>(null)

    val progressMap: StateFlow<Map<String, BookProgressUi>> = progressRepository.observeProgressUiMap()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val coverRevision: StateFlow<Long> = coverCacheRepository.coverRevision
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /**
     * Resolve the library ID we should be showing. This reacts to reachability changes.
     */
    private val libraryIdFlow = combine(
        serverReachabilityRepository.serverReachable,
        selectedLibraryRepository.selectedLibraryId,
    ) { reachable, _ ->
            if (reachable) {
                // When online, ensure user progress is current before resolving visible rows.
                runCatching { meSyncCoordinator.syncFromServer(SyncConflictPolicy.PREFER_SERVER) }
                selectedLibraryRepository.resolveSelectedLibraryId(online = true)
            } else {
                selectedLibraryRepository.resolveSelectedLibraryId(online = false)
            }
        }
        .distinctUntilChanged()

    /**
     * The main UI State, driven by flows from the database.
     */
    val state: StateFlow<HomeState> = combine(
        libraryIdFlow,
        serverReachabilityRepository.serverReachable,
        _isRefreshing,
        _manualError
    ) { libId, isReachable, refreshing, error ->
        DataConfig(libId, !isReachable, refreshing, error)
    }.flatMapLatest { config ->
        val libId = config.libraryId
        if (libId == null) {
            flowOf(
                HomeState(
                    isLoading = false,
                    isRefreshing = config.isRefreshing,
                    error = config.error ?: appContext.getString(R.string.home_no_library_hint)
                )
            )
        } else {
            combine(
                progressRepository.observeContinueListening(libId, 20, config.downloadedOnly),
                progressRepository.observeContinueSeries(libId, 20, config.downloadedOnly),
                itemRepository.observeRecentByAdded(libId, 20, config.downloadedOnly)
            ) { listening, series, added ->
                HomeState(
                    recentlyPlayed = listening.map { row ->
                        LibraryItemSummary(
                            id = row.libraryItemId,
                            libraryId = row.libraryId,
                            title = row.title,
                            author = row.author,
                            mediaType = "book",
                            coverPath = null,
                            progress = if (row.durationSec > 0) (row.currentTimeSec / row.durationSec).toFloat().coerceIn(0f, 1f) else null,
                        )
                    },
                    continueSeries = series.map { row ->
                        LibraryItemSummary(
                            id = row.libraryItemId,
                            libraryId = row.libraryId,
                            title = row.title,
                            author = row.author,
                            mediaType = "book",
                            coverPath = null,
                            progress = null,
                        )
                    },
                    recentlyAdded = added.map { it.toLibraryItemSummary() },
                    isLoading = false,
                    isRefreshing = config.isRefreshing,
                    error = config.error,
                )
            }
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HomeState(isLoading = true)
    )

    fun loadHomeData() {
        // Just triggers a background sync to keep things fresh, UI will react via Room Flows
        viewModelScope.launch {
            if (serverReachabilityRepository.serverReachable.value) {
                val libId = selectedLibraryRepository.resolveSelectedLibraryId(online = true)
                if (libId != null) {
                    catalogSyncCoordinator.syncCatalogNow(libId)
                }
            }
        }
    }

    fun refreshFromPull() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _manualError.value = null
            
            if (serverReachabilityRepository.serverReachable.value) {
                runCatching {
                    val libId = selectedLibraryRepository.resolveSelectedLibraryId(online = true)
                    if (libId != null) {
                        meSyncCoordinator.syncFromServer(SyncConflictPolicy.PREFER_SERVER, force = true)
                        catalogSyncCoordinator.syncCatalogNow(libId)
                    }
                }.onFailure {
                    _manualError.value = it.message
                }
            }
            
            _isRefreshing.value = false
        }
    }

    fun resolveCoverModel(itemId: String): Any? = coverCacheRepository.coverModelForItem(itemId)

    private data class DataConfig(
        val libraryId: String?,
        val downloadedOnly: Boolean,
        val isRefreshing: Boolean,
        val error: String?
    )
}
