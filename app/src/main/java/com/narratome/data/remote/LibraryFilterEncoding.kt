package com.narratome.data.remote

import android.util.Base64
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Audiobookshelf library item filters use `group.{base64url-ish segment}` where the segment is
 * `encodeURIComponent(Buffer.from(value, "utf8").toString("base64"))` (see server `libraryFilters.decode`).
 */
object LibraryFilterEncoding {

    fun encodeFilterValue(raw: String): String =
        URLEncoder.encode(
            Base64.encodeToString(raw.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP),
            StandardCharsets.UTF_8.name(),
        )

    /** Value for the `filter` query on [AudiobookshelfApi.libraryItems] for books in one series. */
    fun seriesFilter(seriesId: String): String = "series.${encodeFilterValue(seriesId)}"
}
