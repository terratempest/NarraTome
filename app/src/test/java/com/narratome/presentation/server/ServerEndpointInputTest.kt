package com.narratome.presentation.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class ServerEndpointInputTest {
    @Test
    fun missingSchemeUsesHttpPreferenceAndPreservesEndpointPath() {
        val http = normalizeServerEndpoint("books.local:13378/abs", false, false, "primary")
        val https = normalizeServerEndpoint("books.local:13378/abs", true, false, "primary")

        assertEquals("http://books.local:13378/abs", http.value)
        assertEquals("https://books.local:13378/abs", https.value)
    }

    @Test
    fun explicitSchemeIsPreservedAndRequireHttpsRejectsExplicitHttp() {
        assertEquals(
            "http://books.local:13378",
            normalizeServerEndpoint("http://books.local:13378", false, false, "primary").value,
        )
        assertThrows(IllegalArgumentException::class.java) {
            normalizeServerEndpoint("http://books.local:13378", true, false, "primary")
        }
    }

    @Test
    fun requireHttpsUpdatesOnlyAnInferredScheme() {
        assertEquals("https://books.local:13378", applyDefaultScheme("http://books.local:13378", true, true))
        assertEquals("http://books.local:13378", applyDefaultScheme("http://books.local:13378", true, false))
        assertTrue(inferredSchemeAfterEdit("http://books.local", true, "http://books.local/new"))
        assertFalse(inferredSchemeAfterEdit("http://books.local", true, "https://books.local"))
    }

    @Test
    fun invalidUrlsAndEmbeddedCredentialsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            normalizeServerEndpoint("http://", false, false, "primary")
        }
        assertThrows(IllegalArgumentException::class.java) {
            normalizeServerEndpoint("https://user:password@books.local", false, false, "primary")
        }
    }
}
