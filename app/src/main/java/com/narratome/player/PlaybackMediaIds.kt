package com.narratome.player

private const val ItemPrefix = "item:"
private const val EpisodeMarker = "::episode::"

fun playbackMediaId(libraryItemId: String?, trackIndex: Int, episodeId: String? = null): String? =
    libraryItemId
        ?.takeIf { it.isNotBlank() }
        ?.let { id ->
            val episodePart = episodeId?.takeIf { it.isNotBlank() }?.let { "$EpisodeMarker$it" }.orEmpty()
            "$ItemPrefix$id$episodePart#$trackIndex"
        }

fun playbackItemMediaId(libraryItemId: String, episodeId: String? = null): String {
    val episodePart = episodeId?.takeIf { it.isNotBlank() }?.let { "$EpisodeMarker$it" }.orEmpty()
    return "$ItemPrefix$libraryItemId$episodePart"
}

fun playbackLibraryItemIdFromMediaId(mediaId: String?): String? =
    mediaId
        ?.takeIf { it.startsWith(ItemPrefix) }
        ?.removePrefix(ItemPrefix)
        ?.substringBefore("#")
        ?.substringBefore(EpisodeMarker)
        ?.takeIf { it.isNotBlank() }

fun playbackEpisodeIdFromMediaId(mediaId: String?): String? =
    mediaId
        ?.takeIf { it.startsWith(ItemPrefix) }
        ?.removePrefix(ItemPrefix)
        ?.substringBefore("#")
        ?.substringAfter(EpisodeMarker, missingDelimiterValue = "")
        ?.takeIf { it.isNotBlank() }
