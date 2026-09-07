package com.narratome.presentation.root

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.narratome.presentation.navigation.Routes

data class RootDrawerItem(
    val label: String,
    val route: String,
    val icon: ImageVector,
)

fun rootDrawerItems(): List<RootDrawerItem> = listOf(
    RootDrawerItem("Home", Routes.HOME, Icons.Default.Home),
    RootDrawerItem("Library", Routes.LIBRARY, Icons.AutoMirrored.Filled.LibraryBooks),
    RootDrawerItem("Series", Routes.SERIES, Icons.AutoMirrored.Filled.ViewList),
    RootDrawerItem("Collections", Routes.COLLECTIONS, Icons.Default.Collections),
    RootDrawerItem("Authors", Routes.AUTHORS, Icons.Default.Group),
    RootDrawerItem("Downloads", Routes.DOWNLOADS, Icons.Default.CloudDownload),
    RootDrawerItem("Server", Routes.SERVER, Icons.Default.Public),
    RootDrawerItem("Settings", Routes.SETTINGS, Icons.Default.Settings),
)
