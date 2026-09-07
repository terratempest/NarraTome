package com.narratome.presentation.authors

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.narratome.R
import com.narratome.data.repository.CoverCacheRepository
import com.narratome.data.repository.ItemRepository
import com.narratome.data.repository.LibraryRepository
import com.narratome.data.repository.SelectedLibraryRepository
import com.narratome.data.repository.ServerReachabilityRepository
import com.narratome.domain.model.AuthorSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthorsState(
    val libraryId: String? = null,
    val authors: List<AuthorSummary> = emptyList(),
    val coverItemIdsByAuthor: Map<String, List<String>> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AuthorsViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val repository: LibraryRepository,
    private val itemRepository: ItemRepository,
    private val coverCacheRepository: CoverCacheRepository,
    private val selectedLibraryRepository: SelectedLibraryRepository,
    private val serverReachabilityRepository: ServerReachabilityRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthorsState())
    val state: StateFlow<AuthorsState> = _state.asStateFlow()
    val coverRevision: StateFlow<Long> = coverCacheRepository.coverRevision

    init {
        loadAuthors()
        viewModelScope.launch {
            serverReachabilityRepository.serverReachable.drop(1).collect { reachable ->
                if (reachable) {
                    loadAuthors()
                } else {
                    paintAuthorsOfflineFirst()
                }
            }
        }
        viewModelScope.launch {
            selectedLibraryRepository.selectedLibraryId.drop(1).collect {
                loadAuthors()
            }
        }
    }

    fun reload() {
        loadAuthors()
    }

    private fun loadAuthors() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            paintAuthorsOfflineFirst()

            if (!serverReachabilityRepository.serverReachable.value) {
                val cachedLib = selectedLibraryRepository.resolveSelectedLibraryId(online = false)
                if (cachedLib == null) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = appContext.getString(R.string.home_no_library_hint),
                    )
                } else {
                    val list = _state.value.authors
                    _state.value = _state.value.copy(
                        libraryId = cachedLib,
                        isLoading = false,
                        error = if (list.isEmpty()) {
                            appContext.getString(R.string.offline_no_downloaded_authors)
                        } else {
                            appContext.getString(R.string.offline_downloaded_items_only)
                        },
                    )
                }
                return@launch
            }

            _state.value = _state.value.copy(isLoading = true)
            val libId = selectedLibraryRepository.resolveSelectedLibraryId(online = true)
            if (libId == null) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = appContext.getString(R.string.home_no_library_hint),
                )
                return@launch
            }

            _state.value = _state.value.copy(libraryId = libId)

            val peek = repository.peekCachedAuthors(libId)
            if (peek != null) {
                _state.value = _state.value.copy(authors = peek.withBooks(), isLoading = true)
            }
            val result = repository.getAuthors(libId)
            result.onSuccess { list ->
                val displayList = list.withBooks()
                val visibleAuthors = displayList.map { it.name }.toSet()
                val coverIds = itemRepository.getAuthorCoverItemIds(libId, downloadedOnly = false)
                    .filterKeys { it in visibleAuthors }
                _state.value = _state.value.copy(
                    authors = displayList,
                    coverItemIdsByAuthor = coverIds,
                    isLoading = false,
                    error = null,
                )
                prefetchCovers(coverIds)
            }
            result.onFailure { e ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    authors = peek?.withBooks() ?: _state.value.authors,
                    error = if (peek != null) {
                        appContext.getString(R.string.browse_list_refresh_cached)
                    } else {
                        e.message
                    },
                )
            }
        }
    }

    private suspend fun paintAuthorsOfflineFirst() {
        val cachedLib = selectedLibraryRepository.resolveSelectedLibraryId(online = false) ?: return
        val list = itemRepository.listDownloadedAuthors(cachedLib).withBooks()
        val visibleAuthors = list.map { it.name }.toSet()
        val coverIds = itemRepository.getAuthorCoverItemIds(cachedLib, downloadedOnly = true)
            .filterKeys { it in visibleAuthors }
        _state.value = _state.value.copy(
            libraryId = cachedLib,
            authors = list,
            coverItemIdsByAuthor = coverIds,
            isLoading = false,
            error = null,
        )
        prefetchCovers(coverIds)
    }

    fun resolveCoverModel(itemId: String): Any? = coverCacheRepository.coverModelForItem(itemId)

    private fun prefetchCovers(coverIds: Map<String, List<String>>) {
        viewModelScope.launch {
            coverCacheRepository.prefetchCovers(coverIds.values.flatten())
        }
    }

    private fun List<AuthorSummary>.withBooks(): List<AuthorSummary> =
        filter { it.bookCount > 0 }
}
