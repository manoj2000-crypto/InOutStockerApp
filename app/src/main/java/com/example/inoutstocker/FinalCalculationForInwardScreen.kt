package com.example.inoutstocker

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinalCalculationForInwardScreen(
    prnOrThc: String,
    prn: String,
    username: String,
    depot: String,
    scannedItems: List<Pair<String, Pair<Int, List<Int>>>>,
    onBack: () -> Unit
) {
    val decodedPrnOrThc = java.net.URLDecoder.decode(prnOrThc, "UTF-8")
    val decodedPrn = java.net.URLDecoder.decode(prn, "UTF-8")

    var totalQty by remember { mutableStateOf(0) }
    var totalWeight by remember { mutableStateOf(0.0) }

    // Calculate TotalBagQty and TotalBoxQty from scannedItems
    LaunchedEffect(scannedItems) {
        totalQty = scannedItems.flatMap { it.second.second }.size
        fetchWeightsFromServer(scannedItems) { weight ->
            totalWeight = weight
        }
    }

    Log.d("FinalCalculationScreen", "PRN/THC: $decodedPrnOrThc, PRN: $decodedPrn")
    Log.d("FinalCalculationScreen", "Username: $username, Depot: $depot")
    scannedItems.forEach { item ->
        Log.d(
            "FinalCalculationScreen",
            "LRNO: ${item.first}, PkgsNo: ${item.second.first}, BoxNos: ${item.second.second}"
        )
    }

    var hamaliVendorName by remember { mutableStateOf("") }
    var hamaliType by remember { mutableStateOf("") }
    var totalAmount by remember { mutableStateOf(1000) }
    var deductionAmount by remember { mutableStateOf("") }

    var finalAmount by remember { mutableStateOf(totalAmount) }
    var freight by remember { mutableStateOf("") }
    var godownKeeperName by remember { mutableStateOf("") }
    var closingKm by remember { mutableStateOf("") }

    var paymentMode by remember { mutableStateOf("") }
    var transactionId by remember { mutableStateOf("") }
    var unloadingHamaliReceived by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }

    // Update Final Amount whenever Deduction Amount changes
    LaunchedEffect(deductionAmount) {
        finalAmount = totalAmount - (deductionAmount.toIntOrNull() ?: 0)
    }

    Log.d("FinalCalculationScreen", "PRN/THC: $decodedPrnOrThc")
    Log.d("FinalCalculationScreen", "Username: $username, Depot: $depot")


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

                // Calculate Total Amount
                val boxQty = totalQty
                val weightInTons = totalWeight / 1000 // Convert weight to tons
                val totalBoxCost = boxQty * rate
                val totalBagCost = weightInTons * rate
                totalAmount = (totalBoxCost + totalBagCost).toInt()

                // Update Final Amount based on deductions
                finalAmount = totalAmount - (deductionAmount.toIntOrNull() ?: 0)

                Log.d(
                    "HamaliCalculation",
                    "Rate: $rate, Total Amount: $totalAmount, Final Amount: $finalAmount"
                )
            }
        }
    }

    LaunchedEffect(deductionAmount) {
        finalAmount = totalAmount - (deductionAmount.toIntOrNull() ?: 0)
        Log.d("DeductionChange", "Deduction: $deductionAmount, Final Amount: $finalAmount")
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Final Calculation") })
    }, content = { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Total Quantity: $totalQty")
            }
            item {
                Text("Total Weight: $totalWeight")
            }

            item {
                HamaliVendorDropdown(
                    selectedOption = hamaliVendorName,
                    onOptionSelected = { hamaliVendorName = it },
                    depot = depot
                )
            }
            Log.d("FinalCalculationScreen", "Hamali Vendor Name: $hamaliVendorName")

            item {
                DropdownMenuField(label = "Hamali Type",
                    options = listOf("REGULAR", "CROSSING"),
                    selectedOption = hamaliType,
                    onOptionSelected = { hamaliType = it })
            }
            Log.d("FinalCalculationScreen", "Hamali Type: $hamaliType")

            item {
                TextField(value = totalAmount.toString(),
                    onValueChange = {},
                    label = { Text("Total Amount") },
                    enabled = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Log.d("FinalCalculationScreen", "Total Amount: $totalAmount")

            item {
                TextField(value = deductionAmount,
                    onValueChange = { deductionAmount = it },
                    label = { Text("Deduction Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Log.d("FinalCalculationScreen", "Deduction Amount: $deductionAmount")

            item {
                TextField(value = finalAmount.toString(),
                    onValueChange = {},
                    label = { Text("Final Amount") },
                    enabled = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Log.d("FinalCalculationScreen", "Final Amount: $finalAmount")

            // Conditional Fields
            if (decodedPrnOrThc == "PRN") {
                item {
                    TextField(value = freight,
                        onValueChange = { freight = it },
                        label = { Text("Freight") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Log.d("FinalCalculationScreen", "Freight: $freight")

                item {
                    TextField(value = godownKeeperName,
                        onValueChange = { godownKeeperName = it },
                        label = { Text("Godown Keeper Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Log.d("FinalCalculationScreen", "Godown Keeper Name: $godownKeeperName")

            } else if (decodedPrnOrThc == "THC") {
                item {
                    TextField(value = closingKm,
                        onValueChange = { closingKm = it },
                        label = { Text("Closing KM") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Log.d("FinalCalculationScreen", "Closing KM: $closingKm")

                item {
                    DropdownMenuField(label = "Payment Mode",
                        options = listOf("BANK", "CASH"),
                        selectedOption = paymentMode,
                        onOptionSelected = { paymentMode = it })
                }
                Log.d("FinalCalculationScreen", "Payment Mode: $paymentMode")

                item {
                    TextField(value = transactionId,
                        onValueChange = { transactionId = it },
                        label = { Text("Transaction ID") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Log.d("FinalCalculationScreen", "Transaction ID: $transactionId")

                item {
                    TextField(
                        value = unloadingHamaliReceived,
                        onValueChange = { unloadingHamaliReceived = it },
                        label = { Text("Unloading Hamali Received From Driver") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Log.d(
                    "FinalCalculationScreen", "Unloading Hamali Received: $unloadingHamaliReceived"
                )
            }

            item {
                TextField(value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Button(
                    onClick = {
                        val processedItems = processScannedItems(scannedItems, reason)

                        submitDataToServer(prnOrThc = prnOrThc,
                            prn = prn,
                            username = username,
                            depot = depot,
                            processedItems = processedItems,
                            hamaliVendorName = hamaliVendorName,
                            hamaliType = hamaliType,
                            totalAmount = totalAmount,
                            deductionAmount = deductionAmount,
                            finalAmount = finalAmount,
                            onSuccess = {
                                Log.d("SubmitButton", "Data submitted successfully.")
                                onBack() // Navigate back and remove the card in PreviewInwardScreen
                            },
                            onFailure = { error ->
                                Log.e("SubmitButton", "Failed to submit data: $error")
                            })
                    }, modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Submit")
                }
            }
        }
    })
}

fun fetchHamaliRates(
    hamaliVendorName: String,
    depot: String,
    onRatesFetched: (Double, Double, Double, Double) -> Unit
) {
    val client = OkHttpClient()
    val url =
        "https://vtc3pl.com/fetch_testingnp_inoutstocker.php?Hvendor=$hamaliVendorName&Depot=$depot"

    CoroutineScope(Dispatchers.IO).launch {
        try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                Log.d("fetchHamaliRates", "Response Body: $responseBody")
                val jsonResponse = JSONObject(responseBody ?: "{}")
                Log.d("fetchHamaliRates", "Parsed Response: $jsonResponse")

                val regular = jsonResponse.optDouble("Regular", 0.0)
                val crossing = jsonResponse.optDouble("Crossing", 0.0)
                val regularBag = jsonResponse.optDouble("Regularbag", 0.0)
                val crossingBag = jsonResponse.optDouble("Crossingbag", 0.0)

                Log.d(
                    "fetchHamaliRates",
                    "Regular: $regular, Crossing: $crossing, RegularBag: $regularBag, CrossingBag: $crossingBag"
                )

                withContext(Dispatchers.Main) {
                    onRatesFetched(regular, crossing, regularBag, crossingBag)
                }
            } else {
                Log.e("fetchHamaliRates", "Error: ${response.message}")
            }
        } catch (e: Exception) {
            Log.e("fetchHamaliRates", "Exception: ${e.message}")
        }
    }
}

fun fetchWeightsFromServer(
    scannedItems: List<Pair<String, Pair<Int, List<Int>>>>, onResult: (Double) -> Unit
) {
    val client = OkHttpClient()
    val jsonRequest = JSONArray().apply {
        scannedItems.forEach { item ->
            val lrno = item.first
            val scannedItemCount = item.second.second.size

            put(JSONObject().apply {
                put("LRNO", lrno)
                put("ScannedItemCount", scannedItemCount)
            })
        }
    }

    val body = jsonRequest.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

    val request =
        Request.Builder().url("https://vtc3pl.com/fetch_total_weight_qty_inoutstocker_app.php")
            .post(body).build()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            Log.e("fetchWeightsFromServer", "Request failed: ${e.message}")
        }

        override fun onResponse(call: Call, response: Response) {
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                val jsonResponse = JSONObject(responseBody ?: "{}")
                val totalWeight = jsonResponse.optDouble("TotalWeight", 0.0)
                onResult(totalWeight)
            } else {
                Log.e("fetchWeightsFromServer", "Error: ${response.message}")
            }
        }
    })
}

@Composable
fun HamaliVendorDropdown(
    selectedOption: String, onOptionSelected: (String) -> Unit, depot: String
) {
    var options by remember { mutableStateOf<List<String>>(listOf("No Hamali Vendor")) }
    var isLoading by remember { mutableStateOf(false) }

    // Function to fetch vendors
    fun fetchVendors() {
        isLoading = true
        val url = "https://vtc3pl.com/fetch_hamalivendor_only_prn_app.php"

        CoroutineScope(Dispatchers.IO).launch {
            val startTime = System.currentTimeMillis() // Track the start time
            try {
                // Build the request body with the depot parameter
                val requestBody = FormBody.Builder().add("spinnerDepo", depot).build()

                // Create the request
                val request = Request.Builder().url(url).post(requestBody).build()

                // Initialize OkHttp client
                val client = OkHttpClient()

                // Execute the request
                val response = client.newCall(request).execute()

                // Parse the response
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    val json = JSONArray(responseBody)

                    val fetchedOptions = List(json.length()) { index ->
                        json.getString(index)
                    }
                    options = listOf("No Hamali Vendor") + fetchedOptions
                } else {
                    Log.e("HamaliVendorDropdown", "Error: ${response.message}")
                }
            } catch (e: Exception) {
                Log.e("HamaliVendorDropdown", "Exception: ${e.message}")
            } finally {
                // Ensure at least 1 second of loading time
                val elapsedTime = System.currentTimeMillis() - startTime
                val remainingTime = 1000 - elapsedTime
                if (remainingTime > 0) {
                    delay(remainingTime)
                }
                isLoading = false
            }
        }
    }

    // UI
    Box(modifier = Modifier.fillMaxWidth()) {
        DropdownMenuField(
            label = "Hamali Vendor Name",
            options = options,
            selectedOption = selectedOption,
            onOptionSelected = onOptionSelected
        )

        // Show loader if fetching
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(24.dp), strokeWidth = 2.dp
            )
        }
    }

    // Trigger fetching when dropdown is clicked
    LaunchedEffect(Unit) {
        fetchVendors()

        // Set the default selected option to "No Hamali Vendor"
        if (selectedOption.isEmpty()) {
            onOptionSelected("No Hamali Vendor")
        }
    }
}

@Composable
fun DropdownMenuField(
    label: String, options: List<String>, selectedOption: String, onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        TextField(value = selectedOption,
            onValueChange = {},
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            readOnly = true,
            trailingIcon = {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
            })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(onClick = {
                    onOptionSelected(option)
                    expanded = false
                }, text = { Text(option) })
            }
        }

    }
}

fun processScannedItems(
    scannedItems: List<Pair<String, Pair<Int, List<Int>>>>, userInputReason: String? = null
): List<Map<String, Any>> {
    val processedItems = mutableListOf<Map<String, Any>>()

    scannedItems.forEach { item ->
        val lrno = item.first
        val pkgsNo = item.second.first
        val boxNos = item.second.second

        val scannedCount = boxNos.size
        val missingQty = pkgsNo - scannedCount
        val notScannedItems = (1..pkgsNo).filter { it !in boxNos }

        val reason = if (missingQty == 0) "OK" else userInputReason ?: ""

        processedItems.add(
            mapOf(
                "LRNO" to lrno,
                "PkgsNo" to pkgsNo,
                "BoxNos" to boxNos,
                "ScannedCount" to scannedCount,
                "MissingQty" to missingQty,
                "NotScannedItemList" to notScannedItems,
                "Reason" to reason
            )
        )
    }

    return processedItems
}

fun submitDataToServer(
    prnOrThc: String,
    prn: String,
    username: String,
    depot: String,
    processedItems: List<Map<String, Any>>,
    hamaliVendorName: String,
    hamaliType: String,
    totalAmount: Int,
    deductionAmount: String,
    finalAmount: Int,
    onSuccess: () -> Unit,
    onFailure: (String) -> Unit
) {
    val client = OkHttpClient()

    val jsonObject = JSONObject().apply {
        put("prnOrThc", prnOrThc)
        put("prn", prn)
        put("username", username)
        put("depot", depot)
        put("processedItems", JSONArray().apply {
            processedItems.forEach { item ->
                put(JSONObject(item))
            }
        })
        put("hamaliVendorName", hamaliVendorName)
        put("hamaliType", hamaliType)
        put("totalAmount", totalAmount)
        put("deductionAmount", deductionAmount)
        put("finalAmount", finalAmount)
    }

    val body = jsonObject.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

    val request =
        Request.Builder().url("https://vtc3pl.com/save_inward_data_app.php").post(body).build()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            Log.e("OkHttp", "Request failed: ${e.message}")
            onFailure("Request failed: ${e.message}")
        }

        override fun onResponse(call: Call, response: Response) {
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                Log.d("OkHttp", "Response: $responseBody")
                onSuccess()
            } else {
                val errorMessage = response.message
                Log.e("OkHttp", "Error: $errorMessage")
                onFailure("Error: $errorMessage")
            }
        }
    })
}