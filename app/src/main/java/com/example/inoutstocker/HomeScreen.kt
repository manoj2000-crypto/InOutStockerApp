package com.example.inoutstocker

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.inoutstocker.ui.theme.InOutStockerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlin.system.exitProcess

@Composable
fun HomeScreen(
    username: String,
    depot: String,
    navigateToAuditScreen: (String, String) -> Unit,
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
            AlertDialog(onDismissRequest = {
                showExitDialog.value = false
                backPressedOnce.value = false
            }, title = {
                Text(text = "Exit")
            }, text = {
                Text(text = "Do you want to exit.")
            }, confirmButton = {
                Button(onClick = { showExitDialog.value = false }) {
                    Text("No")
                }
            }, dismissButton = {
                Button(
                    onClick = { exitProcess(0) }, colors = ButtonDefaults.buttonColors()
                ) {
                    Text("Yes")
                }
            })
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
            TopAppBar(title = {
                Text(text = "Welcome, $username | Depot: $depot")
            }, actions = {
                // You can add additional actions here if needed (e.g., settings icon)
            })
        },

        modifier = Modifier.fillMaxSize(), content = { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // DashboardCounts appears below the top bar and above the buttons.
                DashboardCounts(depot = depot)

                // Then add spacing if needed.
                Spacer(modifier = Modifier.padding(vertical = 16.dp))

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
                        onClick = { onBoxOutwardClick(username, depot) }) {
                        Text(text = "Outward")
                    }
                }
            }
        })
}

@Composable
fun DashboardCounts(depot: String) {
    var prnCount by remember { mutableStateOf<Int?>(null) }
    var thcCount by remember { mutableStateOf<Int?>(null) }
    var vehiclePlacementCount by remember { mutableStateOf<Int?>(null) }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

    // Fetch the dashboard data whenever depot changes
    LaunchedEffect(depot) {
        val client = OkHttpClient()
        val formBody = FormBody.Builder().add("depot", depot).build()
        val request =
            Request.Builder().url("https://vtc3pl.com/inoutstockerapp_dashboard.php").post(formBody)
                .build()

        try {
            val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
            if (response.isSuccessful) {
                val responseString = response.body?.string() ?: ""
                val json = JSONObject(responseString)
                prnCount = json.optInt("prnCount")
                thcCount = json.optInt("thcCount")
                vehiclePlacementCount = json.optInt("vehiclePlacementCount")
            } else {
                errorMessage = "Error: ${response.code}"
            }
        } catch (e: Exception) {
            errorMessage = e.message ?: "Unknown error"
        }
        isLoading = false
    }

    if (isLoading) {
        CircularProgressIndicator(modifier = Modifier.padding(16.dp))
    } else if (errorMessage.isNotEmpty()) {
        Text(
            text = errorMessage,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(16.dp)
        )
    } else {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(8.dp)
                ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Dashboard", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Inward PRN Count: ${prnCount ?: 0}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Inward THC Count: ${thcCount ?: 0}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Vehicle Placement Count: ${vehiclePlacementCount ?: 0}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}