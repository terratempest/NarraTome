package com.narratome.data.repository

import com.narratome.data.local.db.BrowseListCacheKind
import com.narratome.data.local.preferences.AppPreferencesRepository
import com.narratome.data.local.db.LibraryBrowseListCacheDao
import com.narratome.data.local.db.LibraryBrowseListCacheEntity
import com.narratome.data.local.dto.AuthorSummaryCacheDto
import com.narratome.data.local.dto.CollectionSummaryCacheDto
import com.narratome.data.local.dto.LibraryItemSummaryCacheDto
import com.narratome.data.local.dto.SeriesSummaryCacheDto
import com.narratome.data.local.dto.toCacheDto
import com.narratome.data.local.dto.toDomain
import com.narratome.data.remote.AudiobookshelfApi
import com.narratome.data.remote.ServerBaseUrlResolver
import com.narratome.data.remote.dto.LibraryDto
import com.narratome.data.remote.dto.toBookSummaryOrNull
import com.narratome.domain.model.AuthorSummary
import com.narratome.domain.model.CollectionSummary
import com.narratome.domain.model.LibraryItemSummary
import com.narratome.domain.model.SeriesSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepository @Inject constructor(
    private val api: AudiobookshelfApi,
    private val browseListCacheDao: LibraryBrowseListCacheDao,
    private val baseUrlResolver: ServerBaseUrlResolver,
    private val json: Json,
    private val preferences: AppPreferencesRepository,
) {

    fun getCurrentBaseUrl(): String? = baseUrlResolver.resolveBlocking()?.trimEnd('/')

    suspend fun bookLibraries(): Result<List<LibraryDto>> = withContext(Dispatchers.IO) {
        runCatching {
            val libraries = api.libraries().libraries.filter { lib ->
                lib.mediaType.equals("book", ignoreCase = true) || lib.mediaType.equals("podcast", ignoreCase = true)
            }
            preferences.cacheLibraries(libraries)
            libraries
        }
    }

    suspend fun getLibrarySeriesDetail(libraryId: String, seriesId: String): Result<SeriesSummary> =
        withContext(Dispatchers.IO) {
            runCatching {
                val d = api.librarySeriesDetail(libraryId, seriesId)
                SeriesSummary(
                    id = d.id.ifBlank { seriesId },
                    name = d.name?.takeIf { it.isNotBlank() } ?: "Series",
                    bookCount = 0,
                )
            }
        }

    suspend fun getSeries(libraryId: String): Result<List<SeriesSummary>> = withContext(Dispatchers.IO) {
        try {
            val body = api.librarySeries(libraryId)
            val results = body.items()
            val list = results.map { el ->
                val obj = el.jsonObject
                SeriesSummary(
                    id = obj["id"]?.jsonPrimitive?.content ?: "",
                    name = obj["name"]?.jsonPrimitive?.content ?: "Unknown Series",
                    bookCount = obj["books"]?.jsonArray?.size
                        ?: obj["numBooks"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                )
            }
            upsertSeriesCache(libraryId, list)
            Result.success(list)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    suspend fun getCollections(libraryId: String): Result<List<CollectionSummary>> = withContext(Dispatchers.IO) {
        try {
            val body = api.libraryCollections(libraryId)
            val results = body.items()
            val list = results.map { el ->
                val obj = el.jsonObject
                CollectionSummary(
                    id = obj["id"]?.jsonPrimitive?.content ?: "",
                    name = obj["name"]?.jsonPrimitive?.content ?: "Unknown Collection",
                    bookCount = obj["books"]?.jsonArray?.size
                        ?: obj["numBooks"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    description = obj["description"]?.jsonPrimitive?.contentOrNull
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) },
                    coverItemIds = obj["books"]?.jsonArray
                        ?.mapNotNull { book -> book.jsonObject["id"]?.jsonPrimitive?.contentOrNull }
                        ?.distinct()
                        ?.take(5)
                        .orEmpty(),
                )
            }
            upsertCollectionsCache(libraryId, list)
            Result.success(list)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    suspend fun getCollectionBooks(
        collectionId: String,
        fallbackLibraryId: String,
    ): Result<Pair<String, List<LibraryItemSummary>>> = withContext(Dispatchers.IO) {
        runCatching {
            val obj = api.collection(collectionId)
            val title = obj["name"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() }
                ?: "Collection"
            val books = obj["books"]?.jsonArray
                ?.mapNotNull { it.toBookSummaryOrNull(fallbackLibraryId)?.toSummary() }
                ?: emptyList()
            title to books
        }
    }

    suspend fun getAuthors(libraryId: String): Result<List<AuthorSummary>> = withContext(Dispatchers.IO) {
        try {
            val body = api.libraryAuthors(libraryId)
            val results = body.items()
            val list = results.map { el ->
                val obj = el.jsonObject
                AuthorSummary(
                    id = obj["id"]?.jsonPrimitive?.content ?: "",
                    name = obj["name"]?.jsonPrimitive?.content ?: "Unknown Author",
                    bookCount = obj["numBooks"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    description = obj["description"]?.jsonPrimitive?.contentOrNull
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) },
                )
            }
            upsertAuthorsCache(libraryId, list)
            Result.success(list)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    suspend fun searchBooks(libraryId: String, query: String): Result<List<LibraryItemSummary>> =
        withContext(Dispatchers.IO) {
            try {
                val dto = api.librarySearch(libraryId, query)
                val list = extractBookResults(dto.asRootJsonObject(), libraryId)
                upsertSearchCache(libraryId, query.trim(), list)
                Result.success(list)
            } catch (e: Throwable) {
                Result.failure(e)
            }
        }

    suspend fun peekCachedAuthors(libraryId: String): List<AuthorSummary>? = withContext(Dispatchers.IO) {
        decodeAuthorList(browseListCacheDao.getRow(libraryId, BrowseListCacheKind.AUTHORS, "")?.payloadJson)
    }

    suspend fun peekCachedSeries(libraryId: String): List<SeriesSummary>? = withContext(Dispatchers.IO) {
        decodeSeriesList(browseListCacheDao.getRow(libraryId, BrowseListCacheKind.SERIES, "")?.payloadJson)
    }

    suspend fun peekCachedCollections(libraryId: String): List<CollectionSummary>? = withContext(Dispatchers.IO) {
        decodeCollectionList(browseListCacheDao.getRow(libraryId, BrowseListCacheKind.COLLECTIONS, "")?.payloadJson)
    }

    suspend fun peekCachedSearchResults(libraryId: String, query: String): List<LibraryItemSummary>? =
        withContext(Dispatchers.IO) {
            val q = query.trim()
            if (q.isEmpty()) return@withContext null
            decodeSearchList(browseListCacheDao.getRow(libraryId, BrowseListCacheKind.SEARCH, q)?.payloadJson)
        }

    private suspend fun upsertAuthorsCache(libraryId: String, list: List<AuthorSummary>) {
        val payload = json.encodeToString(
            ListSerializer(AuthorSummaryCacheDto.serializer()),
            list.map { it.toCacheDto() },
        )
        browseListCacheDao.upsert(
            LibraryBrowseListCacheEntity(
                libraryId = libraryId,
                kind = BrowseListCacheKind.AUTHORS,
                query = "",
                payloadJson = payload,
                updatedAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun upsertSeriesCache(libraryId: String, list: List<SeriesSummary>) {
        val payload = json.encodeToString(
            ListSerializer(SeriesSummaryCacheDto.serializer()),
            list.map { it.toCacheDto() },
        )
        browseListCacheDao.upsert(
            LibraryBrowseListCacheEntity(
                libraryId = libraryId,
                kind = BrowseListCacheKind.SERIES,
                query = "",
                payloadJson = payload,
                updatedAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun upsertCollectionsCache(libraryId: String, list: List<CollectionSummary>) {
        val payload = json.encodeToString(
            ListSerializer(CollectionSummaryCacheDto.serializer()),
            list.map { it.toCacheDto() },
        )
        browseListCacheDao.upsert(
            LibraryBrowseListCacheEntity(
                libraryId = libraryId,
                kind = BrowseListCacheKind.COLLECTIONS,
                query = "",
                payloadJson = payload,
                updatedAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun upsertSearchCache(libraryId: String, query: String, list: List<LibraryItemSummary>) {
        val q = query.trim()
        if (q.isEmpty()) return
        val payload = json.encodeToString(
            ListSerializer(LibraryItemSummaryCacheDto.serializer()),
            list.map { it.toCacheDto() },
        )
        browseListCacheDao.upsert(
            LibraryBrowseListCacheEntity(
                libraryId = libraryId,
                kind = BrowseListCacheKind.SEARCH,
                query = q,
                payloadJson = payload,
                updatedAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    private fun decodeAuthorList(payload: String?): List<AuthorSummary>? {
        if (payload.isNullOrBlank()) return null
        return runCatching {
            json.decodeFromString(ListSerializer(AuthorSummaryCacheDto.serializer()), payload).map { it.toDomain() }
        }.getOrNull()
    }

    private fun decodeSeriesList(payload: String?): List<SeriesSummary>? {
        if (payload.isNullOrBlank()) return null
        return runCatching {
            json.decodeFromString(ListSerializer(SeriesSummaryCacheDto.serializer()), payload).map { it.toDomain() }
        }.getOrNull()
    }

    private fun decodeCollectionList(payload: String?): List<CollectionSummary>? {
        if (payload.isNullOrBlank()) return null
        return runCatching {
            json.decodeFromString(ListSerializer(CollectionSummaryCacheDto.serializer()), payload).map { it.toDomain() }
        }.getOrNull()
    }

    private fun decodeSearchList(payload: String?): List<LibraryItemSummary>? {
        if (payload.isNullOrBlank()) return null
        return runCatching {
            json.decodeFromString(ListSerializer(LibraryItemSummaryCacheDto.serializer()), payload).map { it.toDomain() }
        }.getOrNull()
    }

    private fun extractBookResults(root: JsonElement, libraryId: String): List<LibraryItemSummary> {
        val out = mutableListOf<LibraryItemSummary>()
        fun visit(node: JsonElement) {
            when (node) {
                is JsonObject -> {
                    val mt = node["mediaType"]?.jsonPrimitive?.content
                    if (mt.equals("book", ignoreCase = true) || mt.equals("podcast", ignoreCase = true)) {
                        val parsed = node.toBookSummaryOrNull(libraryId)
                        if (parsed != null) {
                            out += LibraryItemSummary(
                                id = parsed.id,
                                libraryId = parsed.libraryId,
                                title = parsed.title,
                                author = parsed.author,
                                mediaType = parsed.mediaType,
                                coverPath = parsed.coverPath,
                            )
                        }
                    }
                    for ((_, v) in node) {
                        visit(v)
                    }
                }
                is JsonArray -> node.forEach { visit(it) }
                else -> Unit
            }
        }
        visit(root)
        return out.distinctBy { it.id }
    }

    private fun com.narratome.data.remote.dto.BookItemParsed.toSummary(): LibraryItemSummary =
        LibraryItemSummary(
            id = id,
            libraryId = libraryId,
            title = title,
            author = author,
            mediaType = mediaType,
            coverPath = coverPath,
            progress = progress,
        )
}
