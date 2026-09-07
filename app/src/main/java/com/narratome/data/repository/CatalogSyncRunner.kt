package com.narratome.data.repository

import com.narratome.data.local.db.CatalogDao
import com.narratome.data.local.db.CatalogItemEntity
import com.narratome.data.local.db.CatalogSyncStateEntity
import com.narratome.data.local.dto.BookDetailCacheDto
import com.narratome.data.local.dto.toCacheDto
import com.narratome.data.remote.AudiobookshelfApi
import com.narratome.data.remote.dto.toBookDetailParsed
import com.narratome.domain.model.BookChapter
import com.narratome.domain.model.BookDetail
import com.narratome.domain.model.PodcastEpisode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import javax.inject.Inject
import javax.inject.Singleton

private const val PAGE_SIZE = 50
private const val HYDRATE_PARALLELISM = 6

internal class CatalogSyncActivity {
    private val activeCount = MutableStateFlow(0)
    val running = activeCount.map { it > 0 }.distinctUntilChanged()

    suspend fun <T> track(block: suspend () -> T): T {
        activeCount.update { it + 1 }
        try {
            return block()
        } finally {
            activeCount.update { it - 1 }
        }
    }
}

/**
 * Orchestrates catalog sync (library items + cover files). Does not download or stream audio;
 * playback and user-initiated downloads use [ItemRepository] / [DownloadRepository] only.
 */
