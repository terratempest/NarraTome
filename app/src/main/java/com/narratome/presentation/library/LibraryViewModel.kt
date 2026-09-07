package com.narratome.presentation.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.narratome.data.local.preferences.AppPreferencesRepository
import com.narratome.data.local.preferences.LibraryViewMode
import com.narratome.R
import com.narratome.data.local.db.CatalogItemEntity
import com.narratome.data.remote.dto.LibraryDto
import com.narratome.data.repository.BookProgressUi
import com.narratome.data.repository.CatalogSyncCoordinator
import com.narratome.data.repository.CoverCacheRepository
import com.narratome.data.repository.ItemRepository
import com.narratome.data.repository.LibraryRepository
import com.narratome.data.repository.MeSyncCoordinator
import com.narratome.data.repository.ProgressRepository
import com.narratome.data.repository.SelectedLibraryRepository
import com.narratome.data.repository.ServerReachabilityRepository
import com.narratome.domain.model.LibraryItemSummary
import com.narratome.domain.model.SyncConflictPolicy
import com.narratome.util.ErrorMapper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val libraries: List<LibraryDto> = emptyList(),
    val selectedLibraryId: String? = null,
    val message: String? = null,
    val busy: Boolean = false,
    val searchMode: Boolean = false,
    val searchItems: List<LibraryItemSummary> = emptyList(),
    val viewMode: LibraryViewMode = LibraryViewMode.LargeGrid,
    val sort: LibrarySortState = LibrarySortState(LibrarySortOption.Title),
    val filterOption: LibraryFilterOption = LibraryFilterOption.All,
)

enum class LibrarySortOption(val queryKey: String) {
    Title("TITLE"),
    RecentlyAdded("RECENTLY_ADDED"),
    Author("AUTHOR"),
}

data class LibrarySortState(
    val option: LibrarySortOption? = null,
    val reversed: Boolean = false,
) {
    fun cycle(selected: LibrarySortOption): LibrarySortState = when {
        option != selected -> LibrarySortState(selected)
        !reversed -> copy(reversed = true)
        else -> LibrarySortState()
    }
}

enum class LibraryFilterOption {
    All,
    InProgress,
    Downloaded,
}

