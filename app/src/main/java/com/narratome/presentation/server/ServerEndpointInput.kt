package com.narratome.presentation.server

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal data class NormalizedServerEndpoint(
    val url: HttpUrl,
    val value: String,
)

internal fun hasHttpScheme(value: String): Boolean =
    value.trimStart().startsWith("http://", ignoreCase = true) ||
        value.trimStart().startsWith("https://", ignoreCase = true)

internal fun inferredSchemeAfterEdit(
    previousValue: String,
    previousWasInferred: Boolean,
    newValue: String,
): Boolean {
    val next = newValue.trimStart()
    if (hasHttpScheme(next)) {
        val previousScheme = previousValue.trimStart().substringBefore("://", "")
        val nextScheme = next.substringBefore("://", "")
        return previousWasInferred && previousScheme.equals(nextScheme, ignoreCase = true)
    }
    return "://" !in next
}

internal fun applyDefaultScheme(
    value: String,
    requireHttps: Boolean,
    schemeWasInferred: Boolean,
): String {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return trimmed

    val suffix = when {
        schemeWasInferred && trimmed.startsWith("http://", ignoreCase = true) -> trimmed.substring(7)
        schemeWasInferred && trimmed.startsWith("https://", ignoreCase = true) -> trimmed.substring(8)
        else -> {
            if (hasHttpScheme(trimmed) || "://" in trimmed) return trimmed
            trimmed
        }
    }
    val scheme = if (requireHttps) "https://" else "http://"
    return scheme + suffix
}

internal fun normalizeServerEndpoint(
    value: String,
    requireHttps: Boolean,
    schemeWasInferred: Boolean,
    label: String,
): NormalizedServerEndpoint {
    val input = applyDefaultScheme(value, requireHttps, schemeWasInferred)
    require(input.isNotBlank()) { "Enter the $label server URL." }

    val url = input.toHttpUrlOrNull()
        ?: throw IllegalArgumentException("Enter a valid $label server URL.")
    require(url.username.isBlank() && url.password.isBlank()) {
        "Remove login details from the $label URL; enter them in the account fields."
    }
    require(url.encodedQuery == null && url.encodedFragment == null) {
        "Remove the query or fragment from the $label server URL."
    }
    require(!requireHttps || url.isHttps) {
        "Require HTTPS is on. Change the $label URL to HTTPS or turn off Require HTTPS."
    }

    return NormalizedServerEndpoint(url, url.toString().trimEnd('/'))
}
