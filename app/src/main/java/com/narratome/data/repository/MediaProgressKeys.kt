package com.narratome.data.repository

const val PODCAST_PROGRESS_SEPARATOR = "::episode::"
private const val PLAYBACK_EPISODE_SEPARATOR = "|episode:"

fun mediaProgressKey(libraryItemId: String, episodeId: String? = null): String {
    val actualId = normalizedLibraryItemId(libraryItemId)
    val actualEpisodeId = episodeId?.takeIf { it.isNotBlank() } ?: episodeIdFromProgressKey(libraryItemId)
    return if (actualEpisodeId.isNullOrBlank()) actualId else "$actualId$PODCAST_PROGRESS_SEPARATOR$actualEpisodeId"
}

fun normalizedLibraryItemId(idOrProgressKey: String): String =
    idOrProgressKey
        .removePrefix("item:")
        .substringBefore("#")
        .substringBefore(PLAYBACK_EPISODE_SEPARATOR)
        .substringBefore(PODCAST_PROGRESS_SEPARATOR)
        .takeIf { it.isNotBlank() }
        ?: idOrProgressKey

fun libraryItemIdFromProgressKey(progressKey: String): String = normalizedLibraryItemId(progressKey)

fun episodeIdFromProgressKey(progressKey: String): String? {
    val normalized = progressKey.removePrefix("item:").substringBefore("#")
    normalized.substringAfter(PODCAST_PROGRESS_SEPARATOR, missingDelimiterValue = "")
        .takeIf { it.isNotBlank() }
        ?.let { return it }
    return normalized
        .substringAfter(PLAYBACK_EPISODE_SEPARATOR, missingDelimiterValue = "")
        .takeIf { it.isNotBlank() }
}
