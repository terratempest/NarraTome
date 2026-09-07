package com.narratome.data.remote

import com.narratome.data.remote.dto.PlayItemResponseDto
import com.narratome.domain.model.PlayableTrack
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object PlayResponseParser {

    data class TrackDownloadInfo(
        val url: String,
        val expectedBytes: Long?,
        val sha256: String?,
    )

    private data class PlayTracks(val sessionId: String?, val tracks: JsonArray)

    /**
     * A valid direct-play URL looks like /api/items/{id}/file/{ino}.
     * We reject anything that ends with a JS-undefined or literal-null segment,
     * which would happen when the server has not scanned the file's inode yet.
     * HLS paths (/hls/...) are also excluded — they are handled separately.
     */
    private fun isValidDirectUrl(url: String): Boolean {
        if (url.startsWith("/hls")) return false
        if (url.endsWith("/undefined") || url.endsWith("/null")) return false
        return true
    }

    private fun tracksFromPlayRoot(root: JsonObject): PlayTracks? {
        val sessionId = root["id"]?.jsonPrimitive?.content
        val tracks = root["audioTracks"]?.jsonArray
            ?: root["libraryItem"]?.jsonObject?.get("media")?.jsonObject?.get("tracks")?.jsonArray
            ?: return null
        return PlayTracks(sessionId, tracks)
    }

    private fun tracksFromDto(dto: PlayItemResponseDto): PlayTracks? {
        val tracks = dto.audioTracks
            ?: dto.libraryItem?.get("media")?.jsonObject?.get("tracks")?.jsonArray
            ?: return null
        return PlayTracks(dto.id, tracks)
    }

    fun extractTrackDownloadInfos(json: JsonElement, baseUrl: String?): List<TrackDownloadInfo> {
        val root = json as? JsonObject ?: return emptyList()
        val pt = tracksFromPlayRoot(root) ?: return emptyList()
        return extractTrackDownloadInfos(pt.sessionId, pt.tracks, baseUrl)
    }

    fun extractTrackDownloadInfos(response: PlayItemResponseDto, baseUrl: String?): List<TrackDownloadInfo> {
        val pt = tracksFromDto(response) ?: return emptyList()
        return extractTrackDownloadInfos(pt.sessionId, pt.tracks, baseUrl)
    }

    private fun extractTrackDownloadInfos(sessionId: String?, tracks: JsonArray, baseUrl: String?): List<TrackDownloadInfo> {
        val out = ArrayList<TrackDownloadInfo>()
        for (el in tracks) {
            val o = el as? JsonObject ?: continue
            val contentUrl = o["contentUrl"]?.jsonPrimitive?.content

            val url = when {
                contentUrl != null && contentUrl.startsWith("/hls") ->
                    resolveAgainstBase(contentUrl, baseUrl)
                contentUrl != null && isValidDirectUrl(contentUrl) ->
                    resolveAgainstBase(contentUrl, baseUrl)
                sessionId != null -> {
                    val index = o["index"]?.jsonPrimitive?.content
                        ?.takeIf { it != "null" }?.toIntOrNull() ?: 1
                    resolveAgainstBase("/public/session/$sessionId/track/$index", baseUrl)
                }
                contentUrl != null -> resolveAgainstBase(contentUrl, baseUrl)
                else -> continue
            }

            val size = o["size"]?.jsonPrimitive?.content?.toLongOrNull()
                ?: o["metadata"]?.jsonObject?.get("size")?.jsonPrimitive?.content?.toLongOrNull()
            val hash = o["sha256"]?.jsonPrimitive?.content
                ?: o["metadata"]?.jsonObject?.get("hash")?.jsonPrimitive?.content
            out.add(TrackDownloadInfo(url = url, expectedBytes = size, sha256 = hash))
        }
        return out
    }

    fun extractAllPlayableUrls(json: JsonElement, baseUrl: String?): List<PlayableTrack> {
        val root = json as? JsonObject ?: return emptyList()
        val pt = tracksFromPlayRoot(root) ?: return emptyList()
        return extractAllPlayableTracks(pt.sessionId, pt.tracks, baseUrl)
    }

    fun extractAllPlayableUrls(response: PlayItemResponseDto, baseUrl: String?): List<PlayableTrack> {
        val pt = tracksFromDto(response) ?: return emptyList()
        return extractAllPlayableTracks(pt.sessionId, pt.tracks, baseUrl)
    }

    private fun extractAllPlayableTracks(sessionId: String?, tracks: JsonArray, baseUrl: String?): List<PlayableTrack> {
        val out = ArrayList<PlayableTrack>()
        for (el in tracks) {
            val o = el as? JsonObject ?: continue
            val contentUrl = o["contentUrl"]?.jsonPrimitive?.content
            val dur = o["duration"]?.jsonPrimitive?.content?.toDoubleOrNull()
                ?: o["metadata"]?.jsonObject?.get("duration")?.jsonPrimitive?.content?.toDoubleOrNull()

            val url = when {
                contentUrl != null && contentUrl.startsWith("/hls") ->
                    resolveAgainstBase(contentUrl, baseUrl)
                sessionId != null -> {
                    val index = o["index"]?.jsonPrimitive?.content
                        ?.takeIf { it != "null" }?.toIntOrNull() ?: 1
                    resolveAgainstBase("/public/session/$sessionId/track/$index", baseUrl)
                }
                contentUrl != null && isValidDirectUrl(contentUrl) ->
                    resolveAgainstBase(contentUrl, baseUrl)
                contentUrl != null -> resolveAgainstBase(contentUrl, baseUrl)
                else -> continue
            }
            out.add(PlayableTrack(url, dur))
        }
        return out
    }

    fun extractFirstPlayableUrl(json: JsonElement, baseUrl: String?): String? =
        extractAllPlayableUrls(json, baseUrl).firstOrNull()?.url

    private fun resolveAgainstBase(pathOrUrl: String, baseUrl: String?): String {
        val resolved = if (pathOrUrl.startsWith("http://", ignoreCase = true) ||
            pathOrUrl.startsWith("https://", ignoreCase = true)
        ) {
            pathOrUrl
        } else {
            val base = baseUrl?.trimEnd('/') ?: return pathOrUrl
            if (pathOrUrl.startsWith("/")) base + pathOrUrl else "$base/$pathOrUrl"
        }
        return resolved
    }
}
