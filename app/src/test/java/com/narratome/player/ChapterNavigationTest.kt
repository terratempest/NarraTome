package com.narratome.player

import com.google.common.truth.Truth.assertThat
import com.narratome.domain.model.BookChapter
import org.junit.Test

class ChapterNavigationTest {
    private val chapters = listOf(
        BookChapter("One", 0.0, 60.0),
        BookChapter("Two", 60.0, 120.0),
        BookChapter("Three", 120.0, 180.0),
    )

    @Test
    fun nextChapter_usesTheNextChapterMoreThanOneSecondAhead() {
        assertThat(nextChapterSeekPositionMs(chapters, 58_999L)).isEqualTo(60_000L)
        assertThat(nextChapterSeekPositionMs(chapters, 59_500L)).isEqualTo(120_000L)
        assertThat(nextChapterSeekPositionMs(chapters, 130_000L)).isNull()
    }

    @Test
    fun previousChapter_restartsOrMovesBackUsingTheExistingThresholds() {
        assertThat(previousChapterSeekPositionMs(chapters, 70_000L)).isEqualTo(60_000L)
        assertThat(previousChapterSeekPositionMs(chapters, 62_000L)).isEqualTo(0L)
        assertThat(previousChapterSeekPositionMs(chapters, 1_000L)).isEqualTo(0L)
        assertThat(previousChapterSeekPositionMs(chapters, 60_000L)).isEqualTo(0L)
    }

    @Test
    fun previousChapter_withoutChapterAtPositionFallsBackToMediaItem() {
        assertThat(previousChapterSeekPositionMs(emptyList(), 20_000L)).isNull()
        assertThat(previousChapterSeekPositionMs(chapters, -200L)).isNull()
    }

}
