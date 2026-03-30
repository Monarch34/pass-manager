package com.passmanager.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.passmanager.domain.model.LockState
import com.passmanager.ui.lock.LockScreen
import com.passmanager.ui.onboarding.OnboardingScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val viewModel: NavigationViewModel = hiltViewModel()
    val lockState by viewModel.lockState.collectAsStateWithLifecycle()
    val navReady by viewModel.navReady.collectAsStateWithLifecycle()

    val ready = navReady as? NavReady.Ready
    if (ready == null) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
        return
    }

    val startDestination = when {
        !ready.isVaultSetup -> Screen.Onboarding.route
        lockState is LockState.Unlocked -> Screen.Main.route
        else -> Screen.Lock.route
    }

    // Single lock enforcement point — no screen-level onLocked callbacks needed.
    LaunchedEffect(lockState) {
        if (lockState !is LockState.Unlocked) {
            val currentRoute = navController.currentDestination?.route
            if (currentRoute == Screen.Main.route) {
                navController.navigate(Screen.Lock.route) {
                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onVaultCreated = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Lock.route) {
            LockScreen(
                onUnlocked = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Lock.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Main.route) {
            MainTabNavHost(
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
