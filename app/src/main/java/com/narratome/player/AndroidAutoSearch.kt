package com.narratome.player

import com.narratome.domain.model.LibraryItemSummary

/** The offline callback must query downloaded items only, including after an online failure. */
internal suspend fun searchForAuto(
    query: String,
    online: Boolean,
    remote: suspend (String) -> List<LibraryItemSummary>?,
    downloaded: suspend (String) -> List<LibraryItemSummary>,
): List<LibraryItemSummary> {
    val normalized = query.trim()
    if (normalized.isEmpty()) return emptyList()
    val results = if (online) remote(normalized) else null
    return (results ?: downloaded(normalized)).distinctBy { it.id }.take(80)
}
