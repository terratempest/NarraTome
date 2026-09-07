package com.narratome.data.repository

import org.junit.Assert.*
import org.junit.Test

class CoverIdTest {
    @Test fun onlySingleNonControlPathSegmentsAreAccepted() {
        listOf("", ".", "..", "../auth_token", "a/b", "a\\b", "a\u0000b").forEach { assertFalse(validCoverId(it)) }
        listOf("book-123", "episode_42", "id.with.dots").forEach { assertTrue(validCoverId(it)) }
    }
}
