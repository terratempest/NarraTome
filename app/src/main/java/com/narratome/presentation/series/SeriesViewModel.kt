package com.narratome.presentation.series

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.narratome.R
import com.narratome.data.repository.CoverCacheRepository
import com.narratome.data.repository.ItemRepository
import com.narratome.data.repository.SelectedLibraryRepository
import com.narratome.data.repository.ServerReachabilityRepository
import com.narratome.domain.model.SeriesSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SeriesState(
    val series: List<SeriesSummary> = emptyList(),
    val coverItemIdsBySeries: Map<String, List<String>> = emptyMap(),
    /** Primary book library used to load this list; needed to open a series' books. */
    val libraryId: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SeriesViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val itemRepository: ItemRepository,
    private val coverCacheRepository: CoverCacheRepository,
    private val selectedLibraryRepository: SelectedLibraryRepository,
    private val serverReachabilityRepository: ServerReachabilityRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SeriesState())
    val state: StateFlow<SeriesState> = _state.asStateFlow()
    val coverRevision: StateFlow<Long> = coverCacheRepository.coverRevision

    init {
        loadSeries()
        viewModelScope.launch {
            serverReachabilityRepository.serverReachable.drop(1).collect { reachable ->
                if (reachable) {
                    loadSeries()
                } else {
                    paintSeriesOfflineFirst()
                }
            }
        }
        viewModelScope.launch {
            selectedLibraryRepository.selectedLibraryId.drop(1).collect {
                loadSeries()
            }
        }
    }

    fun reload() {
        loadSeries()
    }

    private fun loadSeries() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            paintSeriesOfflineFirst()
            if (!serverReachabilityRepository.serverReachable.value) {
                if (selectedLibraryRepository.resolveSelectedLibraryId(online = false) == null) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = appContext.getString(R.string.home_no_library_hint),
                    )
                } else {
                    _state.value = _state.value.copy(isLoading = false)
                }
                return@launch
            }
            _state.value = _state.value.copy(isLoading = true)
            val libId = selectedLibraryRepository.resolveSelectedLibraryId(online = true)
            if (libId != null) {
                val seriesSummary = itemRepository.getSeriesSummary(libId, downloadedOnly = false).withBooks()
                val visibleSeries = seriesSummary.map { it.id }.toSet()
                val coverIds = itemRepository.getSeriesCoverItemIds(libId, downloadedOnly = false)
                    .filterKeys { it in visibleSeries }
                _state.value = _state.value.copy(
                    series = seriesSummary,
                    coverItemIdsBySeries = coverIds,
                    libraryId = libId,
                    isLoading = false,
                    error = null,
                )
                prefetchCovers(coverIds)
            } else {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = appContext.getString(R.string.home_no_library_hint),
                )
            }
        }
    }

    private suspend fun paintSeriesOfflineFirst() {
        val cachedLib = selectedLibraryRepository.resolveSelectedLibraryId(online = false) ?: return
        val seriesSummary = itemRepository.getSeriesSummary(cachedLib, downloadedOnly = true).withBooks()
        val visibleSeries = seriesSummary.map { it.id }.toSet()
        val coverIds = itemRepository.getSeriesCoverItemIds(cachedLib, downloadedOnly = true)
            .filterKeys { it in visibleSeries }
        _state.value = _state.value.copy(
            series = seriesSummary,
            coverItemIdsBySeries = coverIds,
            libraryId = cachedLib,
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

    private fun List<SeriesSummary>.withBooks(): List<SeriesSummary> =
        filter { it.bookCount > 0 }
}
