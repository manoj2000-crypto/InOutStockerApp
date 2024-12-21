package com.example.inoutstocker

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.inoutstocker.ui.theme.InOutStockerTheme

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
                System.exit(0)
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
                        onClick = { System.exit(0) }, colors = ButtonDefaults.buttonColors()
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

@Composable
fun HomeScreenContent(
    username: String,
    depot: String,
    onBoxAuditClick: (String, String) -> Unit,
    onBoxInwardClick: (String, String) -> Unit,
    onBoxOutwardClick: (String, String) -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Welcome, $username")
                    Text(
                        text = " | Depot: $depot",
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                Button(
                    onClick = { onBoxAuditClick(username, depot) },
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Text(text = "Box Audit")
                }

                Button(
                    onClick = { onBoxInwardClick(username, depot) },
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Text(text = "Box Inward")
                }

                Button(
                    onClick = { onBoxOutwardClick(username, depot) }
                ) {
                    Text(text = "Box Outward")
                }
            }
        }
    )
}

fun navigateToBoxAudit() {
    // Navigate to Box Audit Screen
}

fun navigateToBoxInward() {
    // Navigate to Box Inward Screen
}

fun navigateToBoxOutward() {
    // Navigate to Box Outward Screen
}