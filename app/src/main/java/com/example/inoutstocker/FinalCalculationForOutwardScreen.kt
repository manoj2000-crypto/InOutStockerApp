package com.example.inoutstocker

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinalCalculationForOutwardScreen(
    username: String,
    depot: String,
    loadingSheetNo: String,
    totalQty: Int,
    totalWeight: Double,
    groupCode: String,
    sharedViewModel: SharedViewModel,
    navController: NavController
) {
    val outwardScannedData = sharedViewModel.outwardScannedData
    var totalAmount by remember { mutableIntStateOf(0) }
    var deductionAmount by remember { mutableStateOf("") }
    var finalAmount by remember { mutableIntStateOf(totalAmount) }
    var hamaliVendorName by remember { mutableStateOf("") }
    var hamaliType by remember { mutableStateOf("") }

    // Fetch total weight and quantity from scanned data
    LaunchedEffect(outwardScannedData) {
        totalAmount = calculateTotalAmount(totalQty, totalWeight)
        finalAmount = totalAmount - (deductionAmount.toIntOrNull() ?: 0)
    }

    // Fetch hamali rates based on selected vendor and type
    LaunchedEffect(hamaliVendorName, hamaliType) {
        if (hamaliVendorName.isNotEmpty() && hamaliType.isNotEmpty()) {
            fetchHamaliRates(
                hamaliVendorName, depot
            ) { regular, crossing, regularBag, crossingBag ->
                val rate = when (hamaliType) {
                    "REGULAR" -> if (regular > 0) regular else regularBag
                    "CROSSING" -> if (crossing > 0) crossing else crossingBag
                    else -> 0.0
                }

                // Update totalAmount based on the rates
                totalAmount = calculateTotalAmount(totalQty, totalWeight, rate)
                finalAmount = totalAmount - (deductionAmount.toIntOrNull() ?: 0)
            }
        }
    }

    // Recalculate finalAmount whenever deductionAmount changes
    LaunchedEffect(deductionAmount) {
        finalAmount = totalAmount - (deductionAmount.toIntOrNull() ?: 0)
    }

    Log.d("FinalCalculationForOutwardScreen", "Received Group Code: $groupCode")

    Scaffold(topBar = {
        TopAppBar(title = { Text("Final Calculation") })
    }) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Text("Username: $username") }
            item { Text("Depot: $depot") }
            item { Text("Loading Sheet No: $loadingSheetNo") }
            item { Text("Total Quantity: $totalQty") }
            item { Text("Total Weight: $totalWeight") }
            item { Text("Outward Scanned Data: $outwardScannedData") }
            // Hamali Vendor Dropdown
            item {
                HamaliVendorDropdown(
                    selectedOption = hamaliVendorName,
                    onOptionSelected = { hamaliVendorName = it },
                    depot = depot
                )
            }

            // Hamali Type Dropdown
            item {
                DropdownMenuField(label = "Hamali Type",
                    options = listOf("REGULAR", "CROSSING"),
                    selectedOption = hamaliType,
                    onOptionSelected = { hamaliType = it })
            }

            // Display Amounts and Deduction
            item {
                TextField(
                    value = totalAmount.toString(),
                    onValueChange = {},
                    label = { Text("Total Amount") },
                    enabled = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                TextField(
                    value = deductionAmount,
                    onValueChange = { deductionAmount = it },
                    label = { Text("Deduction Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                TextField(
                    value = finalAmount.toString(),
                    onValueChange = {},
                    label = { Text("Final Amount") },
                    enabled = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Box(
                    modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = {
                            submitFinalCalculation(username = username,
                                depot = depot,
                                loadingSheetNo = loadingSheetNo,
                                totalQty = totalQty,
                                totalWeight = totalWeight,
                                groupCode = groupCode,
                                totalAmount = totalAmount,
                                deductionAmount = deductionAmount.toIntOrNull() ?: 0,
                                finalAmount = finalAmount,
                                hamaliVendorName = hamaliVendorName,
                                hamaliType = hamaliType,
                                outwardScannedData = outwardScannedData,
                                navController = navController,
                                sharedViewModel = sharedViewModel,
                                onFailure = { error ->
                                    Log.e("FinalCalculation", "Error: $error")
                                })
                        }, modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Submit")
                    }
                }
            }
        }
    }
}

fun submitFinalCalculation(
    username: String,
    depot: String,
    loadingSheetNo: String,
    totalQty: Int,
    totalWeight: Double,
    groupCode: String,
    totalAmount: Int,
    deductionAmount: Int,
    finalAmount: Int,
    hamaliVendorName: String,
    hamaliType: String,
    outwardScannedData: List<Pair<String, Pair<Int, List<Int>>>>,
    navController: NavController,
    sharedViewModel: SharedViewModel,
    onFailure: (String) -> Unit
) {
    val client = OkHttpClient()
    val url =
        "https://vtc3pl.com/final_outward_calculation.php"

    val jsonRequest = JSONObject().apply {
        put("username", username)
        put("depot", depot)
        put("loadingSheetNo", loadingSheetNo)
        put("totalQty", totalQty)
        put("totalWeight", totalWeight)
        put("groupCode", groupCode)
        put("totalAmount", totalAmount)
        put("deductionAmount", deductionAmount)
        put("finalAmount", finalAmount)
        put("hamaliVendorName", hamaliVendorName)
        put("hamaliType", hamaliType)

        val scannedDataArray = JSONArray()
        outwardScannedData.forEach { (lrno, pkgData) ->
            val (pkgNo, scannedBoxes) = pkgData
            val scannedItemJson = JSONObject().apply {
                put("LRNO", lrno)
                put("TotalPkgNo", pkgNo)
                put("ScannedBoxes", JSONArray(scannedBoxes))
            }
            scannedDataArray.put(scannedItemJson)
        }
        put("outwardScannedData", scannedDataArray)
    }

    val requestBody =
        jsonRequest.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

    val request = Request.Builder().url(url).post(requestBody).build()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            Log.e("FinalCalculation", "Request failed: ${e.message}")
            onFailure("Failed to connect to server")
        }

        override fun onResponse(call: Call, response: Response) {
            response.use {
                if (!it.isSuccessful) {
                    Log.e("FinalCalculation", "Server error: ${it.code}")
                    onFailure("Server error: ${it.code}")
                    return
                }
                val responseBody = it.body?.string()
                Log.d("FinalCalculation", "Response: $responseBody")

                try {
                    val jsonResponse = JSONObject(responseBody ?: "{}")
                    val success = jsonResponse.optBoolean("success", false)
                    val message = jsonResponse.optString("message", "Unknown response")

                    Handler(Looper.getMainLooper()).post {
                        if (success) {
                            Log.d("FinalCalculation", "Success: $message")

                            // Clear only Outward data before navigating back
                            sharedViewModel.clearOutwardData()

                            // Navigate to OutwardScreen only
                            navController.navigate("outwardScreen/$username/$depot") {
                                popUpTo("outwardScreen/$username/$depot") { inclusive = true }
                            }
                        } else {
                            onFailure(message)
                        }
                    }
                } catch (e: JSONException) {
                    Log.e("FinalCalculation", "JSON parsing error: ${e.message}")
                    Handler(Looper.getMainLooper()).post {
                        onFailure("Invalid server response")
                    }
                }
            }
        }
    })
}

// Calculate Total Amount
fun calculateTotalAmount(
    totalQty: Int, totalWeight: Double, rate: Double = 0.0
): Int {
    val totalPkgAmount = totalQty * rate
    return (totalPkgAmount + totalWeight).toInt()
}