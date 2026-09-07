package com.narratome.presentation.collections

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.narratome.R
import com.narratome.data.repository.CoverCacheRepository
import com.narratome.data.repository.LibraryRepository
import com.narratome.data.repository.SelectedLibraryRepository
import com.narratome.data.repository.ServerReachabilityRepository
import com.narratome.domain.model.CollectionSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CollectionsState(
    val libraryId: String? = null,
    val collections: List<CollectionSummary> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isOffline: Boolean = false,
)

@HiltViewModel
class CollectionsViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val repository: LibraryRepository,
    private val coverCacheRepository: CoverCacheRepository,
    private val selectedLibraryRepository: SelectedLibraryRepository,
    private val serverReachabilityRepository: ServerReachabilityRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CollectionsState())
    val state: StateFlow<CollectionsState> = _state.asStateFlow()
    val coverRevision: StateFlow<Long> = coverCacheRepository.coverRevision

    init {
        loadCollections()
        viewModelScope.launch {
            serverReachabilityRepository.serverReachable.drop(1).collect { reachable ->
                if (reachable) {
                    loadCollections()
                } else {
                    _state.value = _state.value.copy(
                        collections = emptyList(),
                        isLoading = false,
                        error = null,
                        isOffline = true,
                    )
                }
            }
        }
        viewModelScope.launch {
            selectedLibraryRepository.selectedLibraryId.drop(1).collect {
                loadCollections()
            }
        }
    }

    fun reload() {
        loadCollections()
    }

    private fun loadCollections() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, isOffline = false)
            if (!serverReachabilityRepository.serverReachable.value) {
                _state.value = _state.value.copy(
                    libraryId = selectedLibraryRepository.resolveSelectedLibraryId(online = false),
                    isLoading = false,
                    collections = emptyList(),
                    error = null,
                    isOffline = true,
                )
                return@launch
            }
            val libId = selectedLibraryRepository.resolveSelectedLibraryId(online = true)
            if (libId != null) {
                _state.value = _state.value.copy(libraryId = libId)
                val peek = repository.peekCachedCollections(libId)
                if (peek != null) {
                    _state.value = _state.value.copy(collections = peek, isLoading = true)
                }
                val result = repository.getCollections(libId)
                result.onSuccess { list ->
                    _state.value = _state.value.copy(collections = list, isLoading = false, error = null)
                    prefetchCovers(list)
                }
                result.onFailure { e ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        collections = peek ?: _state.value.collections,
                        error = if (peek != null) {
                            appContext.getString(R.string.browse_list_refresh_cached)
                        } else {
                            e.message
                        },
                    )
                }
            } else {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = appContext.getString(R.string.home_no_library_hint),
                    isOffline = false,
                )
            }
        }
    }

    fun resolveCoverModel(itemId: String): Any? = coverCacheRepository.coverModelForItem(itemId)

    private fun prefetchCovers(collections: List<CollectionSummary>) {
        viewModelScope.launch {
            coverCacheRepository.prefetchCovers(collections.flatMap { it.coverItemIds })
        }
    }
}
