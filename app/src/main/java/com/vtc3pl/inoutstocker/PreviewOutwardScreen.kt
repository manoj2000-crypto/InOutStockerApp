package com.vtc3pl.inoutstocker

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.net.Uri
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
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

@SuppressLint("SourceLockedOrientationActivity", "AutoboxingStateCreation")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewOutwardScreen(
    navController: NavHostController,
    sharedViewModel: SharedViewModel = viewModel(),
    username: String,
    depot: String,
    loadingSheetNo: String,
    groupCode: String,
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

    // Compute excessLrType dynamically based on available loading sheets
    val computedExcessLrType = when {
        drsLoadingSheets.isNotEmpty() && thcLoadingSheets.isNotEmpty() -> "MANIFEST"
        drsLoadingSheets.isNotEmpty() -> "DRS"
        thcLoadingSheets.isNotEmpty() -> "THC"
        else -> "NONE" // fallback if neither loading sheet type is found
    }

    var categorizedLrnos by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var excessLrnos by remember { mutableStateOf(emptyList<String>()) }
    var isLoading by remember { mutableStateOf(false) }
    var totalWeight by remember { mutableStateOf(0.0) }
    var totalQtyScanned by remember { mutableStateOf(0) }
    var isDataFetched by remember { mutableStateOf(false) }
    var isCategorizing by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    var totalBoxQty by remember { mutableStateOf(0) }
    var totalBoxWeight by remember { mutableStateOf(0.0) }
    var totalBagQty by remember { mutableStateOf(0) }
    var totalBagWeight by remember { mutableStateOf(0.0) }

    // Track excess LR numbers that have already been processed
    val processedExcessLrnos = remember { mutableStateListOf<String>() }

    var excessStatuses by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var disallowGetData by remember { mutableStateOf(false) }


    LaunchedEffect(loadingSheetNo) {
        when {
            // Both DRS and THC sheets are available
            drsLoadingSheets.isNotEmpty() && thcLoadingSheets.isNotEmpty() -> {
                val drsResults = fetchLrnosFromServer(
                    outwardScannedLrnos,
                    drsLoadingSheets,
                    "fetch_drs_lrnos.php"
                )
                categorizedLrnos = drsResults.first
                var remainingLrnos = drsResults.second  // LRNO not found in DRS

                if (remainingLrnos.isNotEmpty()) {
                    val thcResults = fetchLrnosFromServer(
                        remainingLrnos,
                        thcLoadingSheets,
                        "fetch_thc_lrnos.php"
                    )
                    categorizedLrnos = categorizedLrnos + thcResults.first
                    excessLrnos = thcResults.second
                } else {
                    excessLrnos = emptyList()
                }
            }
            // Only DRS sheets available
            drsLoadingSheets.isNotEmpty() -> {
                val drsResults = fetchLrnosFromServer(
                    outwardScannedLrnos,
                    drsLoadingSheets,
                    "fetch_drs_lrnos.php"
                )
                categorizedLrnos = drsResults.first
                excessLrnos = drsResults.second
            }
            // Only THC sheets available
            thcLoadingSheets.isNotEmpty() -> {
                val thcResults = fetchLrnosFromServer(
                    outwardScannedLrnos,
                    thcLoadingSheets,
                    "fetch_thc_lrnos.php"
                )
                categorizedLrnos = thcResults.first
                excessLrnos = thcResults.second
            }
            // No valid loading sheets provided
            else -> {
                categorizedLrnos = emptyMap()
                excessLrnos = emptyList()
            }
        }

        // Update the SharedViewModel with the categorized mapping
        sharedViewModel.updateCategorizedLrnos(categorizedLrnos)
        Log.d("CategorizedMapping", "Stored mapping ::::::::=::::::: $categorizedLrnos")

        // Indicate that categorization is complete
        isCategorizing = false
    }

    // Automatically send Excess LR data when new excess LRs are detected
    LaunchedEffect(excessLrnos) {
        // Filter out those already processed
        val filteredExcessLrnos = excessLrnos.filterNot { it in processedExcessLrnos }

        if (filteredExcessLrnos.isNotEmpty()) {
            // Build the list of ExcessLRInfo objects using the scanned data from outwardScannedData
            val excessLRInfoList = filteredExcessLrnos.mapNotNull { lr ->
                outwardScannedData.find { it.first == lr }?.let { scannedRecord ->
                    val totalPkg = scannedRecord.second.first
                    val scannedBoxes = scannedRecord.second.second
                    val scannedCount = scannedBoxes.size
                    val totalDiff = totalPkg - scannedCount
                    val expectedBoxes = (1..totalPkg).toList()
                    val missingBoxes = expectedBoxes.filter { it !in scannedBoxes }
                    val missingItemsStr = missingBoxes.joinToString(",").ifEmpty { "" }
                    ExcessLRInfo(lr, scannedCount, totalDiff, missingItemsStr)
                }
            }

            if (excessLRInfoList.isNotEmpty()) {
                sendExcessLRData(
                    excessLRInfoList = excessLRInfoList,
                    username = username,
                    depot = depot,
                    excessLrType = computedExcessLrType,
                    excessFeatureType = "OUTWARD",
                    onError = { error ->
                        Log.e("PreviewOutwardScreen", "Error sending excess LR data: $error")
                    },
                    onSuccess = {
                        // Mark these LR numbers as processed
                        filteredExcessLrnos.forEach { lr ->
                            processedExcessLrnos.add(lr)
                            Log.i("PreviewOutwardScreen", "Processed Excess LR: $lr")
                        }
                    }
                )
            }
        }
    }


    LaunchedEffect(excessLrnos) {
        if (excessLrnos.isNotEmpty()) {
            fetchExcessStatuses(excessLrnos) { statuses ->
                excessStatuses = statuses
                // If any LRNO’s status is not in allowed set [0,6,8,9], then disable Get Data
                disallowGetData = statuses.values.any { it !in listOf(0, 6, 8, 9) }
            }
        }
    }

    Log.d("PreviewOutwardScreen", "Received Group Code: $groupCode")

    Log.d("PreviewOutwardScreen", "Received LoadingSheetNo: $loadingSheetNo")

    Log.d("PreviewOutwardScreen", "Outward Scanned Data: $outwardScannedData")

    Scaffold(topBar = {
        TopAppBar(title = { Text("Preview Outward Scans") }, navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        })
    }) { paddingValues ->
        if (isCategorizing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                LottieAnimationView()
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp) // This ensures some space between items
            ) {
                item {
                    Text("Scanned LR Numbers", style = MaterialTheme.typography.headlineSmall)
                }
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                item {
                    if (outwardScannedData.isEmpty()) {
                        Text("No data available.", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        outwardScannedData.forEach { (lrno, pkgNoPair) ->
                            val (pkgNo, scannedBoxes) = pkgNoPair
                            OutwardItem(lrno, pkgNo, scannedBoxes)
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Display categorized LR numbers under each Loading Sheet
                categorizedLrnos.forEach { (loadingSheet, lrnos) ->
                    item {
                        Text(
                            "Loading Sheet: $loadingSheet",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        lrnos.forEach { lrno ->
                            Text("LRNO: $lrno")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // Display excess LR numbers that don't belong to any Loading Sheet
                if (excessLrnos.isNotEmpty()) {
                    item {
                        Text(
                            "Excess LR Numbers",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color(0xFFFFA500)
                        )
                        excessLrnos.forEach { lrno ->
                            // Check the fetched status; if not allowed, display in blue
                            val status = excessStatuses[lrno] ?: 0
                            val displayColor =
                                if (status !in listOf(0, 6, 8, 9)) Color.Blue else Color(0xFFFFA500)
                            Text("LRNO: $lrno", color = displayColor)
                        }
                    }
                }

                // Get Data Button and Loader
                item {
                    if (disallowGetData) {
                        Text(
                            "BLUE COLOR LR PLEASE ARRIVAL THEM FIRST AND THEN START THE PROCESS FOR OUTWARD",
                            color = Color.Red,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    } else {

                        if (isLoading) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                LottieAnimationView()
                            }
                        } else {
                            Button(
                                onClick = {
                                    isLoading = true
                                    fetchWeightsFromServer(
                                        categorizedLrnos, excessLrnos, outwardScannedData
                                    ) { boxQty, boxWeight, bagQty, bagWeight, pkgType ->
                                        totalBoxQty = boxQty
                                        totalBoxWeight = boxWeight
                                        totalBagQty = bagQty
                                        totalBagWeight = bagWeight

                                        totalQtyScanned = totalBoxQty + totalBagQty
                                        totalWeight = totalBoxWeight + totalBagWeight
                                        isLoading = false
                                        isDataFetched = totalQtyScanned > 0 && totalWeight > 0.0
                                    }
                                }, modifier = Modifier.fillMaxWidth(),
                                enabled = !isLoading && !isDataFetched
                            ) {
                                Text("Get Data")
                            }
                        }
                    }
                }

                if (totalQtyScanned > 0) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Total Quantity: $totalQtyScanned",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.Green
                        )
                    }
                }

                if (totalWeight > 0.0) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Total Weight: $totalWeight",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.Green
                        )
                    }
                }

                // Show the button only if data is fetched
                if (isDataFetched) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                sharedViewModel.setOutwardScannedData(outwardScannedData)
                                val encodedTotalBoxWeight = Uri.encode(totalBoxWeight.toString())
                                val encodedTotalBagWeight = Uri.encode(totalBagWeight.toString())
                                val encodedGroupCode = Uri.encode(groupCode)
                                navController.navigate("finalCalculationOutwardScreen/$username/$depot/$loadingSheetNo/$totalBoxQty/$encodedTotalBoxWeight/$totalBagQty/$encodedTotalBagWeight/$encodedGroupCode")
                            }, modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Proceed to Final Calculation")
                        }
                    }
                }
            }

            // Scroll to the last item when data is fetched:
            LaunchedEffect(isDataFetched) {
                if (isDataFetched) {
                    listState.animateScrollToItem(index = listState.layoutInfo.totalItemsCount - 1)
                }
            }
        }
    }
}

