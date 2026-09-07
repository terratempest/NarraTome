package com.narratome.presentation.root

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.narratome.presentation.authors.AuthorsScreen
import com.narratome.presentation.authors.AuthorsViewModel
import com.narratome.presentation.collections.CollectionsScreen
import com.narratome.presentation.collections.CollectionsViewModel
import com.narratome.presentation.detail.DetailScreen
import com.narratome.presentation.filtered.FilteredBooksScreen
import com.narratome.presentation.downloads.DownloadsScreen
import com.narratome.presentation.home.HomeScreen
import com.narratome.presentation.library.LibraryScreen
import com.narratome.presentation.navigation.Routes
import com.narratome.presentation.series.SeriesScreen
import com.narratome.presentation.series.SeriesViewModel
import com.narratome.presentation.server.ServerScreen
import com.narratome.presentation.settings.SettingsScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier,
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenItem = { libId, itemId ->
                    navController.navigate(Routes.detail(libId, itemId))
                },
                onOpenDrawer = onOpenDrawer
            )
        }
        composable(Routes.LIBRARY) {
            LibraryScreen(
                onOpenItem = { libId, itemId ->
                    navController.navigate(Routes.detail(libId, itemId))
                },
            )
        }
        composable(Routes.SERIES) {
            val seriesVm: SeriesViewModel = hiltViewModel()
            val state by seriesVm.state.collectAsStateWithLifecycle()
            SeriesScreen(
                onSeriesClick = { libraryId, seriesId ->
                    navController.navigate(Routes.filteredBooks(libraryId, "series", seriesId))
                },
                viewModel = seriesVm
            )
        }
        composable(
            route = Routes.FILTERED_BOOKS,
            arguments = listOf(
                navArgument("libraryId") { type = NavType.StringType },
                navArgument("filterType") { type = NavType.StringType },
                navArgument("filterValue") { type = NavType.StringType },
            ),
        ) {
            FilteredBooksScreen(
                onBack = { navController.popBackStack() },
                onOpenItem = { libId, itemId ->
                    navController.navigate(Routes.detail(libId, itemId))
                },
            )
        }
        composable(Routes.COLLECTIONS) {
            val collectionsVm: CollectionsViewModel = hiltViewModel()
            CollectionsScreen(
                onCollectionClick = { libraryId, collectionId ->
                    navController.navigate(Routes.filteredBooks(libraryId, "collection", collectionId))
                },
                viewModel = collectionsVm,
            )
        }
        composable(Routes.AUTHORS) {
            val authorsVm: AuthorsViewModel = hiltViewModel()
            val state by authorsVm.state.collectAsStateWithLifecycle()
            AuthorsScreen(
                onAuthorClick = { authorId ->
                    state.libraryId?.let { libId ->
                        val authorName = state.authors.find { it.id == authorId }?.name ?: authorId
                        navController.navigate(Routes.filteredBooks(libId, "author", authorName))
                    }
                },
                viewModel = authorsVm
            )
        }
        composable(Routes.DOWNLOADS) {
            DownloadsScreen(
                onOpenBook = { libId, itemId ->
                    navController.navigate(Routes.detail(libId, itemId)) {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(Routes.SERVER) { ServerScreen() }
        composable(Routes.SETTINGS) { SettingsScreen() }
        composable(
            route = Routes.CONTINUE,
            deepLinks = listOf(
                navDeepLink { uriPattern = "narratome://continue" },
            ),
        ) {
            val continueVm: ContinueRouteViewModel = hiltViewModel()
            LaunchedEffect(Unit) {
                val snap = continueVm.readResumeSnapshot()
                if (snap?.libraryItemId != null && snap.libraryId != null) {
                    navController.navigate(Routes.detail(snap.libraryId, snap.libraryItemId)) {
                        popUpTo(Routes.CONTINUE) { inclusive = true }
                        launchSingleTop = true
                    }
                } else {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.CONTINUE) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(
                navArgument("libraryId") { type = NavType.StringType },
                navArgument("itemId") { type = NavType.StringType },
            ),
            deepLinks = listOf(
                navDeepLink { uriPattern = "narratome://open/item/{libraryId}/{itemId}" },
                navDeepLink { uriPattern = "https://narratome.invalid/open/item/{libraryId}/{itemId}" },
            ),
        ) {
            DetailScreen(
                onBack = { navController.popBackStack() },
                onOpenSeries = { libId, sid ->
                    navController.navigate(Routes.filteredBooks(libId, "series", sid))
                },
                onOpenAuthor = { libId, authorName ->
                    navController.navigate(Routes.filteredBooks(libId, "author", authorName))
                },
            )
        }
    }
}
