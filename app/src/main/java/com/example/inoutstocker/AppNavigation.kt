package com.example.inoutstocker

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

@Composable
fun AppNavigation(
    navController: NavHostController,
    sharedViewModel: SharedViewModel = viewModel()
) {
    NavHost(navController = navController, startDestination = "login") {
        composable("login") {
            LoginPage(navController)
        }

        composable("homeScreen/{username}/{depot}") { backStackEntry ->
            val username = backStackEntry.arguments?.getString("username") ?: ""
            val depot = backStackEntry.arguments?.getString("depot") ?: ""
            HomeScreen(username = username,
                depot = depot,
                navigateToAuditScreen = { username, depot ->
                    navController.navigate("auditScreen/$username/$depot")
                },
                navigateToInwardScreen = { username, depot ->
                    navController.navigate("inwardScreen/$username/$depot")
                },
                navigateToOutwardScreen = { username, depot ->
                    navController.navigate("outwardScreen/$username/$depot")
                })
        }

        composable("auditScreen/{username}/{depot}") { backStackEntry ->
            val username = backStackEntry.arguments?.getString("username") ?: ""
            val depot = backStackEntry.arguments?.getString("depot") ?: ""
            AuditScreen(username = username, depot = depot, onPreview = {
                navController.navigate("previewAuditScreen/$username/$depot")
            }, sharedViewModel = sharedViewModel)
        }

        composable("inwardScreen/{username}/{depot}") { backStackEntry ->
            val username = backStackEntry.arguments?.getString("username") ?: ""
            val depot = backStackEntry.arguments?.getString("depot") ?: ""
            InwardScreen(username, depot)
        }

        composable("outwardScreen/{username}/{depot}") { backStackEntry ->
            val username = backStackEntry.arguments?.getString("username") ?: ""
            val depot = backStackEntry.arguments?.getString("depot") ?: ""
            OutwardScreen(username, depot)
        }

        composable("previewAuditScreen/{username}/{depot}") { backStackEntry ->
            val username = backStackEntry.arguments?.getString("username") ?: ""
            val depot = backStackEntry.arguments?.getString("depot") ?: ""

            PreviewAuditScreen(
                scannedItems = sharedViewModel.scannedItems,
                username = username,
                depot = depot,
                onBack = { navController.popBackStack() },
                onBackToHome = {
                    sharedViewModel.clearScannedItems()
                    navController.navigate("homeScreen/$username/$depot") {
                        popUpTo("homeScreen/$username/$depot") { inclusive = true }
                    }
                }
            )
        }

    }
}