package com.example.inoutstocker

import android.net.Uri
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
        //Navigation Starts from login page
        composable("login") {
            LoginPage(navController)
        }

        //After Login this screen will shown to user with three buttons (Stock Audit, Inward, Outward)
        composable("homeScreen/{username}/{depot}") { backStackEntry ->
            val username = backStackEntry.arguments?.getString("username") ?: ""
            val depot = backStackEntry.arguments?.getString("depot") ?: ""
            HomeScreen(
                username = username,
                depot = depot,
                navigateToAuditScreen = { username, depot ->
                    navController.navigate("auditScreen/$username/$depot")
                },
                navigateToInwardScreen = { username, depot ->
                    navController.navigate("inwardScreen/$username/$depot")
                },
                navigateToOutwardScreen = { username, depot ->
                    navController.navigate("outwardScreen/$username/$depot")
                },
                navigateToPRNOutwardScreen = { username, depot ->
                    navController.navigate("prnOutwardScreen/$username/$depot")
                })
        }

        //Audit screen starts from here and this will open common scanning CameraScanView utility with separate data for each feature.
        composable("auditScreen/{username}/{depot}") { backStackEntry ->
            val username = backStackEntry.arguments?.getString("username") ?: ""
            val depot = backStackEntry.arguments?.getString("depot") ?: ""
            sharedViewModel.setFeatureType(SharedViewModel.FeatureType.AUDIT)
            AuditScreen(username = username, depot = depot, onPreview = {
                navController.navigate("previewAuditScreen/$username/$depot")
            }, sharedViewModel = sharedViewModel)
        }

        //Saving the Audited data on the server and user can able to go back and scan the remaining items also.
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
                })
        }

        //Inward screen this has two screens after this : inwardScreen->previewInwardScreen->finalCalculationScreen
        //Inward means the Arrival process for PRN / THC
        composable("inwardScreen/{username}/{depot}") { backStackEntry ->
            val username = backStackEntry.arguments?.getString("username") ?: ""
            val depot = backStackEntry.arguments?.getString("depot") ?: ""
            sharedViewModel.setFeatureType(SharedViewModel.FeatureType.INWARD)

            InwardScreen(navController, username, depot, onPreview = {
                navController.navigate("previewInwardScreen/$username/$depot")
            }, sharedViewModel = sharedViewModel)
        }

        //Here after scanning the items we are segregating the LRNO into where they in which category like : PRN / THC if not both then Excess LR
        composable("previewInwardScreen/{username}/{depot}") { backStackEntry ->
            val username = backStackEntry.arguments?.getString("username") ?: ""
            val depot = backStackEntry.arguments?.getString("depot") ?: ""

            PreviewInwardScreen(
                scannedItems = sharedViewModel.scannedItems,
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
                        }, "UTF-8"
                    )
                    navController.navigate("finalCalculationScreen/$prnOrThc/$prn/$username/$depot/$encodedScannedItems")
                },
                navigateToDRSPage = { prnOrThc, prn, username, depot, scannedItems ->
                    val encodedScannedItems = java.net.URLEncoder.encode(
                        scannedItems.joinToString(";") {
                            "${it.first},${it.second.first},${it.second.second.joinToString(",")}"
                        }, "UTF-8"
                    )
                    navController.navigate("finalDRSInward/$prnOrThc/$prn/$username/$depot/$encodedScannedItems")
                })
        }

        composable("finalDRSInward/{prnOrThc}/{prn}/{username}/{depot}/{scannedItems}") { backStackEntry ->
            val prnOrThc = backStackEntry.arguments?.getString("prnOrThc") ?: ""
            val drs = backStackEntry.arguments?.getString("prn") ?: ""
            val username = backStackEntry.arguments?.getString("username") ?: ""
            val depot = backStackEntry.arguments?.getString("depot") ?: ""
            val scannedItemsEncoded = backStackEntry.arguments?.getString("scannedItems") ?: ""
            val scannedItems = scannedItemsEncoded.split(";").map { data ->
                val parts = data.split(",")
                Pair(parts[0], Pair(parts[1].toInt(), parts.drop(2).map { it.toInt() }))
            }
            FinalDRSInward(
                prnOrThc = prnOrThc,
                drs = drs,
                username = username,
                depot = depot,
                scannedItems = scannedItems,
                onBack = { navController.popBackStack() })
        }

        //After preview we are calculating the Hamali amount based on the items scanned based on their Qty and Weight.
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

            FinalCalculationForInwardScreen(
                prnOrThc = prnOrThc,
                prn = prn,
                username = username,
                depot = depot,
                scannedItems = scannedItems,
                onBack = { navController.popBackStack() })
        }

        //Outward screen this has two screens after this :outwardScreen ->  outwardScanScreen->previewOutwardScreen
        //Outward means just like creating DRS or THC from loading sheet.
        composable("outwardScreen/{username}/{depot}") { backStackEntry ->
            val username = backStackEntry.arguments?.getString("username") ?: ""
            val depot = backStackEntry.arguments?.getString("depot") ?: ""

            // Clear only Outward-related data
//            sharedViewModel.clearOutwardData()
            sharedViewModel.setFeatureType(SharedViewModel.FeatureType.OUTWARD)

            OutwardScreen(navController, username, depot, sharedViewModel)
        }

        //This will show the scanning options with scan camera view.
        composable("outwardScanScreen/{username}/{depot}/{loadingSheetNo}/{groupCode}") { backStackEntry ->
            val username = backStackEntry.arguments?.getString("username") ?: ""
            val depot = backStackEntry.arguments?.getString("depot") ?: ""
            val loadingSheetNo = backStackEntry.arguments?.getString("loadingSheetNo") ?: ""
            val groupCode =
                backStackEntry.arguments?.getString("groupCode")?.let { Uri.decode(it) } ?: ""

            sharedViewModel.setFeatureType(SharedViewModel.FeatureType.OUTWARD)

            OutwardScanScreen(
                navController = navController,
                username = username,
                depot = depot,
                loadingSheetNo = loadingSheetNo,
                groupCode = groupCode,
                onPreview = {
                    // Encode groupCode to ensure it's safely passed
                    val encodedGroupCode = Uri.encode(groupCode)
                    navController.navigate("previewOutwardScreen/$username/$depot/$loadingSheetNo/$encodedGroupCode")
                },
                sharedViewModel = sharedViewModel
            )
        }

        //In this screen user will able to review its scanned item and then go for the Final calculation.
        composable("previewOutwardScreen/{username}/{depot}/{loadingSheetNo}/{groupCode}") { backStackEntry ->
            val username = backStackEntry.arguments?.getString("username") ?: ""
            val depot = backStackEntry.arguments?.getString("depot") ?: ""
            val loadingSheetNo = backStackEntry.arguments?.getString("loadingSheetNo") ?: ""
            val groupCode =
                backStackEntry.arguments?.getString("groupCode")?.let { Uri.decode(it) } ?: ""

            PreviewOutwardScreen(
                navController = navController,
                sharedViewModel = sharedViewModel,
                username = username,
                depot = depot,
                loadingSheetNo = loadingSheetNo,
                groupCode = groupCode,
                onBack = { navController.popBackStack() })
        }

        //If we get the 'service type = FTL' only then we dont need to scan after this page we will direct the user to the last page for final calculation.
        // New FTL Screen
        composable("ftlScreen/{username}/{depot}/{loadingSheetNos}/{groupCode}") { backStackEntry ->
            val username = backStackEntry.arguments?.getString("username") ?: ""
            val depot = backStackEntry.arguments?.getString("depot") ?: ""
            val loadingSheetNos = backStackEntry.arguments?.getString("loadingSheetNos") ?: ""
            val groupCode =
                backStackEntry.arguments?.getString("groupCode")?.let { Uri.decode(it) } ?: ""

            FtlScreen(
                navController = navController,
                username = username,
                depot = depot,
                loadingSheetNos = loadingSheetNos,
                groupCode = groupCode,
                sharedViewModel = sharedViewModel
            )
        }

        // Outward Final Calculation Screen
        composable("finalCalculationOutwardScreen/{username}/{depot}/{loadingSheetNo}/{totalBoxQty}/{totalBoxWeight}/{totalBagQty}/{totalBagWeight}/{groupCode}") { backStackEntry ->
            val username = backStackEntry.arguments?.getString("username") ?: ""
            val depot = backStackEntry.arguments?.getString("depot") ?: ""
            val loadingSheetNo = backStackEntry.arguments?.getString("loadingSheetNo") ?: ""
            val totalBoxQty = backStackEntry.arguments?.getString("totalBoxQty")?.toIntOrNull() ?: 0
            val totalBoxWeight = backStackEntry.arguments?.getString("totalBoxWeight")
                ?.let { Uri.decode(it).toDoubleOrNull() } ?: 0.0
            val totalBagQty = backStackEntry.arguments?.getString("totalBagQty")?.toIntOrNull() ?: 0
            val totalBagWeight = backStackEntry.arguments?.getString("totalBagWeight")
                ?.let { Uri.decode(it).toDoubleOrNull() } ?: 0.0
            val groupCode =
                backStackEntry.arguments?.getString("groupCode")?.let { Uri.decode(it) } ?: ""

            FinalCalculationForOutwardScreen(
                username = username,
                depot = depot,
                loadingSheetNo = loadingSheetNo,
                totalBoxQty = totalBoxQty,
                totalBoxWeight = totalBoxWeight,
                totalBagQty = totalBagQty,
                totalBagWeight = totalBagWeight,
                groupCode = groupCode,
                sharedViewModel = sharedViewModel,
                navController = navController
            )
        }

        composable("prnOutwardScreen/{username}/{depot}") { backStackEntry ->
            val username = backStackEntry.arguments?.getString("username") ?: ""
            val depot = backStackEntry.arguments?.getString("depot") ?: ""
            sharedViewModel.setFeatureType(SharedViewModel.FeatureType.PRN_OUTWARD)
            PrnOutwardScreen(username = username, depot = depot, onPreview = {
                navController.navigate("previewPrnScreen/$username/$depot") // MODIFY HERE USE PRN_PREVIEW_SCREEN
            }, sharedViewModel = sharedViewModel)
        }

        composable("previewPrnScreen/{username}/{depot}") { backStackEntry ->
            val username = backStackEntry.arguments?.getString("username") ?: ""
            val depot = backStackEntry.arguments?.getString("depot") ?: ""

            PreviewPrnScreen(
                scannedItems = sharedViewModel.scannedItems,
                username = username,
                depot = depot,
                onBack = { navController.popBackStack() },
                onBackToHome = {
                    sharedViewModel.clearScannedItems()
//                    navController.navigate("homeScreen/$username/$depot") {
//                        popUpTo("homeScreen/$username/$depot") { inclusive = true }
//                    }
                })
        }

    }
}