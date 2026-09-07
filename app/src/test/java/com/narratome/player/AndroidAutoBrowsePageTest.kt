package com.narratome.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AndroidAutoBrowsePageTest {

    @Test
    fun from_usesRequestedPageAndSize() {
        val result = AndroidAutoBrowsePage.from(page = 2, pageSize = 25)

        assertThat(result).isEqualTo(AndroidAutoBrowsePage(limit = 25, offset = 50))
    }

    @Test
    fun from_defaultsInvalidSizeAndClampsOversizeValues() {
        assertThat(AndroidAutoBrowsePage.from(page = -1, pageSize = 0))
            .isEqualTo(AndroidAutoBrowsePage(limit = 50, offset = 0))
        assertThat(AndroidAutoBrowsePage.from(page = 1, pageSize = 500))
            .isEqualTo(AndroidAutoBrowsePage(limit = 200, offset = 200))
    }
}