@Singleton
class CatalogSyncRunner @Inject constructor(
    private val api: AudiobookshelfApi,
    private val catalogDao: CatalogDao,
    private val coverCacheRepository: CoverCacheRepository,
    private val progressRepository: ProgressRepository,
    private val json: Json,
) {
    private val activity = CatalogSyncActivity()
    val syncRunning = activity.running

    suspend fun syncLibrary(libraryId: String, forceFull: Boolean) = activity.track {
        syncLibraryContents(libraryId, forceFull)
    }

    private suspend fun syncLibraryContents(libraryId: String, forceFull: Boolean) = withContext(Dispatchers.IO) {
        val state = catalogDao.getSyncState(libraryId)
        val doFull = forceFull || state?.hasCompletedFullSync != true
        val now = System.currentTimeMillis()
        catalogDao.upsertSyncState(
            (state ?: CatalogSyncStateEntity(libraryId = libraryId)).copy(
                lastSyncStartedAtEpochMs = now,
                lastSyncPhase = if (doFull) "full" else "delta",
                lastSyncError = null,
            ),
        )
        try {
            val serverIds: List<String>
            if (doFull) {
                serverIds = runEnumerateAndUpsertStubs(libraryId, mergeWithExisting = true)
                runHydrateAllPending(libraryId)
                runCoverSweep(libraryId)
                catalogDao.deleteItemsNotIn(libraryId, serverIds)
            } else {
                serverIds = runDelta(libraryId)
                runCoverSweep(libraryId)
                catalogDao.deleteItemsNotIn(libraryId, serverIds)
            }
            val done = System.currentTimeMillis()
            val prev = catalogDao.getSyncState(libraryId)
            catalogDao.upsertSyncState(
                CatalogSyncStateEntity(
                    libraryId = libraryId,
                    hasCompletedFullSync = true,
                    lastSuccessfulFullSyncAtEpochMs = if (doFull) done else prev?.lastSuccessfulFullSyncAtEpochMs,
                    lastDeltaSyncAtEpochMs = if (!doFull) done else prev?.lastDeltaSyncAtEpochMs,
                    lastSyncStartedAtEpochMs = prev?.lastSyncStartedAtEpochMs,
                    lastSyncCompletedAtEpochMs = done,
                    lastSyncPhase = if (doFull) "full_done" else "delta_done",
                    lastSyncError = null,
                ),
            )
        } catch (e: Exception) {
            val prev = catalogDao.getSyncState(libraryId)
            catalogDao.upsertSyncState(
                (prev ?: CatalogSyncStateEntity(libraryId = libraryId)).copy(
                    lastSyncError = e.message,
                    lastSyncCompletedAtEpochMs = System.currentTimeMillis(),
                    lastSyncPhase = "error",
                ),
            )
            throw e
        }
    }

    private suspend fun runEnumerateAndUpsertStubs(libraryId: String, mergeWithExisting: Boolean): List<String> {
        val serverIds = LinkedHashSet<String>()
        var page = 0
        while (true) {
            val resp = api.libraryItems(
                libraryId = libraryId,
                limit = PAGE_SIZE,
                page = page,
                minified = 1,
                sort = null,
                desc = 0,
                filter = null,
            )
            val stubs = resp.results.mapNotNull { el ->
                el.toCatalogStubFromMinified(libraryId)
            }
            if (stubs.isNotEmpty()) {
                serverIds += stubs.map { it.libraryItemId }
                if (!mergeWithExisting) {
                    catalogDao.upsertAll(stubs)
                } else {
                    val localById = catalogDao.listByIds(stubs.map { it.libraryItemId })
                        .associateBy { it.libraryItemId }
                    catalogDao.upsertAll(
                        stubs.map { stub ->
                            mergeEnumerateStub(localById[stub.libraryItemId], stub)
                        },
                    )
                }
            }
            val total = resp.total?.toInt() ?: break
            page++
            if (resp.results.isEmpty() || page * PAGE_SIZE >= total) break
        }
        return serverIds.toList()
    }

    private fun mergeEnumerateStub(local: CatalogItemEntity?, stub: CatalogItemEntity): CatalogItemEntity {
        if (local == null) return stub
        if (!local.hydrated) {
            return stub.copy(
                seriesId = local.seriesId ?: stub.seriesId,
                addedAtEpochMs = stub.addedAtEpochMs ?: local.addedAtEpochMs,
                hydrated = false,
                payloadJson = null,
                serverUpdatedAtEpochMs = local.serverUpdatedAtEpochMs,
                localRowUpdatedAtEpochMs = System.currentTimeMillis(),
                isDownloaded = local.isDownloaded,
            )
        }
        if (local.hydrated && stub.serverUpdatedAtEpochMs <= local.serverUpdatedAtEpochMs) {
            return local.copy(
                title = stub.title,
                author = stub.author,
                coverPath = stub.coverPath,
                progress = stub.progress ?: local.progress,
                mediaType = stub.mediaType,
                addedAtEpochMs = stub.addedAtEpochMs ?: local.addedAtEpochMs,
                seriesName = stub.seriesName ?: local.seriesName,
                localRowUpdatedAtEpochMs = System.currentTimeMillis(),
            )
        }
        if (stub.serverUpdatedAtEpochMs > local.serverUpdatedAtEpochMs) {
            return local.copy(
                title = stub.title,
                author = stub.author,
                coverPath = stub.coverPath,
                progress = stub.progress,
                mediaType = stub.mediaType,
                addedAtEpochMs = stub.addedAtEpochMs ?: local.addedAtEpochMs,
                seriesName = stub.seriesName ?: local.seriesName,
                hydrated = false,
                payloadJson = null,
                serverUpdatedAtEpochMs = local.serverUpdatedAtEpochMs,
                localRowUpdatedAtEpochMs = System.currentTimeMillis(),
            )
        }
        return local
    }

    private suspend fun runDelta(libraryId: String): List<String> {
        val serverIds = runEnumerateAndUpsertStubs(libraryId, mergeWithExisting = true)
        val pending = catalogDao.listPendingHydrationIds(libraryId)
        hydrateIds(libraryId, pending)
        return serverIds
    }

    private suspend fun runHydrateAllPending(libraryId: String) {
        val ids = catalogDao.listPendingHydrationIds(libraryId)
        hydrateIds(libraryId, ids)
    }

    private suspend fun hydrateIds(libraryId: String, ids: List<String>) {
        if (ids.isEmpty()) return
        coroutineScope {
            val sem = Semaphore(HYDRATE_PARALLELISM)
            ids.map { id ->
                async {
                    sem.withPermit {
                        runCatching { hydrateOne(libraryId, id) }
                    }
                }
            }.awaitAll()
        }
    }

    private suspend fun hydrateOne(libraryId: String, itemId: String) {
        val dto = api.item(itemId)
        val parsed = dto.toBookDetailParsed(libraryId) ?: return
        val detail = BookDetail(
            id = parsed.id,
            libraryId = parsed.libraryId,
            title = parsed.title,
            author = parsed.author,
            seriesName = parsed.seriesName,
            seriesSequence = parsed.seriesSequence,
            seriesId = parsed.seriesId,
            description = parsed.description,
            coverPath = parsed.coverPath,
            durationSec = parsed.durationSec,
            narrators = parsed.narrators,
            genres = parsed.genres,
            publishedYear = parsed.publishedYear,
            chapters = parsed.chapters.map { BookChapter(it.title, it.startSec, it.endSec) },
            currentTimeSec = parsed.currentTimeSec,
            serverProgressLastUpdate = parsed.serverProgressLastUpdate,
            finishedAt = parsed.finishedAt,
            serverUpdatedAtEpochMs = parsed.serverUpdatedAtEpochMs ?: 0L,
            mediaType = parsed.mediaType,
            episodes = parsed.episodes.map { ep ->
                PodcastEpisode(
                    id = ep.id,
                    libraryItemId = ep.libraryItemId,
                    index = ep.index,
                    season = ep.season,
                    episode = ep.episode,
                    title = ep.title,
                    subtitle = ep.subtitle,
                    description = ep.description,
                    pubDate = ep.pubDate,
                    publishedAt = ep.publishedAt,
                    addedAt = ep.addedAt,
                    updatedAt = ep.updatedAt,
                    durationSec = ep.durationSec,
                    currentTimeSec = ep.currentTimeSec,
                    serverProgressLastUpdate = ep.serverProgressLastUpdate,
                    finishedAt = ep.finishedAt,
                )
            },
        )
        val payload = json.encodeToString(BookDetailCacheDto.serializer(), detail.toCacheDto())
        val existing = catalogDao.getById(itemId)
        val serverUpdated = parsed.serverUpdatedAtEpochMs ?: existing?.serverUpdatedAtEpochMs ?: 0L

        val shouldForceCoverRefresh = existing != null &&
                parsed.serverUpdatedAtEpochMs != null &&
                parsed.serverUpdatedAtEpochMs > existing.serverUpdatedAtEpochMs

        catalogDao.upsertItem(
            CatalogItemEntity(
                libraryItemId = itemId,
                libraryId = detail.libraryId,
                serverUpdatedAtEpochMs = serverUpdated,
                title = detail.title,
                author = detail.author,
                seriesName = detail.seriesName,
                seriesId = detail.seriesId,
                addedAtEpochMs = existing?.addedAtEpochMs,
                coverPath = detail.coverPath,
                progress = existing?.progress,
                mediaType = detail.mediaType,
                hydrated = true,
                payloadJson = payload,
                localRowUpdatedAtEpochMs = System.currentTimeMillis(),
                isDownloaded = existing?.isDownloaded == true,
            ),
        )

        try {
            coverCacheRepository.prefetchCover(itemId, force = shouldForceCoverRefresh)
        } catch (_: Exception) {
        }
    }

    private suspend fun runCoverSweep(libraryId: String) {
        val ids = catalogDao.listAllIdsForLibrary(libraryId)
        coverCacheRepository.prefetchCovers(ids)
    }
}

