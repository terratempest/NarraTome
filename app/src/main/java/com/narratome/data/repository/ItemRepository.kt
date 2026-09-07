package com.narratome.data.repository

import android.content.Context
import androidx.paging.PagingSource
import androidx.sqlite.db.SimpleSQLiteQuery
import com.narratome.data.local.db.CatalogDao
import com.narratome.data.local.db.CatalogItemEntity
import com.narratome.data.local.db.CatalogSyncStateEntity
import com.narratome.data.local.db.LocalDownloadDao
import com.narratome.data.local.dto.BookDetailCacheDto
import com.narratome.data.local.dto.decodeBookDetailFromCatalogPayload
import com.narratome.data.local.dto.toCacheDto
import com.narratome.data.remote.AudiobookshelfApi
import com.narratome.data.remote.PlayResponseParser
import com.narratome.data.remote.ServerBaseUrlResolver
import com.narratome.data.remote.dto.toBookDetailParsed
import com.narratome.domain.model.AuthorSummary
import com.narratome.domain.model.BookChapter
import com.narratome.domain.model.BookDetail
import com.narratome.domain.model.LibraryItemSummary
import com.narratome.domain.model.PlayableTrack
import com.narratome.domain.model.PodcastEpisode
import com.narratome.domain.model.SeriesSummary
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

fun CatalogItemEntity.toLibraryItemSummary(): LibraryItemSummary =
    LibraryItemSummary(
        id = libraryItemId,
        libraryId = libraryId,
        title = title,
        author = author,
        mediaType = mediaType,
        coverPath = coverPath,
        progress = progress,
    )