fun fetchExcessStatuses(excessLrnos: List<String>, onResult: (Map<String, Int>) -> Unit) {
    val client = OkHttpClient()
    val url = "https://vtc3pl.com/check_excess_status.php"
    val formBody = FormBody.Builder()
        .add("excessLRs", excessLrnos.joinToString(","))
        .build()
    val request = Request.Builder().url(url).post(formBody).build()
    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            Log.e("fetchExcessStatuses", "Request failed: ${e.message}")
            onResult(emptyMap())
        }

        override fun onResponse(call: Call, response: Response) {
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: "{}"
                try {
                    val jsonObj = JSONObject(responseBody)
                    val statuses = mutableMapOf<String, Int>()
                    val keys = jsonObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        statuses[key] = jsonObj.optInt(key, 0)
                    }
                    onResult(statuses)
                } catch (e: Exception) {
                    Log.e("fetchExcessStatuses", "JSON parsing error: ${e.message}")
                    onResult(emptyMap())
                }
            } else {
                Log.e("fetchExcessStatuses", "Error: ${response.message}")
                onResult(emptyMap())
            }
        }
    })
}

// Function to fetch weight and quantity from the server
fun fetchWeightsFromServer(
    categorizedLrnos: Map<String, List<String>>,
    excessLrnos: List<String>,
    scannedItems: List<Pair<String, Pair<Int, List<Int>>>>, // Includes scanned counts
    onResult: (totalBoxQty: Int, totalBoxWeight: Double, totalBagQty: Int, totalBagWeight: Double, pkgType: String) -> Unit // Returns TotalQty and TotalWeight
) {
    Log.i(
        "fetchWeightsFromServer",
        "Starting fetchWeightsFromServer with scannedItems: $scannedItems"
    )
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
    Log.i("fetchWeightsFromServer", "Constructed JSON Request: $jsonRequest")

    val body = jsonRequest.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

//    val request = Request.Builder()
//        .url("https://vtc3pl.com/fetch_total_weight_qty_outward_inoutstocker_app.php").post(body)
//        .build()

    val request = Request.Builder()
        .url("https://vtc3pl.com/fetch_total_weight_qty_inoutstocker_app.php").post(body)
        .build()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            Log.e("fetchWeightsFromServer", "Request failed: ${e.message}")
            onResult(0, 0.0, 0, 0.0, "")
        }

        override fun onResponse(call: Call, response: Response) {
            Log.i(
                "fetchWeightsFromServer",
                "onResponse triggered with status code: ${response.code}"
            )

            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                Log.i("fetchWeightsFromServer", "Response body received: $responseBody")

                val jsonResponse = JSONObject(responseBody ?: "{}")
                val totalBoxQty = jsonResponse.optInt("TotalBoxQty", 0)
                val totalBoxWeight = jsonResponse.optDouble("TotalBoxWeight", 0.0)
                val totalBagQty = jsonResponse.optInt("TotalBagQty", 0)
                val totalBagWeight = jsonResponse.optDouble("TotalBagWeight", 0.0)
                val pkgType = jsonResponse.optString("PkgType", "")
                Log.i(
                    "fetchWeightsFromServer",
                    "Parsed totals - BoxQty: $totalBoxQty, BoxWeight: $totalBoxWeight, BagQty: $totalBagQty, BagWeight: $totalBagWeight, pkgType: $pkgType"
                )
                onResult(totalBoxQty, totalBoxWeight, totalBagQty, totalBagWeight, pkgType)
            } else {
                Log.e("fetchWeightsFromServer", "Error: ${response.message}")
                onResult(0, 0.0, 0, 0.0, "")
            }
        }
    })
}

