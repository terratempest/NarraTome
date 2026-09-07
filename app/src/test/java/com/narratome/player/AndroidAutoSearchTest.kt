package com.narratome.player

import com.narratome.domain.model.LibraryItemSummary
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AndroidAutoSearchTest {
    private fun book(id: String) = LibraryItemSummary(id, "library", id, null, "book", null)

    @Test fun onlineRankingIsPreservedAndNoMatchDoesNotInventAResult() = runBlocking {
        val result = searchForAuto("  title  ", true,
            remote = { assertEquals("title", it); listOf(book("b"), book("a"), book("b")) },
            downloaded = { error("Unexpected fallback") })
        assertEquals(listOf("b", "a"), result.map { it.id })
        assertTrue(searchForAuto("none", true, { emptyList() }, { error("Unexpected fallback") }).isEmpty())
        assertTrue(searchForAuto("  ", true, { error("Unexpected search") }, { error("Unexpected search") }).isEmpty())
    }

    @Test fun offlineAndFailedRequestsUseDownloadedResultsWithBoundedPages() = runBlocking {
        val downloaded = (0..100).map { book(it.toString()) }
        val offline = searchForAuto("book", false, { error("Network while offline") }, { downloaded })
        val failed = searchForAuto("book", true, { null }, { downloaded })
        assertEquals(offline, failed)
        assertEquals(80, offline.size)
        val page = AndroidAutoBrowsePage.from(3, 25)
        assertEquals(listOf("75", "76", "77", "78", "79"),
            offline.drop(page.offset).take(page.limit).map { it.id })
        assertTrue(offline.drop(AndroidAutoBrowsePage.from(Int.MAX_VALUE, 200).offset).isEmpty())
    }
}