@Singleton
class ItemRepository @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val api: AudiobookshelfApi,
    private val baseUrlResolver: ServerBaseUrlResolver,
    private val localDownloadDao: LocalDownloadDao,
    private val catalogDao: CatalogDao,
    private val coverCacheRepository: CoverCacheRepository,
    private val json: Json,
) {

    fun observeItems(libraryId: String): Flow<List<CatalogItemEntity>> = catalogDao.observeItems(libraryId)

    fun observeItemEntity(itemId: String): Flow<CatalogItemEntity?> = catalogDao.observeById(itemId)

    fun observeBookDetail(itemId: String): Flow<BookDetail?> =
        catalogDao.observeById(itemId).map { entity ->
            decodeBookDetailFromCatalogPayload(entity?.payloadJson, json)
        }

    fun observeItemsInSeries(libraryId: String, seriesId: String, downloadedOnly: Boolean = false): Flow<List<CatalogItemEntity>> =
        catalogDao.observeItemsInSeries(libraryId, seriesId, downloadedOnly)

    fun observeItemsByAuthor(libraryId: String, author: String, downloadedOnly: Boolean = false): Flow<List<CatalogItemEntity>> =
        catalogDao.observeItemsByAuthor(libraryId, author, downloadedOnly)

    fun observeSyncState(libraryId: String): Flow<CatalogSyncStateEntity?> = catalogDao.observeSyncState(libraryId)

    fun pagingSource(
        libraryId: String,
        downloadedOnly: Boolean = false,
        inProgressOnly: Boolean = false,
        sortKey: String? = "TITLE",
        sortReversed: Boolean = false,
    ): PagingSource<Int, CatalogItemEntity> {
        val where = buildList {
            add("c.libraryId = ?")
            if (downloadedOnly) add("c.isDownloaded = 1")
            if (inProgressOnly) {
                add("mp.finishedAt IS NULL")
                add(
                    """
                    (
                        CASE
                            WHEN mp.durationSec > 0 THEN mp.currentTimeSec / mp.durationSec
                            ELSE ifnull(c.progress, 0)
                        END
                    ) > 0.0001
                    """.trimIndent(),
                )
                add(
                    """
                    (
                        CASE
                            WHEN mp.durationSec > 0 THEN mp.currentTimeSec / mp.durationSec
                            ELSE ifnull(c.progress, 0)
                        END
                    ) < 0.999
                    """.trimIndent(),
                )
            }
        }.joinToString(separator = " AND ")
        val direction = if (sortReversed) "DESC" else "ASC"
        val orderBy = when (sortKey) {
            null -> ""
            "RECENTLY_ADDED" -> "ORDER BY c.addedAtEpochMs ${if (sortReversed) "ASC" else "DESC"}, c.title COLLATE NOCASE $direction, c.libraryItemId $direction"
            "AUTHOR" -> "ORDER BY ifnull(c.author, '') COLLATE NOCASE $direction, c.title COLLATE NOCASE $direction, c.libraryItemId $direction"
            else -> "ORDER BY c.title COLLATE NOCASE $direction, c.libraryItemId $direction"
        }
        val sql = """
            SELECT c.*
            FROM catalog_items c
            LEFT JOIN media_progress mp ON mp.libraryItemId = c.libraryItemId AND mp.episodeId IS NULL
            WHERE $where
            $orderBy
        """.trimIndent()
        return catalogDao.pagingSource(SimpleSQLiteQuery(sql, arrayOf(libraryId)))
    }

    suspend fun getById(id: String): CatalogItemEntity? = catalogDao.getById(id)

    suspend fun getCachedBookDetailOrNull(itemId: String): BookDetail? = withContext(Dispatchers.IO) {
        decodeBookDetailFromCatalogPayload(catalogDao.getById(itemId)?.payloadJson, json)
    }

    suspend fun firstLibraryIdOrNull(): String? = catalogDao.firstLibraryIdOrNull()

    suspend fun listDownloadedLibraryIds(): List<String> = catalogDao.listDownloadedLibraryIds()

    fun observeRecentByAdded(libraryId: String, limit: Int, downloadedOnly: Boolean = false): Flow<List<CatalogItemEntity>> =
        catalogDao.observeRecentByAdded(libraryId, limit, downloadedOnly)

    suspend fun listRecentByAdded(libraryId: String, limit: Int, downloadedOnly: Boolean = false): List<CatalogItemEntity> =
        catalogDao.listRecentByAdded(libraryId, limit, downloadedOnly)

    suspend fun getSeriesSummary(libraryId: String, downloadedOnly: Boolean = false): List<SeriesSummary> =
        catalogDao.getSeriesSummary(libraryId, "", downloadedOnly)

    suspend fun getSeriesCoverItemIds(
        libraryId: String,
        downloadedOnly: Boolean = false,
        limitPerSeries: Int = 5,
    ): Map<String, List<String>> = withContext(Dispatchers.IO) {
        catalogDao.listSeriesCoverCandidateIds(libraryId, downloadedOnly)
            .groupBy { it.groupId }
            .mapValues { (_, rows) -> rows.map { it.libraryItemId }.distinct().take(limitPerSeries) }
    }

    suspend fun getAuthorCoverItemIds(
        libraryId: String,
        downloadedOnly: Boolean = false,
        limitPerAuthor: Int = 5,
    ): Map<String, List<String>> = withContext(Dispatchers.IO) {
        catalogDao.listAuthorCoverCandidateIds(libraryId, downloadedOnly)
            .groupBy { it.groupId }
            .mapValues { (_, rows) -> rows.map { it.libraryItemId }.distinct().take(limitPerAuthor) }
    }

    suspend fun getBooksBySeries(libraryId: String, seriesId: String, downloadedOnly: Boolean = false): List<LibraryItemSummary> =
        catalogDao.getBookSeriesDetails(libraryId, seriesId, downloadedOnly)

    suspend fun searchLocalSubstring(
        libraryId: String,
        query: String,
        limit: Int = 80,
        downloadedOnly: Boolean = false,
    ): List<LibraryItemSummary> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return withContext(Dispatchers.IO) {
            catalogDao.searchLocalSubstring(libraryId, q, limit, downloadedOnly).map { it.toLibraryItemSummary() }
        }
    }

    suspend fun listDownloadedAuthors(libraryId: String): List<AuthorSummary> = withContext(Dispatchers.IO) {
        catalogDao.listDownloadedAuthorCounts(libraryId).map { row ->
            AuthorSummary(
                id = row.author,
                name = row.author,
                description = null,
                bookCount = row.bookCount,
            )
        }
    }

    suspend fun ensureItemHydrated(itemId: String, libraryId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val row = catalogDao.getById(itemId)
        if (row?.hydrated == true && !row.payloadJson.isNullOrBlank()) {
            return@withContext Result.success(Unit)
        }
        refreshItemFromServer(itemId, libraryId).map { }
    }

    suspend fun refreshItemFromServer(itemId: String, libraryId: String): Result<BookDetail> = withContext(Dispatchers.IO) {
        runCatching {
            val dto = api.item(itemId)
            val parsed = dto.toBookDetailParsed(libraryId) ?: error("Unsupported item or parse failed")
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
            val serverUpdated = parsed.serverUpdatedAtEpochMs ?: existing?.serverUpdatedAtEpochMs ?: System.currentTimeMillis()

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
            detail
        }
    }

    suspend fun getBookDetail(itemId: String, libraryId: String): Result<BookDetail> = withContext(Dispatchers.IO) {
        refreshItemFromServer(itemId, libraryId).recoverCatching {
            getCachedBookDetailOrNull(itemId) ?: throw it
        }
    }

    suspend fun resolvePlayableUrls(
        itemId: String,
        allowRemoteFallback: Boolean = true,
    ): Result<List<PlayableTrack>> = resolvePlayableUrls(
        itemId = itemId,
        episodeId = null,
        allowRemoteFallback = allowRemoteFallback,
    )

    suspend fun resolvePlayableUrls(
        itemId: String,
        episodeId: String?,
        allowRemoteFallback: Boolean = true,
    ): Result<List<PlayableTrack>> = withContext(Dispatchers.IO) {
        runCatching {
            listLocalPlayableTracksIfComplete(itemId, episodeId)?.let { return@runCatching it }
            check(allowRemoteFallback) { "Item is not available offline" }
            fetchRemotePlayUrls(itemId, episodeId).getOrThrow()
        }
    }

    private suspend fun fetchRemotePlayUrls(itemId: String, episodeId: String?): Result<List<PlayableTrack>> = withContext(Dispatchers.IO) {
        runCatching {
            val playBody =
                """{"forceDirectPlay":true,"forceTranscode":false,"mediaPlayer":"android","deviceInfo":{"clientName":"NarraTome","clientVersion":"1.0"}}"""
            val body = playBody.toRequestBody("application/json".toMediaType())
            val play = if (episodeId.isNullOrBlank()) {
                api.playItem(itemId, body)
            } else {
                api.playPodcastEpisode(itemId, episodeId, body)
            }
            val base = baseUrlResolver.resolveBlocking()
            val tracks = PlayResponseParser.extractAllPlayableUrls(play, base)
            if (tracks.isEmpty()) error("No playable tracks in play response")
            tracks
        }
    }

    private suspend fun listLocalPlayableTracksIfComplete(itemId: String, episodeId: String?): List<PlayableTrack>? {
        val cleanEpisodeId = episodeId?.takeIf { it.isNotBlank() }
        val downloadKey = mediaProgressKey(itemId, cleanEpisodeId)
        val manifest = localDownloadDao.getManifest(downloadKey) ?: return null
        val parts = localDownloadDao.listParts(downloadKey)
        if (parts.size != manifest.expectedPartCount) return null
        val dir = itemDownloadDir(itemId, cleanEpisodeId)
        val out = ArrayList<PlayableTrack>()
        for (p in parts) {
            val f = File(dir, p.fileName)
            if (!f.isFile || f.length() == 0L) return null
            out.add(PlayableTrack(f.toURI().toString(), null))
        }
        return out
    }

    private fun itemDownloadDir(libraryItemId: String, episodeId: String?): File {
        val root = File(appContext.filesDir, "downloads")
        return if (episodeId.isNullOrBlank()) {
            File(root, "items/${safeDiskSegment(libraryItemId)}")
        } else {
            File(File(root, "podcast_episodes/${safeDiskSegment(libraryItemId)}"), safeDiskSegment(episodeId))
        }
    }

    private fun safeDiskSegment(value: String): String =
        value.replace(Regex("[^a-zA-Z0-9._-]+"), "_").trim('_').ifBlank { "item" }
}
