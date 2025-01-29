package com.example.inoutstocker

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

@Composable
fun AppNavigation(
    navController: NavHostController, sharedViewModel: SharedViewModel = viewModel()
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
            sharedViewModel.setFeatureType(SharedViewModel.FeatureType.AUDIT)
            AuditScreen(username = username, depot = depot, onPreview = {
                navController.navigate("previewAuditScreen/$username/$depot")
            }, sharedViewModel = sharedViewModel)
        }

        composable("inwardScreen/{username}/{depot}") { backStackEntry ->
            val username = backStackEntry.arguments?.getString("username") ?: ""
            val depot = backStackEntry.arguments?.getString("depot") ?: ""
            sharedViewModel.setFeatureType(SharedViewModel.FeatureType.INWARD)

            InwardScreen(navController, username, depot, onPreview = {
                navController.navigate("previewInwardScreen/$username/$depot")
            }, sharedViewModel = sharedViewModel)
        }

        composable("outwardScreen/{username}/{depot}") { backStackEntry ->
            val username = backStackEntry.arguments?.getString("username") ?: ""
            val depot = backStackEntry.arguments?.getString("depot") ?: ""
            sharedViewModel.setFeatureType(SharedViewModel.FeatureType.OUTWARD)

            OutwardScreen(navController, username, depot)
        }

        composable("outwardScanScreen/{username}/{depot}") { backStackEntry ->
            val username = backStackEntry.arguments?.getString("username") ?: ""
            val depot = backStackEntry.arguments?.getString("depot") ?: ""
            sharedViewModel.setFeatureType(SharedViewModel.FeatureType.OUTWARD)

            OutwardScanScreen(
                navController = navController,
                username = username,
                depot = depot,
                onPreview = {
                    navController.navigate("previewOutwardScreen/$username/$depot")
                },
                sharedViewModel = sharedViewModel
            )
        }


        composable("previewOutwardScreen/{username}/{depot}") { backStackEntry ->
            val username = backStackEntry.arguments?.getString("username") ?: ""
            val depot = backStackEntry.arguments?.getString("depot") ?: ""

            PreviewOutwardScreen(
                sharedViewModel = sharedViewModel,
                username = username,
                depot = depot,
                onBack = { navController.popBackStack() }
            )
        }

        composable("previewAuditScreen/{username}/{depot}") { backStackEntry ->
            val username = backStackEntry.arguments?.getString("username") ?: ""
            val depot = backStackEntry.arguments?.getString("depot") ?: ""

            PreviewAuditScreen(scannedItems = sharedViewModel.scannedItems,
                username = username,
                depot = depot,
                onBack = { navController.popBackStack() },
                onBackToHome = {
                    sharedViewModel.clearScannedItems()
                    navController.navigate("homeScreen/$username/$depot") {
                        popUpTo("homeScreen/$username/$depot") { inclusive = true }
                    }
                })
        }

        composable("previewInwardScreen/{username}/{depot}") { backStackEntry ->
            val username = backStackEntry.arguments?.getString("username") ?: ""
            val depot = backStackEntry.arguments?.getString("depot") ?: ""

            PreviewInwardScreen(scannedItems = sharedViewModel.scannedItems,
                username = username,
                depot = depot,
                onBack = { navController.popBackStack() },
                navigateToFinalCalculation = { prnOrThc, prn, username, depot, scannedItems ->
                    val encodedScannedItems = java.net.URLEncoder.encode(
                        scannedItems.joinToString(";") {
                            "${it.first},${it.second.first},${
                                it.second.second.joinToString(
                                    ","
                                )
                            }"
                        },
                        "UTF-8"
                    )
                    navController.navigate("finalCalculationScreen/$prnOrThc/$prn/$username/$depot/$encodedScannedItems")
                })
        }

        composable("finalCalculationScreen/{prnOrThc}/{prn}/{username}/{depot}/{scannedItems}") { backStackEntry ->
            val prnOrThc = backStackEntry.arguments?.getString("prnOrThc") ?: ""
            val prn = backStackEntry.arguments?.getString("prn") ?: ""
            val username = backStackEntry.arguments?.getString("username") ?: ""
            val depot = backStackEntry.arguments?.getString("depot") ?: ""
            val scannedItemsEncoded = backStackEntry.arguments?.getString("scannedItems") ?: ""

            val scannedItems = scannedItemsEncoded.split(";").map { data ->
                val parts = data.split(",")
                Pair(parts[0], Pair(parts[1].toInt(), parts.drop(2).map { it.toInt() }))
            }

            FinalCalculationForInwardScreen(prnOrThc = prnOrThc,
                prn = prn,
                username = username,
                depot = depot,
                scannedItems = scannedItems,
                onBack = { navController.popBackStack() })
        }

    }
}