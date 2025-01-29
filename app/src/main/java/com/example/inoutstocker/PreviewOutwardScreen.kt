package com.example.inoutstocker

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

@SuppressLint("SourceLockedOrientationActivity")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewOutwardScreen(
    sharedViewModel: SharedViewModel = viewModel(),
    username: String,
    depot: String,
    loadingSheetNo: String,
    onBack: () -> Unit
) {
    // Lock orientation to portrait
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

    // Set feature type to OUTWARD to fetch outward scanned data
    sharedViewModel.setFeatureType(SharedViewModel.FeatureType.OUTWARD)

    val outwardScannedData = sharedViewModel.scannedItems
    val outwardScannedLrnos = outwardScannedData.map { it.first }

    val drsLoadingSheets = loadingSheetNo.split(",").filter { it.startsWith("LSD") }
    val thcLoadingSheets = loadingSheetNo.split(",").filter { it.startsWith("LST") }

    var categorizedLrnos by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var excessLrnos by remember { mutableStateOf(emptyList<String>()) }
    var isLoading by remember { mutableStateOf(false) }
    var totalWeight by remember { mutableStateOf(0.0) }
    var totalQtyScanned by remember { mutableStateOf(0) }
    var isDataFetched by remember { mutableStateOf(false) }

    // Create NavController
    val navController = rememberNavController()

    LaunchedEffect(loadingSheetNo) {
        val drsResults =
            fetchLrnosFromServer(outwardScannedLrnos, drsLoadingSheets, "fetch_drs_lrnos.php")
        val thcResults =
            fetchLrnosFromServer(outwardScannedLrnos, thcLoadingSheets, "fetch_thc_lrnos.php")

        // Combine categorized results
        categorizedLrnos = drsResults.first + thcResults.first
        excessLrnos = drsResults.second + thcResults.second
    }

    Log.d("PreviewOutwardScreen", "Received LoadingSheetNo: $loadingSheetNo")

    Log.d("PreviewOutwardScreen", "Outward Scanned Data: $outwardScannedData")

    Scaffold(topBar = {
        TopAppBar(title = { Text("Preview Outward Scans") }, navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        })
    }) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text("Scanned LR Numbers", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))

            if (outwardScannedData.isEmpty()) {
                Text("No data available.", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn {
                    items(outwardScannedData) { (lrno, pkgNoPair) ->
                        val (pkgNo, scannedBoxes) = pkgNoPair
                        OutwardItem(lrno, pkgNo, scannedBoxes)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Display categorized LR numbers under each Loading Sheet
            categorizedLrnos.forEach { (loadingSheet, lrnos) ->
                Text("Loading Sheet: $loadingSheet", style = MaterialTheme.typography.headlineSmall)
                LazyColumn {
                    items(lrnos) { lrno -> Text("LRNO: $lrno") }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Display excess LR numbers that don't belong to any Loading Sheet
            if (excessLrnos.isNotEmpty()) {
                Text(
                    "Excess LR Numbers",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.Red
                )
                LazyColumn {
                    items(excessLrnos) { lrno -> Text("LRNO: $lrno", color = Color.Red) }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Get Data Button and Loader
            Button(
                onClick = {
                    isLoading = true
                    fetchWeightsFromServer(
                        categorizedLrnos, excessLrnos, outwardScannedData
                    ) { totalQty, weight -> // Pass scannedItems
                        totalWeight = weight
                        totalQtyScanned = totalQty // Store totalQty if needed
                        isLoading = false

                        // Enable the next button only if valid data is fetched
                        isDataFetched = totalQty > 0 && weight > 0.0
                    }

                }, modifier = Modifier.fillMaxWidth()
            ) {
                Text("Get Data")
            }

            if (isLoading) {
                Spacer(modifier = Modifier.height(8.dp))
                CircularProgressIndicator()
            }

            if (totalQtyScanned > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Total Quantity: $totalQtyScanned",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Green
                )
            }

            if (totalWeight > 0.0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Total Weight: $totalWeight",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Green
                )
            }

            // Show the button only if data is fetched
            if (isDataFetched) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        // Serialize outwardScannedData
                        val gson = Gson()
                        val outwardScannedDataJson = gson.toJson(outwardScannedData)

                        // Navigate to Final Calculation Screen
                        navController.navigate("finalCalculationOutwardScreen/$username/$depot/$loadingSheetNo/$totalQtyScanned/$totalWeight/$outwardScannedDataJson")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Proceed to Final Calculation")
                }
            }

        }
    }
}

// Function to fetch weight and quantity from the server
fun fetchWeightsFromServer(
    categorizedLrnos: Map<String, List<String>>,
    excessLrnos: List<String>,
    scannedItems: List<Pair<String, Pair<Int, List<Int>>>>, // Includes scanned counts
    onResult: (Int, Double) -> Unit // Returns TotalQty and TotalWeight
) {
    val client = OkHttpClient()

    val jsonRequest = JSONArray().apply {
        val scannedMap =
            scannedItems.associate { it.first to it.second.second.size } // LRNO -> Scanned Count

        // Add categorized LRNOs
        categorizedLrnos.values.flatten().forEach { lrno ->
            put(JSONObject().apply {
                put("LRNO", lrno)
                put("ScannedItemCount", scannedMap[lrno] ?: 0) // Default to 0 if not found
            })
        }

        // Add excess LRNOs
        excessLrnos.forEach { lrno ->
            put(JSONObject().apply {
                put("LRNO", lrno)
                put("ScannedItemCount", scannedMap[lrno] ?: 0)
            })
        }
    }

    val body = jsonRequest.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

    val request = Request.Builder()
        .url("https://vtc3pl.com/fetch_total_weight_qty_outward_inoutstocker_app.php").post(body)
        .build()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            Log.e("fetchWeightsFromServer", "Request failed: ${e.message}")
            onResult(0, 0.0) // Return default values on failure
        }

        override fun onResponse(call: Call, response: Response) {
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                val jsonResponse = JSONObject(responseBody ?: "{}")
                val totalQty = jsonResponse.optInt("TotalQty", 0)
                val totalWeight = jsonResponse.optDouble("TotalWeight", 0.0)
                onResult(totalQty, totalWeight)
            } else {
                Log.e("fetchWeightsFromServer", "Error: ${response.message}")
                onResult(0, 0.0)
            }
        }
    })
}

