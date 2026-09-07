package com.narratome.presentation.navigation

import android.net.Uri

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val SERIES = "series"
    const val SERIES_BOOKS = "seriesBooks/{libraryId}/{seriesId}"
    const val FILTERED_BOOKS = "filteredBooks/{libraryId}/{filterType}/{filterValue}"
    const val COLLECTIONS = "collections"
    const val AUTHORS = "authors"
    const val SERVER = "server"
    const val SETTINGS = "settings"
    const val DOWNLOADS = "downloads"
    const val DETAIL = "detail/{libraryId}/{itemId}"
    const val CONTINUE = "continue_resume"

    fun detail(libraryId: String, itemId: String) =
        "detail/${libraryId.routeArg()}/${itemId.routeArg()}"

    fun seriesBooks(libraryId: String, seriesId: String) =
        "seriesBooks/${libraryId.routeArg()}/${seriesId.routeArg()}"

    fun filteredBooks(libraryId: String, filterType: String, filterValue: String) =
        "filteredBooks/${libraryId.routeArg()}/${filterType.routeArg()}/${filterValue.routeArg()}"

    private fun String.routeArg(): String = Uri.encode(this)
}
