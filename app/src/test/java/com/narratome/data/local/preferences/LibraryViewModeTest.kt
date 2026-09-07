package com.narratome.data.local.preferences

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LibraryViewModeTest {

    @Test
    fun fromPreferenceValue_defaultsToLargeGrid() {
        assertThat(LibraryViewMode.fromPreferenceValue(null)).isEqualTo(LibraryViewMode.LargeGrid)
        assertThat(LibraryViewMode.fromPreferenceValue("")).isEqualTo(LibraryViewMode.LargeGrid)
        assertThat(LibraryViewMode.fromPreferenceValue("unknown")).isEqualTo(LibraryViewMode.LargeGrid)
    }

    @Test
    fun fromPreferenceValue_readsSavedModes() {
        assertThat(LibraryViewMode.fromPreferenceValue("large_grid")).isEqualTo(LibraryViewMode.LargeGrid)
        assertThat(LibraryViewMode.fromPreferenceValue("compact_grid")).isEqualTo(LibraryViewMode.CompactGrid)
        assertThat(LibraryViewMode.fromPreferenceValue("list")).isEqualTo(LibraryViewMode.List)
    }
}
