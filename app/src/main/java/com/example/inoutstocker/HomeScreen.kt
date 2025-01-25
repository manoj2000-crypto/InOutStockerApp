package com.example.inoutstocker

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.inoutstocker.ui.theme.InOutStockerTheme
import kotlin.system.exitProcess

@Composable
fun HomeScreen(
    username: String, depot: String, navigateToAuditScreen: (String, String) -> Unit,
    navigateToInwardScreen: (String, String) -> Unit,
    navigateToOutwardScreen: (String, String) -> Unit
) {
    InOutStockerTheme {
        val showExitDialog = remember { mutableStateOf(false) }
        val backPressedOnce = remember { mutableStateOf(false) }

        // Intercept the back button press
        BackHandler {
            if (backPressedOnce.value) {
                exitProcess(0)
            } else {
                backPressedOnce.value = true
                showExitDialog.value = true
            }
        }

        if (showExitDialog.value) {
            AlertDialog(
                onDismissRequest = {
                    showExitDialog.value = false
                    backPressedOnce.value = false
                },
                title = {
                    Text(text = "Exit")
                },
                text = {
                    Text(text = "Do you want to exit.")
                },
                confirmButton = {
                    Button(onClick = { showExitDialog.value = false }) {
                        Text("No")
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { exitProcess(0) }, colors = ButtonDefaults.buttonColors()
                    ) {
                        Text("Yes")
                    }
                }
            )
        }

        HomeScreenContent(
            username = username,
            depot = depot,
            onBoxAuditClick = navigateToAuditScreen,
            onBoxInwardClick = navigateToInwardScreen,
            onBoxOutwardClick = navigateToOutwardScreen
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenContent(
    username: String,
    depot: String,
    onBoxAuditClick: (String, String) -> Unit,
    onBoxInwardClick: (String, String) -> Unit,
    onBoxOutwardClick: (String, String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "Welcome, $username | Depot: $depot")
                },
                actions = {
                    // You can add additional actions here if needed (e.g., settings icon)
                }
            )
        },

        modifier = Modifier.fillMaxSize(),
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // Center Section: Buttons
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Button(
                        onClick = { onBoxAuditClick(username, depot) },
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Text(text = "Stock Audit")
                    }

                    Button(
                        onClick = { onBoxInwardClick(username, depot) },
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Text(text = "Inward")
                    }

                    Button(
                        onClick = { onBoxOutwardClick(username, depot) }
                    ) {
                        Text(text = "Outward")
                    }
                }
            }
        }
    )
}