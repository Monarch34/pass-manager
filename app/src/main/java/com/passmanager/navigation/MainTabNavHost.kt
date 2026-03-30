package com.passmanager.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
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
import com.passmanager.ui.settings.SettingsScreen
import com.passmanager.ui.vault.VaultListScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTabNavHost(
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
            AppDrawerContent(
                currentRoute = currentRoute,
                onItemClick = { item ->
                    scope.launch { drawerState.close() }
                    navController.navigate(item.route) {
                        popUpTo(Screen.VaultList.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    ) {
        NavHost(
            navController = navController,
            startDestination = Screen.VaultList.route,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(
                Screen.VaultList.route,
                enterTransition = { NavTransitions.fadeEnter },
                exitTransition = { NavTransitions.fadeExit },
                popEnterTransition = { NavTransitions.slideInFromLeft },
                popExitTransition = { NavTransitions.slideOutToRight }
            ) {
                var viewItemSheetId by remember { mutableStateOf<String?>(null) }

                VaultListScreen(
                    onNavigateToAddItem = { filterCategory ->
                        navController.navigate(
                            Screen.AddEditItem.createRoute(
                                itemId = null,
                                initialCategory = filterCategory?.name?.lowercase()
                            )
                        )
                    },
                    onNavigateToViewItem = { itemId -> viewItemSheetId = itemId },
                    onNavigateToSettings = {
                        navController.navigate(Screen.DrawerSettings.route) {
                            popUpTo(Screen.VaultList.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenDrawer = { scope.launch { drawerState.open() } }
                )

                val activeSheetId = viewItemSheetId
                if (activeSheetId != null) {
                    ViewItemSheet(
                        itemId = activeSheetId,
                        onDismiss = { viewItemSheetId = null },
                        onRequestEdit = { id ->
                            viewItemSheetId = null
                            navController.navigate(Screen.AddEditItem.createRoute(id))
                        }
                    )
                }
            }

            composable(
                route = Screen.AddEditItem.route,
                enterTransition = { NavTransitions.slideInFromRight },
                exitTransition = { NavTransitions.slideOutToLeft },
                popEnterTransition = { NavTransitions.slideInFromLeft },
                popExitTransition = { NavTransitions.slideOutToRight },
                arguments = listOf(
                    navArgument("itemId") {
                        type = NavType.StringType
                    },
                    navArgument("initialCategory") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) {
                val addEditVm: AddEditItemViewModel = hiltViewModel()

                val savedStateHandle = it.savedStateHandle
                val pending = savedStateHandle.get<CharArray>("generated_password")
                
                LaunchedEffect(pending) {
                    if (pending != null) {
                        addEditVm.applyGeneratedPassword(String(pending))
                        pending.fill('\u0000')
                        savedStateHandle.remove<CharArray>("generated_password")
                    }
                }

                val rawId = it.arguments?.getString("itemId")
                val cleanId =
                    if (rawId == Screen.AddEditItem.NEW_ITEM_ROUTE_ID) null else rawId

                AddEditItemScreen(
                    itemId = cleanId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToGenerator = {
                        navController.navigate(Screen.PasswordGenerator.route)
                    },
                    viewModel = addEditVm
                )
            }

            composable(
                Screen.PasswordGenerator.route,
                enterTransition = { NavTransitions.slideInFromRight },
                exitTransition = { NavTransitions.slideOutToLeft },
                popEnterTransition = { NavTransitions.slideInFromLeft },
                popExitTransition = { NavTransitions.slideOutToRight }
            ) {
                PasswordGeneratorScreen(
                    onPasswordSelected = { password ->
                        navController.previousBackStackEntry?.savedStateHandle?.set("generated_password", password)
                        navController.popBackStack()
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.DrawerGenerator.route) {
                PasswordGeneratorScreen(
                    onPasswordSelected = { },
                    onNavigateBack = {
                        navController.navigate(Screen.VaultList.route) {
                            popUpTo(Screen.VaultList.route) { inclusive = true }
                        }
                    },
                    showUseButton = false
                )
            }

            composable(
                Screen.DrawerSettings.route,
                enterTransition = { NavTransitions.fadeEnter },
                exitTransition = { NavTransitions.fadeExit }
            ) {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                Screen.DesktopLink.route,
                enterTransition = { NavTransitions.fadeEnter },
                exitTransition = { NavTransitions.fadeExit }
            ) {
                DesktopLinkScreen(
                    onOpenDrawer = { scope.launch { drawerState.open() } }
                )
            }
        }
    }
}
