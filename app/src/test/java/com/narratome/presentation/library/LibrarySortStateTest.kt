package com.narratome.presentation.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySortStateTest {
    @Test
    fun sortCyclesForwardReverseOffAndSwitchesToForward() {
        for (option in LibrarySortOption.entries) {
            val forward = LibrarySortState().cycle(option)
            assertEquals(LibrarySortState(option), forward)
            val reverse = forward.cycle(option)
            assertEquals(LibrarySortState(option, reversed = true), reverse)
            val off = reverse.cycle(option)
            assertEquals(LibrarySortState(), off)
            assertEquals(forward, off.cycle(option))
            for (other in LibrarySortOption.entries.filter { it != option }) {
                assertEquals(LibrarySortState(other), forward.cycle(other))
                assertEquals(LibrarySortState(other), reverse.cycle(other))
            }
        }
    }
}
