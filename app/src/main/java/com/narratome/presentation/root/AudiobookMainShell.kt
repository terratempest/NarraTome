package com.narratome.presentation.root

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.narratome.R
import com.narratome.presentation.navigation.Routes
import com.narratome.presentation.player.FullPlayerSheet
import com.narratome.presentation.player.MiniPlayerViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudiobookMainShell() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route.orEmpty()
    val hideTabs = route == Routes.CONTINUE
    val isDetail = route.startsWith("detail/")
    val isFiltered = route.startsWith("filteredBooks/")
    val hideShellTopBar = isDetail || route.startsWith("seriesBooks/") || isFiltered
    val miniVm: MiniPlayerViewModel = hiltViewModel()
    val serverVm: ServerConnectionViewModel = hiltViewModel()
    val libraryVm: LibrarySelectionViewModel = hiltViewModel()
    val player by miniVm.playerState.collectAsStateWithLifecycle()
    val serverReachable by serverVm.serverReachable.collectAsStateWithLifecycle()
    val meteredActions by serverVm.meteredActions.collectAsStateWithLifecycle()
    val mediaNetwork by serverVm.mediaNetwork.collectAsStateWithLifecycle()
    val catalogSyncRunning by serverVm.catalogSyncRunning.collectAsStateWithLifecycle()
    val libraryUi by libraryVm.ui.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showFullPlayer by remember { mutableStateOf(false) }

    val activeDownloadCount by miniVm.activeDownloadCount.collectAsStateWithLifecycle()

    val navItems = remember { rootDrawerItems() }

    LaunchedEffect(player.libraryItemId) {
        if (player.libraryItemId == null) {
            showFullPlayer = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = !hideTabs && !isDetail,
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier.width(220.dp),
                    drawerContainerColor = MaterialTheme.colorScheme.surface,
                    drawerContentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    Spacer(Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "NARRATOME",
                            modifier = Modifier.padding(start = 28.dp, top = 16.dp, bottom = 16.dp, end = 8.dp),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp,
                                color = MaterialTheme.colorScheme.primary,
                            ),
                        )
                        // app icon
                        Icon(
                            painter = painterResource(R.drawable.narratomeicon),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    navItems.forEach { item ->
                        val selected = route == item.route
                        NavigationDrawerItem(
                            label = {
                                Text(
                                    item.label.uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                )
                            },
                            selected = selected,
                            onClick = {
                                scope.launch { drawerState.close() }
                                if (!selected && item.route == Routes.HOME) {
                                    navController.popBackStack(Routes.HOME, inclusive = false)
                                } else if (!selected) {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = null) },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            },
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    if (!hideTabs && !hideShellTopBar) {
                        TopAppBar(
                            title = {
                                val currentItem = navItems.find { it.route == route }
                                Text(currentItem?.label ?: "NarraTome")
                            },
                            navigationIcon = {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Filled.Menu, contentDescription = "Menu")
                                }
                            },
                            actions = {
                                LibrarySelectorButton(
                                    ui = libraryUi,
                                    onSelectLibrary = libraryVm::selectLibrary,
                                )
                                if (catalogSyncRunning) {
                                    val spin = rememberInfiniteTransition(label = "catalogSync")
                                    val rotation by spin.animateFloat(
                                        initialValue = 0f,
                                        targetValue = 360f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(1_100, easing = LinearEasing),
                                            repeatMode = RepeatMode.Restart,
                                        ),
                                        label = "catalogSyncRotation",
                                    )
                                    Icon(
                                        imageVector = Icons.Filled.Sync,
                                        contentDescription = "Syncing library with server",
                                        tint = Color(0xFFFFC107),
                                        modifier = Modifier
                                            .padding(end = 8.dp)
                                            .size(20.dp)
                                            .rotate(rotation),
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.Circle,
                                        contentDescription = if (serverReachable) {
                                            "Server connected"
                                        } else {
                                            "Server unreachable"
                                        },
                                        tint = if (serverReachable) Color(0xFF4CAF50) else Color(0xFFF44336),
                                        modifier = Modifier
                                            .padding(end = 8.dp)
                                            .size(14.dp),
                                    )
                                }
                                BadgedBox(
                                    badge = {
                                        if (activeDownloadCount > 0) {
                                            Badge { Text(activeDownloadCount.toString()) }
                                        }
                                    },
                                ) {
                                    IconButton(onClick = {
                                        navController.navigate(Routes.DOWNLOADS) {
                                            launchSingleTop = true
                                        }
                                    }) {
                                        Icon(Icons.Filled.CloudDownload, contentDescription = "Downloads")
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background,
                            ),
                        )
                    }
                },
                bottomBar = {
                    if (!hideTabs && player.libraryItemId != null) {
                        MiniPlayer(
                            player = player,
                            viewModel = miniVm,
                            onClick = { showFullPlayer = true },
                        )
                    }
                },
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = if (hideShellTopBar) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets
            ) { padding ->
                val onOpenDrawer = remember(drawerState, scope) {
                    {
                        if (drawerState.targetValue == DrawerValue.Closed) {
                            scope.launch { drawerState.open() }
                        }
                    }
                }
                AppNavHost(
                    navController = navController,
                    onOpenDrawer = onOpenDrawer,
                    modifier = Modifier.padding(padding),
                )
            }
        }

        AnimatedVisibility(
            visible = showFullPlayer && player.libraryItemId != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            FullPlayerSheet(
                player = player,
                viewModel = miniVm,
                onDismiss = { showFullPlayer = false },
                onOpenSeries = {
                    val lib = player.libraryId
                    val sid = player.seriesId
                    if (lib != null && sid != null) {
                        navController.navigate(Routes.filteredBooks(lib, "series", sid))
                        showFullPlayer = false
                    }
                },
                onOpenAuthor = {
                    val lib = player.libraryId
                    val author = player.author
                    if (lib != null && !author.isNullOrBlank()) {
                        navController.navigate(Routes.filteredBooks(lib, "author", author))
                        showFullPlayer = false
                    }
                },
            )
        }
        meteredActions.firstOrNull()?.let { action ->
            MeteredMediaDialog(
                action = action,
                network = mediaNetwork,
                acknowledge = { serverVm.acknowledgeMetered(action.key) },
                dismiss = { serverVm.dismissMetered(action.key) },
            )
        }
    }
}

@Composable
private fun LibrarySelectorButton(
    ui: LibrarySelectionUiState,
    onSelectLibrary: (String) -> Unit,
) {
    val label = ui.selectedLibraryName ?: return
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { if (ui.libraries.isNotEmpty()) expanded = true },
            enabled = ui.enabled,
            modifier = Modifier.widthIn(max = 150.dp),
        ) {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
            )
            if (ui.libraries.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            ui.libraries.forEach { library ->
                val selected = library.id == ui.selectedLibraryId
                DropdownMenuItem(
                    text = {
                        Text(
                            text = library.name?.takeIf { it.isNotBlank() } ?: "Library",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelectLibrary(library.id)
                    },
                    leadingIcon = if (selected) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}