private val client = OkHttpClient()
suspend fun fetchLrnosFromServer(
    scannedLrnos: List<String>,
    loadingSheets: List<String>,
    endpoint: String
): Pair<Map<String, List<String>>, List<String>> {
    // Log input validation
    if (scannedLrnos.isEmpty() || loadingSheets.isEmpty()) {
        Log.w("PreviewOutwardScreen", "fetchLrnosFromServer: Empty scannedLrnos or loadingSheets")
        return Pair(emptyMap(), emptyList())
    }

    // Construct URL and request parameters
    val url = "https://vtc3pl.com/$endpoint"
    Log.d("PreviewOutwardScreen", "fetchLrnosFromServer: URL -> $url")
    Log.d(
        "PreviewOutwardScreen",
        "fetchLrnosFromServer: Parameters -> lrnos: ${scannedLrnos.joinToString(",")}, loadingSheetNos: ${
            loadingSheets.joinToString(
                ","
            )
        }"
    )

    val requestBody = FormBody.Builder()
        .add("lrnos", scannedLrnos.joinToString(","))
        .add("loadingSheetNos", loadingSheets.joinToString(","))
        .build()

    val request = Request.Builder()
        .url(url)
        .post(requestBody)
        .build()

    return try {
        withContext(Dispatchers.IO) {
            Log.d("PreviewOutwardScreen", "fetchLrnosFromServer: Initiating network call...")
            val response = client.newCall(request).execute()
            Log.d("PreviewOutwardScreen", "fetchLrnosFromServer: Response code: ${response.code}")

            if (!response.isSuccessful) {
                Log.e(
                    "PreviewOutwardScreen",
                    "fetchLrnosFromServer: Server Error: ${response.code}"
                )
                return@withContext Pair(emptyMap(), emptyList())
            }

            val responseBody = response.body?.string()
            if (responseBody.isNullOrEmpty()) {
                Log.e("PreviewOutwardScreen", "fetchLrnosFromServer: Empty response body")
                return@withContext Pair(emptyMap(), emptyList())
            }

            Log.d("PreviewOutwardScreen", "fetchLrnosFromServer: Response body -> $responseBody")

            // Parse JSON response with additional logging and type-checking for "excess"
            try {
                val jsonObject = JSONObject(responseBody)
                val categorizedLrnos = mutableMapOf<String, List<String>>()
                val excessLrnos = mutableListOf<String>()
                val categorizedSet = mutableSetOf<String>()

                val keys = jsonObject.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    Log.d("PreviewOutwardScreen", "fetchLrnosFromServer: Processing key: $key")
                    if (key == "excess") {
                        // Check if "excess" is a JSONArray or JSONObject
                        val excessValue = jsonObject.get("excess")
                        if (excessValue is JSONArray) {
                            val lrnosList =
                                List(excessValue.length()) { index -> excessValue.getString(index) }
                            excessLrnos.addAll(lrnosList)
                            Log.d(
                                "PreviewOutwardScreen",
                                "fetchLrnosFromServer: Excess LRNOs (JSONArray): $lrnosList"
                            )
                        } else if (excessValue is JSONObject) {
                            val lrnosList = mutableListOf<String>()
                            val excessKeys = excessValue.keys()
                            while (excessKeys.hasNext()) {
                                val exKey = excessKeys.next()
                                lrnosList.add(excessValue.getString(exKey))
                            }
                            excessLrnos.addAll(lrnosList)
                            Log.d(
                                "PreviewOutwardScreen",
                                "fetchLrnosFromServer: Excess LRNOs (JSONObject): $lrnosList"
                            )
                        } else {
                            Log.e(
                                "PreviewOutwardScreen",
                                "fetchLrnosFromServer: Unexpected type for 'excess': ${excessValue.javaClass}"
                            )
                        }
                    } else {
                        // Process other keys as JSONArray
                        val lrnosArray = jsonObject.getJSONArray(key)
                        val lrnosList =
                            List(lrnosArray.length()) { index -> lrnosArray.getString(index) }
                        categorizedLrnos[key] = lrnosList
                        categorizedSet.addAll(lrnosList)
                        Log.d(
                            "PreviewOutwardScreen",
                            "fetchLrnosFromServer: Categorized LRNOs for key '$key': $lrnosList"
                        )
                    }
                }

                // Filter out duplicate excess LRNOs that appear in categorized data
                val uniqueExcessLrnos = excessLrnos.filterNot { it in categorizedSet }.toSet()
                Log.d(
                    "PreviewOutwardScreen",
                    "fetchLrnosFromServer: Unique excess LRNOs: $uniqueExcessLrnos"
                )

                Pair(categorizedLrnos, uniqueExcessLrnos.toList())
            } catch (jsonEx: Exception) {
                Log.e(
                    "PreviewOutwardScreen",
                    "fetchLrnosFromServer: JSON Parsing error: ${jsonEx.message}"
                )
                Pair(emptyMap(), emptyList())
            }
        }
    } catch (e: Exception) {
        Log.e(
            "PreviewOutwardScreen",
            "fetchLrnosFromServer: Exception during network call: ${e.message}"
        )
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