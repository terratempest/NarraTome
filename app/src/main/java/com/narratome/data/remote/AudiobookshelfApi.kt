package com.narratome.data.remote

import com.narratome.data.remote.dto.LibrariesResponseDto
import com.narratome.data.remote.dto.GenericLibraryBrowseResponseDto
import com.narratome.data.remote.dto.LibraryItemsResponseDto
import com.narratome.data.remote.dto.LibrarySearchResponseDto
import com.narratome.data.remote.dto.LibrarySeriesDetailDto
import com.narratome.data.remote.dto.LoginRequest
import com.narratome.data.remote.dto.LoginResponseDto
import com.narratome.data.remote.dto.AudioBookmarkDto
import com.narratome.data.remote.dto.BookmarkUpsertRequestDto
import com.narratome.data.remote.dto.MediaProgressPatchDto
import com.narratome.data.remote.dto.MeDto
import com.narratome.data.remote.dto.ExpandedLibraryItemDto
import com.narratome.data.remote.dto.MeItemsInProgressResponseDto
import com.narratome.data.remote.dto.PingResponseDto
import com.narratome.data.remote.dto.PlayItemResponseDto
import com.narratome.data.remote.dto.StatusResponseDto
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.DELETE
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import kotlinx.serialization.json.JsonObject

interface AudiobookshelfApi {

    @GET("ping")
    suspend fun ping(@retrofit2.http.Tag endpoint: okhttp3.HttpUrl? = null): PingResponseDto

    @GET("status")
    suspend fun status(): StatusResponseDto

    @POST("login")
    suspend fun login(@Body body: LoginRequest): LoginResponseDto

    @POST("api/authorize")
    suspend fun authorize(): LoginResponseDto

    @GET("api/me")
    suspend fun me(): MeDto

    @GET("api/libraries")
    suspend fun libraries(): LibrariesResponseDto

    @GET("api/libraries/{libraryId}/series")
    suspend fun librarySeries(
        @Path("libraryId") libraryId: String,
        @Query("limit") limit: Int = 100,
        @Query("page") page: Int = 0,
    ): GenericLibraryBrowseResponseDto

    @GET("api/libraries/{libraryId}/series/{seriesId}")
    suspend fun librarySeriesDetail(
        @Path("libraryId") libraryId: String,
        @Path("seriesId") seriesId: String,
    ): LibrarySeriesDetailDto

    @GET("api/libraries/{libraryId}/collections")
    suspend fun libraryCollections(
        @Path("libraryId") libraryId: String,
        @Query("limit") limit: Int = 100,
        @Query("page") page: Int = 0,
    ): GenericLibraryBrowseResponseDto

    @GET("api/collections/{collectionId}")
    suspend fun collection(
        @Path("collectionId") collectionId: String,
    ): JsonObject

    @GET("api/libraries/{libraryId}/authors")
    suspend fun libraryAuthors(
        @Path("libraryId") libraryId: String,
        @Query("limit") limit: Int = 100,
        @Query("page") page: Int = 0,
    ): GenericLibraryBrowseResponseDto

    @GET("api/libraries/{libraryId}/items")
    suspend fun libraryItems(
        @Path("libraryId") libraryId: String,
        @Query("limit") limit: Int = 50,
        @Query("page") page: Int = 0,
        @Query("minified") minified: Int = 1,
        @Query("sort") sort: String? = null,
        @Query("desc") desc: Int? = null,
        @Query("filter") filter: String? = null,
    ): LibraryItemsResponseDto

    @GET("api/me/items-in-progress")
    suspend fun meItemsInProgress(
        @Query("limit") limit: Int = 25,
    ): MeItemsInProgressResponseDto

    @GET("api/libraries/{libraryId}/search")
    suspend fun librarySearch(
        @Path("libraryId") libraryId: String,
        @Query("q") query: String,
        @Query("limit") limit: Int = 25,
    ): LibrarySearchResponseDto

    @GET("api/items/{itemId}")
    suspend fun item(
        @Path("itemId") itemId: String,
        @Query("expanded") expanded: Int = 1,
        @Query("include") include: String = "progress",
    ): ExpandedLibraryItemDto

    @GET("api/items/{itemId}/cover")
    suspend fun itemCover(
        @Path("itemId") itemId: String,
        @Query("token") token: String? = null,
        @Query("width") width: Int? = null,
        @Query("height") height: Int? = null,
    ): Response<ResponseBody>

    @PATCH("api/me/progress/{libraryItemId}")
    suspend fun patchProgress(
        @Path("libraryItemId") libraryItemId: String,
        @Body body: MediaProgressPatchDto,
    ): Response<Unit>

    @PATCH("api/me/progress/{libraryItemId}/{episodeId}")
    suspend fun patchEpisodeProgress(
        @Path("libraryItemId") libraryItemId: String,
        @Path("episodeId") episodeId: String,
        @Body body: MediaProgressPatchDto,
    ): Response<Unit>

    @POST("api/items/{itemId}/play")
    suspend fun playItem(
        @Path("itemId") itemId: String,
        @Body body: RequestBody,
    ): PlayItemResponseDto

    @POST("api/items/{itemId}/play/{episodeId}")
    suspend fun playPodcastEpisode(
        @Path("itemId") itemId: String,
        @Path("episodeId") episodeId: String,
        @Body body: RequestBody,
    ): PlayItemResponseDto

    @POST("api/me/item/{itemId}/bookmark")
    suspend fun createBookmark(
        @Path("itemId") itemId: String,
        @Body body: BookmarkUpsertRequestDto,
    ): AudioBookmarkDto

    @PATCH("api/me/item/{itemId}/bookmark")
    suspend fun updateBookmark(
        @Path("itemId") itemId: String,
        @Body body: BookmarkUpsertRequestDto,
    ): AudioBookmarkDto

    @DELETE("api/me/item/{itemId}/bookmark/{timeSeconds}")
    suspend fun deleteBookmark(
        @Path("itemId") itemId: String,
        @Path("timeSeconds") timeSeconds: Int,
    ): Response<Unit>
}
