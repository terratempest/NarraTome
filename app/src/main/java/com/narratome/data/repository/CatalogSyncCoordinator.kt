package com.narratome.data.repository

import com.narratome.data.remote.dto.LibraryDto
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the primary book library and runs [CatalogSyncRunner] for local-first catalog sync.
 */
@Singleton
class CatalogSyncCoordinator @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val catalogSyncRunner: CatalogSyncRunner,
    private val itemRepository: ItemRepository,
) {

    suspend fun bookLibraries(): Result<List<LibraryDto>> = libraryRepository.bookLibraries()

    suspend fun firstCachedLibraryIdOrNull(): String? = itemRepository.firstLibraryIdOrNull()

    suspend fun resolvePrimaryBookLibraryId(): String? =
        bookLibraries().getOrNull()?.firstOrNull()?.id ?: firstCachedLibraryIdOrNull()

    /**
     * Delta when a prior full sync completed; otherwise full. Used for periodic sync and routine pull-to-refresh.
     */
    suspend fun syncCatalogNow(libraryId: String): Result<Unit> =
        runCatching {
            catalogSyncRunner.syncLibrary(libraryId, forceFull = false)
        }

    /**
     * Forces a full re-index (enumeration, hydrate all, covers, deletion reconcile).
     */
    suspend fun syncCatalogFullRebuild(libraryId: String): Result<Unit> =
        runCatching {
            catalogSyncRunner.syncLibrary(libraryId, forceFull = true)
        }
}
