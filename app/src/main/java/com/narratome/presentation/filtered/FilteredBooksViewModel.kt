package com.narratome.presentation.filtered

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.narratome.data.local.dto.decodeBookDetailFromCatalogPayload
import com.narratome.data.repository.BookProgressUi
import com.narratome.data.repository.CatalogSyncCoordinator
import com.narratome.data.repository.CoverCacheRepository
import com.narratome.data.repository.ItemRepository
import com.narratome.data.repository.LibraryRepository
import com.narratome.data.repository.MeSyncCoordinator
import com.narratome.data.repository.ProgressRepository
import com.narratome.data.repository.ServerReachabilityRepository
import com.narratome.data.repository.toLibraryItemSummary
import com.narratome.domain.model.LibraryItemSummary
import com.narratome.domain.model.SyncConflictPolicy
import com.narratome.util.ErrorMapper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class FilteredBooksUiState(
    val title: String = "",
    val books: List<LibraryItemSummary> = emptyList(),
    val seriesNumbersByItemId: Map<String, String> = emptyMap(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
)

private data class FilteredBooksList(
    val books: List<LibraryItemSummary>,
    val seriesNumbersByItemId: Map<String, String>,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FilteredBooksViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    savedStateHandle: SavedStateHandle,
    private val itemRepository: ItemRepository,
    private val libraryRepository: LibraryRepository,
    private val catalogSyncCoordinator: CatalogSyncCoordinator,
    private val meSyncCoordinator: MeSyncCoordinator,
    private val progressRepository: ProgressRepository,
    private val coverCacheRepository: CoverCacheRepository,
    private val json: Json,
    private val serverReachabilityRepository: ServerReachabilityRepository,
) : ViewModel() {

    private val libraryId: String = checkNotNull(savedStateHandle["libraryId"])
    private val filterType: String = checkNotNull(savedStateHandle["filterType"])
    private val filterValue: String = checkNotNull(savedStateHandle["filterValue"])

    private val _error = MutableStateFlow<String?>(null)
    private val _isRefreshing = MutableStateFlow(false)
    private val _collectionTitle = MutableStateFlow("")
    private val _collectionBooks = MutableStateFlow<List<LibraryItemSummary>>(emptyList())

    private val catalogRowsFlow = serverReachabilityRepository.serverReachable.flatMapLatest { reachable ->
        when (filterType) {
            "series" -> itemRepository.observeItemsInSeries(libraryId, filterValue, downloadedOnly = !reachable)
            "author" -> itemRepository.observeItemsByAuthor(libraryId, filterValue, downloadedOnly = !reachable)
            else -> itemRepository.observeItems(libraryId)
        }
    }

    private val titleFlow = when (filterType) {
        "collection" -> _collectionTitle
        else -> catalogRowsFlow.map { rows ->
            when (filterType) {
                "series" -> rows.firstOrNull()?.seriesName?.let { fullSeriesName ->
                    if (fullSeriesName.contains("#")) {
                        fullSeriesName.substringBefore("#").trim()
                    } else {
                        fullSeriesName
                    }
                } ?: filterValue
                "author" -> filterValue
                else -> "Books"
            }
        }
    }

    private val booksListFlow = when (filterType) {
        "collection" -> _collectionBooks.map { books ->
            FilteredBooksList(books = books, seriesNumbersByItemId = emptyMap())
        }
        else -> catalogRowsFlow.map { rows ->
            val seriesNumbers = mutableMapOf<String, String>()
            val books = rows.map { entity ->
                val fromPayload = decodeBookDetailFromCatalogPayload(entity.payloadJson, json)
                val seq = if (filterType == "series") {
                    fromPayload?.seriesSequence.cleanSeriesSequence()
                        ?: entity.seriesName.sequenceFromSeriesName()
                } else {
                    null
                }
                if (seq != null) {
                    seriesNumbers[entity.libraryItemId] = seq
                }
                entity.toLibraryItemSummary().copy(
                    title = if (seq != null) {
                        "$seq. ${entity.title}"
                    } else {
                        entity.title
                    },
                )
            }
            FilteredBooksList(books = books, seriesNumbersByItemId = seriesNumbers)
        }
    }

    val ui: StateFlow<FilteredBooksUiState> = combine(
        titleFlow,
        booksListFlow,
        _error,
        _isRefreshing,
    ) { derivedTitle, booksList, err, refreshing ->
        FilteredBooksUiState(
            title = derivedTitle,
            books = booksList.books,
            seriesNumbersByItemId = booksList.seriesNumbersByItemId,
            isLoading = derivedTitle.isBlank() && booksList.books.isEmpty() && err == null,
            isRefreshing = refreshing,
            error = err,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FilteredBooksUiState())

    val progressMap: StateFlow<Map<String, BookProgressUi>> = progressRepository.observeProgressUiMap()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val coverRevision = coverCacheRepository.coverRevision
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    init {
        if (filterType == "collection") {
            viewModelScope.launch {
                loadCollectionBooks()
            }
        } else if (serverReachabilityRepository.serverReachable.value) {
            viewModelScope.launch {
                catalogSyncCoordinator.syncCatalogNow(libraryId)
            }
        }
    }

    fun refreshFromPull() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _error.value = null
            if (filterType == "collection") {
                if (serverReachabilityRepository.serverReachable.value) {
                    meSyncCoordinator.syncFromServer(SyncConflictPolicy.PREFER_SERVER, force = true)
                }
                loadCollectionBooks(refreshingAlreadySet = true)
            } else if (serverReachabilityRepository.serverReachable.value) {
                meSyncCoordinator.syncFromServer(SyncConflictPolicy.PREFER_SERVER, force = true)
                catalogSyncCoordinator.syncCatalogNow(libraryId).onFailure { e ->
                    _error.value = ErrorMapper.map(appContext, e)
                }
            }
            _isRefreshing.value = false
        }
    }

    private suspend fun loadCollectionBooks(refreshingAlreadySet: Boolean = false) {
        if (!refreshingAlreadySet) {
            _isRefreshing.value = true
        }
        _error.value = null
        if (!serverReachabilityRepository.serverReachable.value) {
            _collectionTitle.value = _collectionTitle.value.ifBlank { "Collection" }
            _collectionBooks.value = emptyList()
            _error.value = "Offline"
            if (!refreshingAlreadySet) {
                _isRefreshing.value = false
            }
            return
        }
        libraryRepository.getCollectionBooks(filterValue, libraryId)
            .onSuccess { (title, books) ->
                _collectionTitle.value = title
                _collectionBooks.value = books
                coverCacheRepository.prefetchCovers(books.map { it.id })
            }
            .onFailure { e ->
                _collectionTitle.value = _collectionTitle.value.ifBlank { "Collection" }
                _error.value = ErrorMapper.map(appContext, e)
            }
        if (!refreshingAlreadySet) {
            _isRefreshing.value = false
        }
    }

    fun resolveCoverModel(itemId: String): Any? = coverCacheRepository.coverModelForItem(itemId)
}

private fun String?.cleanSeriesSequence(): String? =
    this
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

private fun String?.sequenceFromSeriesName(): String? =
    this
        ?.substringAfter("#", missingDelimiterValue = "")
        .cleanSeriesSequence()
