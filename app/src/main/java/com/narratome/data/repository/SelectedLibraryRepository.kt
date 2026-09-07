package com.narratome.data.repository

import com.narratome.data.local.preferences.AppPreferencesRepository
import com.narratome.data.remote.dto.LibraryDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SelectedLibraryRepository @Inject constructor(
    private val preferences: AppPreferencesRepository,
    private val libraryRepository: LibraryRepository,
    private val itemRepository: ItemRepository,
) {
    val selectedLibraryId: Flow<String?> = preferences.selectedLibraryId

    suspend fun setSelectedLibraryId(id: String) {
        preferences.setSelectedLibraryId(id)
    }

    suspend fun resolveSelectedLibraryId(
        online: Boolean,
        serverLibraries: List<LibraryDto>? = null,
    ): String? {
        val saved = preferences.selectedLibraryId.first()
        if (online) {
            val libraries = serverLibraries ?: libraryRepository.bookLibraries().getOrNull().orEmpty()
            if (libraries.isNotEmpty()) {
                val resolved = chooseSelectedLibraryId(saved, libraries) ?: return null
                if (resolved != saved) {
                    preferences.setSelectedLibraryId(resolved)
                }
                return resolved
            }
        }

        return resolveOfflineLibraries().second
    }

    suspend fun loadBookLibraries(): Result<List<LibraryDto>> = libraryRepository.bookLibraries()

    suspend fun resolveOfflineLibraries(): Pair<List<LibraryDto>, String?> {
        val libraries = downloadedLibraries(itemRepository.listDownloadedLibraryIds(), preferences.cachedLibraries())
        val saved = preferences.selectedLibraryId.first()
        val selected = chooseSelectedLibraryId(saved, libraries)
        if (selected != null && selected != saved) preferences.setSelectedLibraryId(selected)
        return libraries to selected
    }
}

internal fun downloadedLibraries(ids: List<String>, cached: List<LibraryDto>): List<LibraryDto> {
    val metadata = cached.associateBy { it.id }
    return ids.distinct().map { id ->
        val library = metadata[id] ?: LibraryDto(id = id, mediaType = "book")
        library.copy(name = library.name?.takeIf { it.isNotBlank() } ?: "Library ($id)")
    }.sortedWith(compareBy<LibraryDto, String>(String.CASE_INSENSITIVE_ORDER) { it.name.orEmpty() }.thenBy { it.id })
}

internal fun chooseSelectedLibraryId(
    savedLibraryId: String?,
    serverLibraries: List<LibraryDto>,
): String? {
    if (serverLibraries.isNotEmpty()) {
        return serverLibraries.firstOrNull { it.id == savedLibraryId }?.id ?: serverLibraries.first().id
    }
    return null
}
