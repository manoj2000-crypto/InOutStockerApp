package com.example.inoutstocker

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

// Data class to hold totals
data class Totals(
    val totalBoxQty: Int, val totalBoxWeight: Float, val totalBagQty: Int, val totalBagWeight: Float
)

@Composable
fun FtlScreen(
    navController: NavController,
    username: String,
    depot: String,
    loadingSheetNos: String,
    groupCode: String,
    sharedViewModel: SharedViewModel
) {
    var isProcessing by remember { mutableStateOf(false) }
    var localTotalBoxQty by remember { mutableIntStateOf(0) }
    var localTotalBoxWeight by remember { mutableFloatStateOf(0f) }
    var localTotalBagQty by remember { mutableIntStateOf(0) }
    var localTotalBagWeight by remember { mutableFloatStateOf(0f) }
    // Local state for scanned LRNO list (from SharedViewModel)
    var localScannedData by remember {
        mutableStateOf<List<Pair<String, Pair<Int, List<Int>>>>>(
            emptyList()
        )
    }
    var errorMessage by remember { mutableStateOf("") }

    // Fetch data on first composition or when loadingSheetNos changes.
    LaunchedEffect(loadingSheetNos) {
        isProcessing = true
        fetchFtlData(loadingSheetNos, sharedViewModel, onComplete = { totals ->
            // Update local totals from the fetched data.
            localTotalBoxQty = totals.totalBoxQty
            localTotalBoxWeight = totals.totalBoxWeight
            localTotalBagQty = totals.totalBagQty
            localTotalBagWeight = totals.totalBagWeight
            // Also update local scanned data from the SharedViewModel.
            localScannedData = sharedViewModel.outwardScannedData
            isProcessing = false
        }, onError = { error ->
            errorMessage = error
            isProcessing = false
        })
    }

    if (errorMessage.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { errorMessage = "" },
            title = { Text("Error") },
            text = { Text(errorMessage) },
            confirmButton = {
                Button(onClick = { errorMessage = "" }) {
                    Text("OK")
                }
            })
    }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .padding(paddingValues),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "FTL Processing", style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Loading Sheet Numbers: $loadingSheetNos",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(24.dp))
            if (isProcessing) {
                CircularProgressIndicator()
            } else {
                // Display fetched LRNO's.
                if (localScannedData.isNotEmpty()) {
                    // Display the LRNO list in a card with border and scrollable.
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .padding(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        // Use LazyColumn for a scrollable list inside the card.
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                        ) {
                            items(localScannedData) { data ->
                                Text(
                                    text = data.first,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        // Build the navigation route, encoding values if necessary.
                        val route =
                            "finalCalculationOutwardScreen/${username}/${depot}/${loadingSheetNos}/" + "${localTotalBoxQty}/${localTotalBoxWeight}/${localTotalBagQty}/${localTotalBagWeight}/${
                                Uri.encode(
                                    groupCode
                                )
                            }"
                        navController.navigate(route)
                    }) {
                        Text("FTL Final Calculation")
                    }
                } else {
                    Text("No LRNO data fetched.")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { navController.popBackStack() }) {
                Text(text = "Back")
            }
        }
    }
}

/**
 * Fetches FTL data from the server and updates the SharedViewModel (for scanned data),
 * then returns the totals via onComplete. If an error occurs, onError is called.
 */
fun fetchFtlData(
    loadingSheetNos: String,
    sharedViewModel: SharedViewModel,
    onComplete: (Totals) -> Unit,
    onError: (String) -> Unit
) {
    val url = "https://vtc3pl.com/fetch_ftl_data.php"
    val client = OkHttpClient()
    val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()

    // Prepare the JSON request body.
    val jsonBody = JSONObject().apply {
        put("loadingSheetNos", loadingSheetNos)
    }
    val requestBody = jsonBody.toString().toRequestBody(mediaType)

    // Build the request.
    val request = Request.Builder().url(url).post(requestBody).build()

    // Launch the network call on the IO dispatcher.
    CoroutineScope(Dispatchers.IO).launch {
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: ""
                    val jsonResponse = JSONObject(responseBody)
                    if (jsonResponse.getString("status") == "success") {
                        // Parse the scanned data array.
                        val dataArray = jsonResponse.getJSONArray("data")
                        val scannedData = mutableListOf<Pair<String, Pair<Int, List<Int>>>>()
                        for (i in 0 until dataArray.length()) {
                            val item = dataArray.getJSONObject(i)
                            val lrno = item.getString("lrno")
                            val pkgsNo = item.getInt("pkgsNo")
                            // Generate the box number list as [1, 2, ..., pkgsNo]
                            val boxNumbers = (1..pkgsNo).toList()
                            scannedData.add(lrno to (pkgsNo to boxNumbers))
                        }

                        // Parse the totals.
                        val totalsJson = jsonResponse.getJSONObject("totals")
                        val totalBoxQty = totalsJson.getInt("totalBoxQty")
                        val totalBoxWeight = totalsJson.getDouble("totalBoxWeight").toFloat()
                        val totalBagQty = totalsJson.getInt("totalBagQty")
                        val totalBagWeight = totalsJson.getDouble("totalBagWeight").toFloat()

                        // Update SharedViewModel on the Main thread.
                        withContext(Dispatchers.Main) {
                            sharedViewModel.setOutwardScannedData(scannedData)
                            Log.d("FTL", "FTL data fetched and stored successfully.")
                            onComplete(
                                Totals(
                                    totalBoxQty = totalBoxQty,
                                    totalBoxWeight = totalBoxWeight,
                                    totalBagQty = totalBagQty,
                                    totalBagWeight = totalBagWeight
                                )
                            )
                        }
                    } else {
                        Log.e("FTL", "Server error: ${jsonResponse.getString("message")}")
                        withContext(Dispatchers.Main) {
                            onError("Server error: ${jsonResponse.getString("message")}")
                        }
                    }
                } else {
                    Log.e("FTL", "Response not successful: ${response.code}")
                    withContext(Dispatchers.Main) {
                        onError("Response not successful: ${response.code}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("FTL", "Exception during fetch: ${e.message}")
            withContext(Dispatchers.Main) {
                onError("Exception during fetch: ${e.message}")
            }
        }
    }
}