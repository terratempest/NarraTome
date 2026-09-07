package com.narratome.data.catalog

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogMinifiedParserTest {

    @Test
    fun toProgressSnapshotFromMinified_parsesFinishedOnlyProgress() {
        val element = Json.parseToJsonElement(
            """
            {
              "id": "book-1",
              "libraryId": "lib-1",
              "mediaType": "book",
              "media": {"metadata": {"title": "T"}},
              "userMediaProgress": {
                "finishedAt": 1700000001234,
                "lastUpdate": "1700000005678"
              }
            }
            """.trimIndent(),
        )

        val progress = element.toProgressSnapshotFromMinified("fallback")!!

        assertEquals("book-1", progress.libraryItemId)
        assertEquals("lib-1", progress.libraryId)
        assertNull(progress.currentTime)
        assertNull(progress.duration)
        assertEquals(1700000001234L, progress.finishedAt)
        assertEquals(1700000005678L, progress.lastUpdate)
    }
}
