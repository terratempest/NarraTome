package com.narratome.presentation.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.narratome.data.remote.dto.LibraryDto
import com.narratome.data.repository.CatalogSyncCoordinator
import com.narratome.data.repository.SelectedLibraryRepository
import com.narratome.data.repository.ServerReachabilityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibrarySelectionUiState(
    val libraries: List<LibraryDto> = emptyList(),
    val selectedLibraryId: String? = null,
    val selectedLibraryName: String? = null,
    val loading: Boolean = false,
    val enabled: Boolean = false,
    val serverReachable: Boolean = false,
)

@HiltViewModel
class LibrarySelectionViewModel @Inject constructor(
    private val selectedLibraryRepository: SelectedLibraryRepository,
    private val catalogSyncCoordinator: CatalogSyncCoordinator,
    private val serverReachabilityRepository: ServerReachabilityRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(LibrarySelectionUiState())
    val ui: StateFlow<LibrarySelectionUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                serverReachabilityRepository.serverReachable,
                selectedLibraryRepository.selectedLibraryId,
            ) { reachable, _ -> reachable }
                .collectLatest { reachable ->
                    refresh(reachable)
                }
        }
    }

    fun selectLibrary(id: String) {
        val library = _ui.value.libraries.firstOrNull { it.id == id }
        viewModelScope.launch {
            selectedLibraryRepository.setSelectedLibraryId(id)
            _ui.update {
                it.copy(
                    selectedLibraryId = id,
                    selectedLibraryName = library.displayName(),
                    enabled = it.libraries.isNotEmpty(),
                )
            }
            if (serverReachabilityRepository.serverReachable.value) {
                catalogSyncCoordinator.syncCatalogNow(id)
            }
        }
    }

    private suspend fun refresh(serverReachable: Boolean) {
        _ui.update { it.copy(loading = true, serverReachable = serverReachable) }
        if (serverReachable) {
            selectedLibraryRepository.loadBookLibraries()
                .onSuccess { libraries ->
                    val selectedId = selectedLibraryRepository.resolveSelectedLibraryId(
                        online = true,
                        serverLibraries = libraries,
                    )
                    val selected = libraries.firstOrNull { it.id == selectedId }
                    _ui.value = LibrarySelectionUiState(
                        libraries = libraries,
                        selectedLibraryId = selectedId,
                        selectedLibraryName = selected.displayName() ?: selectedId?.let { "Library" },
                        loading = false,
                        enabled = libraries.isNotEmpty(),
                        serverReachable = true,
                    )
                }
                .onFailure {
                    paintOfflineFallback(serverReachable = true)
                }
        } else {
            paintOfflineFallback(serverReachable = false)
        }
    }

    private suspend fun paintOfflineFallback(serverReachable: Boolean) {
        val (libraries, selectedId) = selectedLibraryRepository.resolveOfflineLibraries()
        _ui.value = LibrarySelectionUiState(
            libraries = libraries,
            selectedLibraryId = selectedId,
            selectedLibraryName = libraries.firstOrNull { it.id == selectedId }?.name ?: "Library",
            loading = false,
            enabled = selectedId != null,
            serverReachable = serverReachable,
        )
    }

    private fun LibraryDto?.displayName(): String? =
        this?.name?.takeIf { it.isNotBlank() } ?: this?.id?.takeIf { it.isNotBlank() }?.let { "Library" }
}
