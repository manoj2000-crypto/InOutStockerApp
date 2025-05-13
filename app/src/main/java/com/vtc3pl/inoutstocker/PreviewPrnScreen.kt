package com.vtc3pl.inoutstocker

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.graphics.Color.GREEN
import android.graphics.Color.RED
import android.net.Uri
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
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SourceLockedOrientationActivity")
@Composable
fun PreviewPrnScreen(
    scannedItems: List<Pair<String, Pair<Int, List<Int>>>>,
    username: String,
    depot: String,
    sharedViewModel: SharedViewModel,
    navController: NavHostController,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    val previewData = remember { mutableStateListOf<Triple<String, Int, List<Int>>>() }

    var totalBoxQty by remember { mutableIntStateOf(0) }
    var totalBoxWeight by remember { mutableDoubleStateOf(0.0) }
    var totalBagQty by remember { mutableIntStateOf(0) }
    var totalBagWeight by remember { mutableDoubleStateOf(0.0) }
    var pkgType by remember { mutableStateOf("") }

    // Additional states to store overall totals and loading status
    var totalQtyScanned by remember { mutableIntStateOf(0) }
    var totalWeight by remember { mutableDoubleStateOf(0.0) }
    var isLoading by remember { mutableStateOf(true) }
    var isDataFetched by remember { mutableStateOf(false) }

    LaunchedEffect(scannedItems) {
        scannedItems.forEach { (lrno, pair) ->
            val (totalPkgs, boxes) = pair
            val missingBoxes = (1..totalPkgs).filter { it !in boxes }
            previewData.add(Triple(lrno, totalPkgs, missingBoxes))
        }
    }
    LaunchedEffect(scannedItems) {
        // Clear any previous preview data and rebuild it
        previewData.clear()
        scannedItems.forEach { (lrno, pair) ->
            val (totalPkgs, boxes) = pair
            val missingBoxes = (1..totalPkgs).filter { it !in boxes }
            previewData.add(Triple(lrno, totalPkgs, missingBoxes))
        }

        // Call the function that fetches the weight and quantity info from the server.
        fetchWeightsFromServerForPrnOnly(scannedItems) { boxQty, boxWeight, bagQty, bagWeight, type ->
            totalBoxQty = boxQty
            totalBoxWeight = boxWeight
            totalBagQty = bagQty
            totalBagWeight = bagWeight
            pkgType = type

            // Compute overall totals.
            totalQtyScanned = totalBoxQty + totalBagQty
            totalWeight = totalBoxWeight + totalBagWeight

            isLoading = false
            isDataFetched = totalQtyScanned > 0 && totalWeight > 0.0
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Preview Screen") }, navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        })
    }) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {

            // Display the overall fetched information at the top
            Column(modifier = Modifier.padding(16.dp)) {
                if (isLoading) {
                    LottieAnimationView()
                } else {
                    Text("Total Quantity Scanned: $totalQtyScanned")
                    Text("Total Weight: $totalWeight")
//                    Text("Total Box Quantity: $totalBoxQty")
//                    Text("Total Box Weight: $totalBoxWeight")
//                    Text("Total Bag Quantity: $totalBagQty")
//                    Text("Total Bag Weight: $totalBagWeight")
//                    Text("Package Type: $pkgType")
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                items(previewData) { (lrno, totalPkgs, missingBoxes) ->
                    val cardColor = if (missingBoxes.isEmpty()) Color(GREEN) else Color(RED)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = cardColor),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("LRNO: $lrno", color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                "Total Packages: $totalPkgs",
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (missingBoxes.isEmpty()) "No missing boxes" else "Missing Boxes: ${
                                    missingBoxes.joinToString(
                                        ", "
                                    )
                                }", color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            sharedViewModel.setPrnOutwardScannedData(scannedItems)
                            val encodedTotalBoxWeight = Uri.encode(totalBoxWeight.toString())
                            val encodedTotalBagWeight = Uri.encode(totalBagWeight.toString())
                            navController.navigate(
                                "finalCalculationForPrnOutwardScreen/$username/$depot/$totalBoxQty/$encodedTotalBoxWeight/$totalBagQty/$encodedTotalBagWeight"
                            )
                        }, modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Final Calculation")
                    }
                }
            }
        }
    }
}

fun fetchWeightsFromServerForPrnOnly(
    scannedItems: List<Pair<String, Pair<Int, List<Int>>>>,
    onResult: (totalBoxQty: Int, totalBoxWeight: Double, totalBagQty: Int, totalBagWeight: Double, pkgType: String) -> Unit
) {
    Log.i(
        "fetchWeightsFromServerForPrnOnly",
        "Starting fetchWeightsFromServerForPrnOnly with scannedItems: $scannedItems"
    )
    val client = OkHttpClient()

    // Build the JSON request array using each scanned item's LRNO and scanned count.
    val jsonRequest = JSONArray().apply {
        scannedItems.forEach { (lrno, pair) ->
            val scannedCount = pair.second.size  // Calculate scanned count based on the list size
            put(JSONObject().apply {
                put("LRNO", lrno)
                put("ScannedItemCount", scannedCount)
            })
        }
    }
    Log.i("fetchWeightsFromServer", "Constructed JSON Request: $jsonRequest")

    val body = jsonRequest.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

    val request =
        Request.Builder().url("https://vtc3pl.com/fetch_total_weight_qty_inoutstocker_app.php")
            .post(body).build()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            Log.e("fetchWeightsFromServer", "Request failed: ${e.message}")
            onResult(0, 0.0, 0, 0.0, "")
        }

        override fun onResponse(call: Call, response: Response) {
            Log.i(
                "fetchWeightsFromServer", "onResponse triggered with status code: ${response.code}"
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