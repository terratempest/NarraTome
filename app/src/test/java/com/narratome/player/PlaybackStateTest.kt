package com.narratome.player

import com.narratome.domain.model.BookChapter
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlaybackStateTest {

    @Test
    fun playerStateForActiveItem_clearsPreviousBookMetadata() {
        val previous = PlayerState(
            libraryItemId = "book-1",
            libraryId = "library-1",
            catalogDurationSec = 3600.0,
            title = "First book",
            author = "Author",
            seriesName = "Series",
            chapters = listOf(BookChapter("Chapter 1", 0.0, 60.0)),
        )

        val result = playerStateForActiveItem(previous, "book-2")

        assertThat(result.libraryItemId).isEqualTo("book-2")
        assertThat(result.libraryId).isNull()
        assertThat(result.catalogDurationSec).isNull()
        assertThat(result.title).isNull()
        assertThat(result.seriesName).isNull()
        assertThat(result.chapters).isEmpty()
    }
}