private data class CatalogPagingRequest(
    val libraryId: String,
    val session: Int,
    val downloadedOnly: Boolean,
    val inProgressOnly: Boolean,
    val sort: LibrarySortState,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val libraryRepository: LibraryRepository,
    private val itemRepository: ItemRepository,
    private val catalogSyncCoordinator: CatalogSyncCoordinator,
    private val coverCacheRepository: CoverCacheRepository,
    private val progressRepository: ProgressRepository,
    private val meSyncCoordinator: MeSyncCoordinator,
    private val selectedLibraryRepository: SelectedLibraryRepository,
    private val serverReachabilityRepository: ServerReachabilityRepository,
    private val appPreferencesRepository: AppPreferencesRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(LibraryUiState())
    val ui: StateFlow<LibraryUiState> = _ui.asStateFlow()

    private val libraryIdForPaging = MutableStateFlow<String?>(null)
    private val pagingSession = MutableStateFlow(0)
    private val searchQuery = MutableStateFlow("")
    private val sort = MutableStateFlow(_ui.value.sort)
    private val filterOption = MutableStateFlow(LibraryFilterOption.All)

    private var lastSearchQueryRaw: String = ""

    val pagedItems: Flow<PagingData<CatalogItemEntity>> =
        combine(
            libraryIdForPaging,
            pagingSession,
            serverReachabilityRepository.serverReachable,
            sort,
            filterOption,
        ) { lid, session, reachable, sort, filter ->
            if (lid == null) null
            else CatalogPagingRequest(
                libraryId = lid,
                session = session,
                downloadedOnly = !reachable || filter == LibraryFilterOption.Downloaded,
                inProgressOnly = filter == LibraryFilterOption.InProgress,
                sort = sort,
            )
        }
            .distinctUntilChanged()
            .flatMapLatest { request ->
                if (request == null) flowOf(PagingData.empty<CatalogItemEntity>()) else Pager(
                    config = PagingConfig(pageSize = 30, enablePlaceholders = false),
                    pagingSourceFactory = {
                        itemRepository.pagingSource(
                            libraryId = request.libraryId,
                            downloadedOnly = request.downloadedOnly,
                            inProgressOnly = request.inProgressOnly,
                            sortKey = request.sort.option?.queryKey,
                            sortReversed = request.sort.reversed,
                        )
                    },
                ).flow
            }
            .cachedIn(viewModelScope)

    val progressMap: StateFlow<Map<String, BookProgressUi>> = progressRepository.observeProgressUiMap()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val coverRevision: StateFlow<Long> = coverCacheRepository.coverRevision
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    init {
        refreshLibraries()
        syncProgress()
        viewModelScope.launch {
            appPreferencesRepository.libraryViewMode.collect { mode ->
                _ui.update { it.copy(viewMode = mode) }
            }
        }
        viewModelScope.launch {
            serverReachabilityRepository.serverReachable.drop(1).collect { reachable ->
                if (reachable) {
                    refreshLibraries()
                } else {
                    paintLibraryOfflineFirst()
                }
            }
        }
        viewModelScope.launch {
            selectedLibraryRepository.selectedLibraryId.drop(1).collect { selected ->
                if (!selected.isNullOrBlank() && selected != _ui.value.selectedLibraryId) {
                    applySelectedLibrary(selected, syncIfOnline = serverReachabilityRepository.serverReachable.value)
                }
            }
        }
        viewModelScope.launch {
            searchQuery
                .debounce(300)
                .distinctUntilChanged()
                .collect { query ->
                    if (query.isNotBlank() && query == lastSearchQueryRaw) {
                        performSearch(query)
                    }
                }
        }
    }

    private fun syncProgress() {
        if (!serverReachabilityRepository.serverReachable.value) return
        viewModelScope.launch {
            runCatching { meSyncCoordinator.syncFromServer(SyncConflictPolicy.PREFER_SERVER) }
        }
    }

    fun resolveCoverModel(itemId: String): Any? = coverCacheRepository.coverModelForItem(itemId)

    private suspend fun paintLibraryOfflineFirst() {
        val (libraries, sel) = selectedLibraryRepository.resolveOfflineLibraries()
        libraryIdForPaging.value = sel
        pagingSession.update { it + 1 }
        _ui.update {
            it.copy(
                libraries = libraries,
                selectedLibraryId = sel,
                busy = false,
                message = null,
                searchMode = false,
                searchItems = emptyList(),
            )
        }
    }

    fun refreshLibraries() {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            if (!serverReachabilityRepository.serverReachable.value) {
                paintLibraryOfflineFirst()
                val sel = _ui.value.selectedLibraryId
                if (sel == null) {
                    _ui.update {
                        it.copy(
                            busy = false,
                            message = appContext.getString(R.string.home_no_library_hint),
                        )
                    }
                } else {
                    _ui.update { it.copy(busy = false) }
                }
                return@launch
            }

            _ui.update { it.copy(busy = true) }
            val res = catalogSyncCoordinator.bookLibraries()
            res.onSuccess { libs ->
                val sel = selectedLibraryRepository.resolveSelectedLibraryId(online = true, serverLibraries = libs)
                _ui.update {
                    it.copy(
                        libraries = libs,
                        selectedLibraryId = sel,
                        busy = false,
                        message = null,
                        searchMode = false,
                        searchItems = emptyList(),
                    )
                }
                sel?.let {
                    libraryIdForPaging.value = it
                    launch { catalogSyncCoordinator.syncCatalogNow(it) }
                }
            }.onFailure { e ->
                paintLibraryOfflineFirst()
                _ui.update {
                    it.copy(
                        busy = false,
                        message = ErrorMapper.map(appContext, e),
                    )
                }
            }
        }
    }

    fun selectLibrary(id: String) {
        viewModelScope.launch {
            selectedLibraryRepository.setSelectedLibraryId(id)
            applySelectedLibrary(id, syncIfOnline = serverReachabilityRepository.serverReachable.value)
        }
    }

    private fun applySelectedLibrary(id: String, syncIfOnline: Boolean) {
        lastSearchQueryRaw = ""
        _ui.update { it.copy(selectedLibraryId = id, searchMode = false, searchItems = emptyList()) }
        libraryIdForPaging.value = id
        if (syncIfOnline) {
            viewModelScope.launch { catalogSyncCoordinator.syncCatalogNow(id) }
        } else {
            viewModelScope.launch { paintLibraryOfflineFirst() }
        }
    }

    fun clearSearch() {
        lastSearchQueryRaw = ""
        searchQuery.value = ""
        _ui.update { it.copy(searchMode = false, searchItems = emptyList()) }
    }

    fun search(query: String) {
        lastSearchQueryRaw = query
        searchQuery.value = query
    }

    fun setViewMode(mode: LibraryViewMode) {
        _ui.update { it.copy(viewMode = mode) }
        viewModelScope.launch {
            appPreferencesRepository.setLibraryViewMode(mode)
        }
    }

    fun setSortOption(option: LibrarySortOption) {
        sort.update { it.cycle(option) }
        _ui.update { it.copy(sort = sort.value) }
    }

    fun setFilterOption(option: LibraryFilterOption) {
        filterOption.value = option
        _ui.update { it.copy(filterOption = option) }
    }

    private fun performSearch(query: String) {
        val lib = _ui.value.selectedLibraryId ?: return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            val q = query.trim()
            if (!serverReachabilityRepository.serverReachable.value) {
                val local = itemRepository.searchLocalSubstring(lib, query, downloadedOnly = true)
                _ui.update {
                    it.copy(
                        busy = false,
                        searchMode = true,
                        searchItems = local,
                        message = if (local.isEmpty()) {
                            appContext.getString(R.string.offline_no_downloaded_matches)
                        } else {
                            appContext.getString(R.string.offline_downloaded_items_only)
                        },
                    )
                }
                return@launch
            }
            if (q.isNotEmpty()) {
                val peekSearch = libraryRepository.peekCachedSearchResults(lib, q)
                if (peekSearch != null) {
                    _ui.update {
                        it.copy(
                            busy = true,
                            searchMode = true,
                            searchItems = peekSearch,
                            message = null,
                        )
                    }
                }
            }
            val r = libraryRepository.searchBooks(lib, query)
            r.onSuccess { list ->
                _ui.update {
                    it.copy(busy = false, searchMode = true, searchItems = list, message = null)
                }
            }.onFailure { e ->
                val local = itemRepository.searchLocalSubstring(lib, query, downloadedOnly = false)
                if (local.isNotEmpty()) {
                    _ui.update {
                        it.copy(
                            busy = false,
                            searchMode = true,
                            searchItems = local,
                            message = appContext.getString(R.string.search_offline_local_matches),
                        )
                    }
                } else {
                    val peekOnly = libraryRepository.peekCachedSearchResults(lib, query.trim())
                    _ui.update {
                        it.copy(
                            busy = false,
                            searchMode = peekOnly != null,
                            searchItems = peekOnly ?: emptyList(),
                            message = when {
                                peekOnly != null -> appContext.getString(R.string.browse_list_refresh_cached)
                                else -> e.message
                            },
                        )
                    }
                }
            }
        }
    }

    fun resyncCatalogClearAndReload() {
        val libId = _ui.value.selectedLibraryId ?: return
        if (!serverReachabilityRepository.serverReachable.value) {
            _ui.update {
                it.copy(message = appContext.getString(R.string.offline_action_requires_network))
            }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, message = null) }
            catalogSyncCoordinator.syncCatalogFullRebuild(libId)
                .onSuccess {
                    pagingSession.update { it + 1 }
                    _ui.update { it.copy(busy = false, message = null) }
                }
                .onFailure { e ->
                    _ui.update {
                        it.copy(
                            busy = false,
                            message = ErrorMapper.map(appContext, e),
                        )
                    }
                }
        }
    }

    fun dismissMessage() {
        _ui.update { it.copy(message = null) }
    }

    fun refreshFromPull() {
        val s = _ui.value
        when {
            s.searchMode && lastSearchQueryRaw.isNotBlank() -> performSearch(lastSearchQueryRaw)
            !s.searchMode && s.selectedLibraryId != null -> {
                val libId = s.selectedLibraryId
                if (!serverReachabilityRepository.serverReachable.value) {
                    pagingSession.update { it + 1 }
                    return
                }
                viewModelScope.launch {
                    _ui.update { it.copy(busy = true, message = null) }
                    runCatching {
                        meSyncCoordinator.syncFromServer(SyncConflictPolicy.PREFER_SERVER, force = true)
                    }.onFailure { e ->
                        _ui.update {
                            it.copy(
                                busy = false,
                                message = ErrorMapper.map(appContext, e),
                            )
                        }
                        return@launch
                    }
                    catalogSyncCoordinator.syncCatalogNow(libId)
                        .onSuccess { _ui.update { it.copy(busy = false, message = null) } }
                        .onFailure { e ->
                            _ui.update {
                                it.copy(
                                    busy = false,
                                    message = ErrorMapper.map(appContext, e),
                                )
                            }
                        }
                }
            }
            else -> Unit
        }
    }
}