private val client = OkHttpClient()
suspend fun fetchLrnosFromServer(
    scannedLrnos: List<String>, loadingSheets: List<String>, endpoint: String
): Pair<Map<String, List<String>>, List<String>> {
    if (scannedLrnos.isEmpty() || loadingSheets.isEmpty()) return Pair(emptyMap(), emptyList())

    val url = "https://vtc3pl.com/$endpoint"
    val requestBody = FormBody.Builder().add("lrnos", scannedLrnos.joinToString(","))
        .add("loadingSheetNos", loadingSheets.joinToString(",")).build()

    val request = Request.Builder().url(url).post(requestBody).build()

    return try {
        withContext(Dispatchers.IO) {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e("PreviewOutwardScreen", "Server Error: ${response.code}")
                return@withContext Pair(emptyMap(), emptyList())
            }

            val responseBody = response.body?.string()
            if (responseBody.isNullOrEmpty()) {
                Log.e("PreviewOutwardScreen", "Empty response body")
                return@withContext Pair(emptyMap(), emptyList())
            }

            Log.d("PreviewOutwardScreen", "Response: $responseBody")

            // Parse JSON response
            val jsonObject = JSONObject(responseBody)
            val categorizedLrnos = mutableMapOf<String, List<String>>()
            val excessLrnos = mutableListOf<String>()

            // Extract categorized LR numbers
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val lrnosArray = jsonObject.getJSONArray(key)
                val lrnosList = List(lrnosArray.length()) { index -> lrnosArray.getString(index) }

                if (key == "excess") {
                    excessLrnos.addAll(lrnosList)
                } else {
                    categorizedLrnos[key] = lrnosList
                }
            }

            Pair(categorizedLrnos, excessLrnos)
        }
    } catch (e: Exception) {
        Log.e("PreviewOutwardScreen", "Error fetching LRNOs: ${e.message}")
        Pair(emptyMap(), emptyList())
    }
}


@Composable
fun OutwardItem(lrno: String, pkgNo: Int, scannedBoxes: List<Int>) {
    val missingItems = (1..pkgNo).filter { it !in scannedBoxes }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("LRNO: $lrno", style = MaterialTheme.typography.bodyLarge)
            Text("PkgNo: $pkgNo", style = MaterialTheme.typography.bodyMedium)
            Text("Scanned Count: ${scannedBoxes.size}", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Missing Items: ${
                    if (missingItems.isEmpty()) "None" else missingItems.joinToString(
                        ", "
                    )
                }", style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}