package com.narratome.data.remote.dto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpandedLibraryItemDtoTest {

    @Test
    fun toBookDetailParsed_minimalBook() {
        val json = """
            {
              "id":"item1",
              "libraryId":"lib1",
              "mediaType":"book",
              "media":{
                "metadata":{"title":"T","authorName":"A"},
                "duration":3600.0,
                "chapters":[{"title":"C1","start":0,"end":10}]
              },
              "userMediaProgress":{"currentTime":5,"lastUpdate":"1700000000000","finishedAt":1700000001234}
            }
        """.trimIndent()
        val dto = ExpandedItemJson.decodeFromString(ExpandedLibraryItemDto.serializer(), json)
        val p = dto.toBookDetailParsed("fallback")!!
        assertEquals("item1", p.id)
        assertEquals("lib1", p.libraryId)
        assertEquals("T", p.title)
        assertEquals("A", p.author)
        assertEquals(1, p.chapters.size)
        assertEquals(5.0, p.currentTimeSec, 0.001)
        assertEquals(1700000000000L, p.serverProgressLastUpdate)
        assertEquals(1700000001234L, p.finishedAt)
    }

    @Test
    fun toBookDetailParsed_acceptsPodcastWhenMediaTypeSet() {
        val json = """{"id":"x","libraryId":"l","mediaType":"podcast","media":{"metadata":{}}}"""
        val dto = ExpandedItemJson.decodeFromString(ExpandedLibraryItemDto.serializer(), json)
        val parsed = dto.toBookDetailParsed("l")
        assertNotNull(parsed)
        assertEquals("podcast", parsed!!.mediaType)
    }

    @Test
    fun toBookDetailParsed_seriesSequenceAsJsonNumber() {
        val json =
            """{"id":"a","libraryId":"l","mediaType":"book","media":{"metadata":{"title":"T","seriesSequence":3}}}"""
        val dto = ExpandedItemJson.decodeFromString(ExpandedLibraryItemDto.serializer(), json)
        assertEquals("3", dto.toBookDetailParsed("l")!!.seriesSequence)
    }

    @Test
    fun toBookDetailParsed_progressTimesFlexible() {
        val json = """
            {"id":"a","libraryId":"l","mediaType":"book","media":{"metadata":{"title":"T"}},
             "userMediaProgress":{"currentTime":12,"lastUpdate":1700000000123}}
        """.trimIndent()
        val dto = ExpandedItemJson.decodeFromString(ExpandedLibraryItemDto.serializer(), json)
        val p = dto.toBookDetailParsed("l")!!
        assertEquals(12.0, p.currentTimeSec, 0.001)
        assertEquals(1700000000123L, p.serverProgressLastUpdate)
    }

    @Test
    fun toBookDetailParsed_lastUpdateIso8601String() {
        val json = """
            {"id":"a","libraryId":"l","mediaType":"book","media":{"metadata":{"title":"T"}},
             "userMediaProgress":{"currentTime":0,"lastUpdate":"2024-01-15T12:30:00.000Z"}}
        """.trimIndent()
        val dto = ExpandedItemJson.decodeFromString(ExpandedLibraryItemDto.serializer(), json)
        val last = dto.toBookDetailParsed("l")!!.serverProgressLastUpdate
        assertNotNull(last)
        assertTrue(last!! > 1_000_000_000_000L)
    }

    @Test
    fun toBookDetailParsed_publishedYearAsJsonNumber() {
        val json = """{"id":"a","libraryId":"l","mediaType":"book","media":{"metadata":{"title":"T","publishedYear":1999}}}"""
        val dto = ExpandedItemJson.decodeFromString(ExpandedLibraryItemDto.serializer(), json)
        val p = dto.toBookDetailParsed("l")!!
        assertEquals("1999", p.publishedYear)
    }

    @Test
    fun toBookDetailParsed_seriesIdFromMetadataSeriesArray() {
        val json = """
            {"id":"item1","libraryId":"lib1","mediaType":"book","media":{
              "metadata":{
                "title":"T",
                "seriesName":"My Series",
                "seriesSequence":"2",
                "series":[{"id":"s1","name":"Other","sequence":"1"},{"id":"s2","name":"My Series","sequence":"2"}]
              },
              "duration":1.0
            }}
        """.trimIndent()
        val dto = ExpandedItemJson.decodeFromString(ExpandedLibraryItemDto.serializer(), json)
        val p = dto.toBookDetailParsed("lib1")!!
        assertEquals("s2", p.seriesId)
    }

    @Test
    fun parseBookDetailFromDto_jsonElementRoundTrip() {
        val json = ExpandedItemJson.parseToJsonElement(
            """{"id":"i","libraryId":"l","mediaType":"book","media":{"metadata":{"title":"X"}}}""",
        )
        val p = json.parseBookDetailFromDto("fb")
        assertNotNull(p)
        assertEquals("i", p!!.id)
        assertEquals("l", p.libraryId)
        assertEquals("X", p.title)
    }
}
