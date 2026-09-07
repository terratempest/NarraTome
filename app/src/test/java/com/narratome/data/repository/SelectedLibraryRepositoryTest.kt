package com.narratome.data.repository

import com.narratome.data.remote.dto.LibrariesResponseDto
import com.narratome.data.remote.dto.LibraryDto
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class SelectedLibraryRepositoryTest {
    @Test
    fun onlineSelectionPreservesSavedLibraryOrUsesServerOrder() {
        val libraries = listOf(LibraryDto("history", "History"), LibraryDto("fiction", "Fiction"))
        assertThat(chooseSelectedLibraryId("fiction", libraries)).isEqualTo("fiction")
        assertThat(chooseSelectedLibraryId("missing", libraries)).isEqualTo("history")
    }

    @Test
    fun offlineLibrariesRestoreNamesFilterDownloadsAndAllowSwitching() {
        val metadata = LibrariesResponseDto(listOf(
            LibraryDto("history", "History", "book"),
            LibraryDto("fiction", "fiction", "book"),
            LibraryDto("remote", "Remote only", "book"),
        ))
        val saved = Json.encodeToString(LibrariesResponseDto.serializer(), metadata)
        val restored = Json.decodeFromString(LibrariesResponseDto.serializer(), saved).libraries
        val libraries = downloadedLibraries(listOf("history", "fiction", "history"), restored)

        assertThat(libraries.map { it.name }).containsExactly("fiction", "History").inOrder()
        assertThat(chooseSelectedLibraryId("history", libraries)).isEqualTo("history")
        assertThat(chooseSelectedLibraryId("fiction", libraries)).isEqualTo("fiction")
        assertThat(chooseSelectedLibraryId("remote", libraries)).isEqualTo("fiction")
        assertThat(chooseSelectedLibraryId(null, libraries)).isEqualTo("fiction")
        assertThat(chooseSelectedLibraryId("history", downloadedLibraries(emptyList(), restored))).isNull()
        assertThat(downloadedLibraries(listOf("legacy"), restored).single().name).isEqualTo("Library (legacy)")
        assertThat(downloadedLibraries(listOf("blank"), listOf(LibraryDto("blank", " "))).single().name)
            .isEqualTo("Library (blank)")
    }
}