private fun JsonElement.toCatalogStubFromMinified(defaultLibraryId: String): CatalogItemEntity? {
    val obj = this as? JsonObject ?: return null
    val id = obj["id"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
    val mediaType = obj["mediaType"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: "book"
    if (!mediaType.equals("book", ignoreCase = true) && !mediaType.equals("podcast", ignoreCase = true)) return null
    val libraryId = obj["libraryId"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: defaultLibraryId
    val media = obj["media"]?.jsonObject ?: JsonObject(emptyMap())
    val metadata = media["metadata"]?.jsonObject ?: JsonObject(emptyMap())
    val title = metadata["title"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: "Unknown"
    val author = if (mediaType.equals("podcast", ignoreCase = true)) {
        metadata["author"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
    } else {
        metadata["authorName"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
    }
    val seriesName = metadata["seriesName"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
    val seriesId = metadata["series"]?.let { runCatching { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull }.getOrNull() }
    val addedAt = obj["addedAt"]?.jsonPrimitive?.longOrNull
    val updatedAt = obj["updatedAt"]?.jsonPrimitive?.longOrNull ?: 0L
    val progress = obj["userMediaProgress"]?.jsonObject?.let { progressObj ->
        val current = progressObj["currentTime"]?.jsonPrimitive?.floatOrNull ?: 0f
        val duration = progressObj["duration"]?.jsonPrimitive?.floatOrNull ?: 0f
        if (duration > 0f) (current / duration).coerceIn(0f, 1f) else null
    }
    return CatalogItemEntity(
        libraryItemId = id,
        libraryId = libraryId,
        serverUpdatedAtEpochMs = updatedAt,
        title = title,
        author = author,
        seriesName = seriesName,
        seriesId = seriesId,
        addedAtEpochMs = addedAt,
        coverPath = media["coverPath"]?.jsonPrimitive?.contentOrNull,
        progress = progress,
        mediaType = mediaType,
        hydrated = false,
        payloadJson = null,
        localRowUpdatedAtEpochMs = System.currentTimeMillis(),
        isDownloaded = false,
    )
}
