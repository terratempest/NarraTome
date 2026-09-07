package com.narratome.data.remote

import com.narratome.data.remote.dto.PlayItemResponseDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayResponseParserTest {

    private val lenientJson = Json { ignoreUnknownKeys = true }

    @Test
    fun extractFirstPlayableUrl_relativePath_joinsBase() {
        val json = Json.parseToJsonElement(
            """
            {"audioTracks":[{"contentUrl":"/s/item/abc/track.mp3"}]}
            """.trimIndent(),
        )
        val url = PlayResponseParser.extractFirstPlayableUrl(json, "https://example.com:13378")
        assertEquals("https://example.com:13378/s/item/abc/track.mp3", url)
    }

    @Test
    fun extractFirstPlayableUrl_absolute_unchanged() {
        val obj = JsonObject(
            mapOf(
                "audioTracks" to Json.parseToJsonElement(
                    """[{"contentUrl":"https://cdn.example/audio.mp3"}]""",
                ),
            ),
        )
        val url = PlayResponseParser.extractFirstPlayableUrl(obj, "https://ignored")
        assertEquals("https://cdn.example/audio.mp3", url)
    }

    @Test
    fun extractFirstPlayableUrl_missing_returnsNull() {
        assertNull(PlayResponseParser.extractFirstPlayableUrl(JsonObject(emptyMap()), "https://x"))
    }

    @Test
    fun extractAllPlayableUrls_multipleTracks() {
        val json = Json.parseToJsonElement(
            """
            {"audioTracks":[
              {"contentUrl":"/a/1.mp3"},
              {"contentUrl":"https://cdn.example/2.m4b"}
            ]}
            """.trimIndent(),
        )
        val urls = PlayResponseParser.extractAllPlayableUrls(json, "https://srv")
        assertEquals(
            listOf(
                "https://srv/a/1.mp3",
                "https://cdn.example/2.m4b",
            ),
            urls.map { it.url },
        )
    }

    @Test
    fun extractAllPlayableUrls_playItemDto_decodesSameAsJson() {
        val raw = """
            {"audioTracks":[
              {"contentUrl":"/a/1.mp3"},
              {"contentUrl":"https://cdn.example/2.m4b"}
            ]}
        """.trimIndent()
        val jsonUrls = PlayResponseParser.extractAllPlayableUrls(Json.parseToJsonElement(raw), "https://srv")
        val dto = lenientJson.decodeFromString<PlayItemResponseDto>(raw)
        val dtoUrls = PlayResponseParser.extractAllPlayableUrls(dto, "https://srv")
        assertEquals(jsonUrls, dtoUrls)
    }
}
