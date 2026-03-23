package com.passmanager.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.passmanager.R
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.passmanager.ui.desktop.DesktopLinkScreen
import com.passmanager.ui.generator.PasswordGeneratorScreen
import com.passmanager.ui.item.AddEditItemScreen
import com.passmanager.ui.item.AddEditItemViewModel
import com.passmanager.ui.item.ViewItemPresentation
import com.passmanager.ui.item.ViewItemScreen
import com.passmanager.ui.item.ViewItemViewModel
import com.passmanager.ui.settings.SettingsScreen
import com.passmanager.ui.vault.VaultListScreen
import kotlinx.coroutines.launch

private object DrawerRoutes {
    const val VAULT_LIST = "vault_list"
    const val GENERATOR = "password_generator_drawer"
    const val DESKTOP_LINK = "desktop_link"
    const val SETTINGS = "settings_drawer"
}

private data class DrawerItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val drawerItems = listOf(
    DrawerItem(DrawerRoutes.VAULT_LIST, "Vault", Icons.Default.Lock),
    DrawerItem(DrawerRoutes.GENERATOR, "Generator", Icons.Default.Password),
    DrawerItem(DrawerRoutes.DESKTOP_LINK, "Desktop Link", Icons.Default.Laptop),
    DrawerItem(DrawerRoutes.SETTINGS, "Settings", Icons.Default.Settings)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTabNavHost(
    rootNavController: NavHostController,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    ModalNavigationDrawer(
        drawerState = drawerState,
        modifier = modifier,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp)
                )
                Spacer(Modifier.height(8.dp))

                drawerItems.forEach { item ->
                    val selected = when (item.route) {
                        DrawerRoutes.VAULT_LIST -> currentRoute == DrawerRoutes.VAULT_LIST
                            || currentRoute == Screen.AddEditItem.route
                        else -> currentRoute == item.route
                    }
                    NavigationDrawerItem(
                        label = { Text(item.label, style = MaterialTheme.typography.labelLarge) },
                        icon = { Icon(item.icon, contentDescription = item.label, modifier = Modifier.size(24.dp)) },
                        selected = selected,
                        onClick = {
                            scope.launch { drawerState.close() }
                            if (!selected) {
                                navController.navigate(item.route) {
                                    popUpTo(DrawerRoutes.VAULT_LIST) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }
        }
    ) {
        NavHost(
            navController = navController,
            startDestination = DrawerRoutes.VAULT_LIST,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(
                DrawerRoutes.VAULT_LIST,
                enterTransition = { fadeIn(tween(250)) },
                exitTransition = { fadeOut(tween(250)) },
                popEnterTransition = { fadeIn(tween(250)) + slideInHorizontally(tween(250)) { -it / 4 } },
                popExitTransition = { fadeOut(tween(250)) + slideOutHorizontally(tween(250)) { it / 4 } }
            ) {
                var viewItemSheetId by remember { mutableStateOf<String?>(null) }
                val configuration = LocalConfiguration.current
                val maxSheetHeight = (configuration.screenHeightDp * 0.70f).dp

                VaultListScreen(
                    onNavigateToAddItem = {
                        navController.navigate(Screen.AddEditItem.createRoute(null))
                    },
                    onNavigateToViewItem = { itemId -> viewItemSheetId = itemId },
                    onNavigateToSettings = {
                        navController.navigate(DrawerRoutes.SETTINGS) {
                            popUpTo(DrawerRoutes.VAULT_LIST) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onLocked = {
                        rootNavController.navigate(Screen.Lock.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )

                val activeSheetId = viewItemSheetId
                if (activeSheetId != null) {
                    val viewItemVm: ViewItemViewModel =
                        hiltViewModel(key = "viewItem_$activeSheetId")

                    LaunchedEffect(activeSheetId) {
                        viewItemVm.loadForItem(activeSheetId)
                    }

                    val sheetState = rememberModalBottomSheetState(
                        skipPartiallyExpanded = true
                    )

                    ModalBottomSheet(
                        onDismissRequest = { viewItemSheetId = null },
                        sheetState = sheetState,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f),
                        dragHandle = null
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = maxSheetHeight)
                        ) {
                            ViewItemScreen(
                                itemId = activeSheetId,
                                presentation = ViewItemPresentation.Sheet,
                                onNavigateBack = {
                                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                                        viewItemSheetId = null
                                    }
                                },
                                onRequestEdit = {
                                    viewItemSheetId = null
                                    navController.navigate(
                                        Screen.AddEditItem.createRoute(activeSheetId)
                                    )
                                },
                                viewModel = viewItemVm
                            )
                        }
                    }
                }
            }

            composable(
                route = Screen.AddEditItem.route,
                enterTransition = { fadeIn(tween(250)) + slideInHorizontally(tween(250)) { it } },
                exitTransition = { fadeOut(tween(250)) + slideOutHorizontally(tween(250)) { -it / 4 } },
                popEnterTransition = { fadeIn(tween(250)) + slideInHorizontally(tween(250)) { -it / 4 } },
                popExitTransition = { fadeOut(tween(250)) + slideOutHorizontally(tween(250)) { it } },
                arguments = listOf(
                    navArgument("itemId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val itemId = backStackEntry.arguments?.getString("itemId")
                val addEditVm: AddEditItemViewModel = hiltViewModel()

                LaunchedEffect(backStackEntry) {
                    backStackEntry.savedStateHandle
                        .getStateFlow("generated_password", "")
                        .collect { pwd ->
                            if (pwd.isNotEmpty()) {
                                addEditVm.applyGeneratedPassword(pwd)
                                backStackEntry.savedStateHandle.remove<String>("generated_password")
                            }
                        }
                }

                AddEditItemScreen(
                    itemId = itemId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToGenerator = {
                        navController.navigate(Screen.PasswordGenerator.route)
                    },
                    viewModel = addEditVm
                )
            }

            composable(
                Screen.PasswordGenerator.route,
                enterTransition = { fadeIn(tween(250)) + slideInHorizontally(tween(250)) { it } },
                exitTransition = { fadeOut(tween(250)) + slideOutHorizontally(tween(250)) { -it / 4 } },
                popEnterTransition = { fadeIn(tween(250)) + slideInHorizontally(tween(250)) { -it / 4 } },
                popExitTransition = { fadeOut(tween(250)) + slideOutHorizontally(tween(250)) { it } }
            ) {
                PasswordGeneratorScreen(
                    onPasswordSelected = { password ->
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set("generated_password", password)
                        navController.popBackStack()
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(DrawerRoutes.GENERATOR) {
                PasswordGeneratorScreen(
                    onPasswordSelected = { },
                    onNavigateBack = {
                        navController.navigate(DrawerRoutes.VAULT_LIST) {
                            popUpTo(DrawerRoutes.VAULT_LIST) { inclusive = true }
                        }
                    },
                    showUseButton = false
                )
            }

            composable(
                DrawerRoutes.SETTINGS,
                enterTransition = { fadeIn(tween(250)) },
                exitTransition = { fadeOut(tween(250)) }
            ) {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onVaultLocked = {
                        rootNavController.navigate(Screen.Lock.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }

            composable(
                DrawerRoutes.DESKTOP_LINK,
                enterTransition = { fadeIn(tween(250)) },
                exitTransition = { fadeOut(tween(250)) }
            ) {
                DesktopLinkScreen(
                    onOpenDrawer = { scope.launch { drawerState.open() } }
                )
            }
        }
    }
}
