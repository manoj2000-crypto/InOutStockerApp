package com.vtc3pl.inoutstocker

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URLDecoder

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
    val decodedPrnOrThc = URLDecoder.decode(prnOrThc, "UTF-8")
    val decodedPrn = URLDecoder.decode(prn, "UTF-8")

    var totalAllQtySum by remember { mutableDoubleStateOf(0.0) }
    var totalAllWeightSum by remember { mutableDoubleStateOf(0.0) }

    var totalBoxQty by remember { mutableDoubleStateOf(0.0) }
    var totalBoxWeight by remember { mutableDoubleStateOf(0.0) }
    var totalBagQty by remember { mutableDoubleStateOf(0.0) }
    var totalBagWeight by remember { mutableDoubleStateOf(0.0) }
//    var pkgType by remember { mutableStateOf("") }

    val isLoading = remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val showSuccessDialog = remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    // Calculate TotalBagQty and TotalBoxQty from scannedItems
    LaunchedEffect(scannedItems) {
        fetchWeightsFromServer(scannedItems) { totals ->
            totalBoxQty = totals.totalBoxQty
            totalBoxWeight = totals.totalBoxWeight
            totalBagQty = totals.totalBagQty
            totalBagWeight = totals.totalBagWeight

            totalAllQtySum = totalBoxQty + totalBagQty
            totalAllWeightSum = totalBoxWeight + totalBagWeight
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

    //ADDED code here to see the all inward data and after that if it matches the arrival lr with the shared view modal then we have to remove that from the Shred view modal
    val sharedViewModel: SharedViewModel = viewModel()
    // Trigger logging when the composable is first composed
    LaunchedEffect(Unit) {
        // Set the feature type to INWARD
        sharedViewModel.setFeatureType(SharedViewModel.FeatureType.INWARD)

        // Log the dates
        Log.d("InwardData", "From Date: ${sharedViewModel.fromDate}")
        Log.d("InwardData", "To Date: ${sharedViewModel.toDate}")

        // Log each scanned item
        sharedViewModel.scannedItems.forEach { item ->
            val (lrno, data) = item
            val (totalPkgs, boxNos) = data
            Log.d(
                "InwardData",
                "LRNO: $lrno, Total Packages: $totalPkgs, Box Numbers: ${boxNos.joinToString(", ")}"
            )
        }

        // Log any processed excess LRs
        sharedViewModel.processedExcessLrs.forEach { lr ->
            Log.d("InwardData", "Processed Excess LR: $lr")
        }
    }

    var hamaliVendorName by remember { mutableStateOf("") }
    var hamaliType by remember { mutableStateOf("REGULAR") }
    var totalAmount by remember { mutableIntStateOf(0) }
    var deductionAmount by remember { mutableStateOf("0") }

    var finalAmount by remember { mutableIntStateOf(totalAmount) }
    var freight by remember { mutableStateOf("0") }
    var godownKeeperName by remember { mutableStateOf("") }
    var closingKm by remember { mutableStateOf("") }

    var paymentMode by remember { mutableStateOf("") }
    var transactionId by remember { mutableStateOf("") }
    var unloadingHamaliReceived by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var vendorType by remember { mutableStateOf("ATTACHED") }
    var vendorName by remember { mutableStateOf("") }
    var vehicleNo by remember { mutableStateOf("") }

    val coroutineExceptionHandler = CoroutineExceptionHandler { _, exception ->
        Log.e("CoroutineException", "Unhandled exception: ${exception.message}")
        isLoading.value = false
        errorMessage = exception.message ?: "An unexpected error occurred."
        showErrorDialog = true
    }

    LaunchedEffect(username) {
        godownKeeperName = fetchFullName(username)
    }

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
                var boxAmount = 0.0
                var bagAmount = 0.0

                if (hamaliType.equals("REGULAR", ignoreCase = true)) {
                    if (regular > 0) {
                        boxAmount = totalBoxQty * regular
                    }
                    if (regularBag > 0) {
                        bagAmount = (totalBagWeight / 1000) * regularBag
                    }
                } else if (hamaliType.equals("CROSSING", ignoreCase = true)) {
                    if (crossing > 0) {
                        boxAmount = totalBoxQty * crossing
                    }
                    if (crossingBag > 0) {
                        bagAmount = (totalBagWeight / 1000) * crossingBag
                    }
                }

                totalAmount = (boxAmount + bagAmount).toInt()
                finalAmount = totalAmount - (deductionAmount.toIntOrNull() ?: 0)
                Log.d(
                    "HamaliCalculation",
                    "hamaliType: $hamaliType, Regular: $regular, Crossing: $crossing, RegularBag: $regularBag, CrossingBag: $crossingBag"
                )
                Log.d(
                    "HamaliCalculation",
                    "Box Amount: $boxAmount, Bag Amount: $bagAmount, Total Amount: $totalAmount, Final Amount: $finalAmount"
                )
            }
        }
    }

    LaunchedEffect(deductionAmount) {
        finalAmount = totalAmount - (deductionAmount.toIntOrNull() ?: 0)
        Log.d("DeductionChange", "Deduction: $deductionAmount, Final Amount: $finalAmount")
    }

    LaunchedEffect(vendorType) {
        vendorName = ""
        vehicleNo = ""
    }

    val isSubmitEnabled = reason.trim().isNotEmpty() && hamaliVendorName.trim()
        .isNotEmpty() && godownKeeperName.trim().isNotEmpty() && (deductionAmount.toIntOrNull()
        ?: 0) >= 0 && (freight.toIntOrNull() ?: 0) >= 0 && (if (decodedPrnOrThc == "PRN") {
        vendorName.trim().isNotEmpty() && vendorType.trim().isNotEmpty() && vehicleNo.trim()
            .isNotEmpty()
    } else true)

//    val isSubmitEnabled = reason.trim().isNotEmpty() && hamaliVendorName.trim()
//        .isNotEmpty() && godownKeeperName.trim().isNotEmpty() && ((deductionAmount.toIntOrNull()
//        ?: 0) >= 0) && ((freight.toIntOrNull() ?: 0) >= 0)

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
                Text("Total Qty: $totalAllQtySum")
            }
            item {
                Text("Total Weight: $totalAllWeightSum")
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
                DropdownMenuField(
                    label = "Hamali Type",
                    options = listOf("REGULAR", "CROSSING"),
                    selectedOption = hamaliType,
                    onOptionSelected = { hamaliType = it })
            }
            Log.d("FinalCalculationScreen", "Hamali Type: $hamaliType")

            item {
                TextField(
                    value = totalAmount.toString(),
                    onValueChange = {},
                    label = { Text("Total Amount") },
                    enabled = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Log.d("FinalCalculationScreen", "Total Amount: $totalAmount")

            item {
                TextField(
                    value = deductionAmount,
                    onValueChange = { input ->
                        // Keep only digits
                        deductionAmount = input.filter { it.isDigit() }
                    },
                    label = { Text("Deduction Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = (deductionAmount.toIntOrNull() ?: 0) < 0,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Log.d("FinalCalculationScreen", "Deduction Amount: $deductionAmount")

            item {
                TextField(
                    value = finalAmount.toString(),
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
                    DropdownMenuField(
                        label = "Vendor Type",
                        options = listOf("ATTACHED", "OWN"),
                        selectedOption = vendorType,
                        onOptionSelected = { vendorType = it })
                }
                Log.d("FinalCalculationScreen", "Vendor Type: $vendorType")

                // New Vendor Name dropdown based on vendor type and depot
                item {
                    VendorNameDropdown(
                        vendorType = vendorType,
                        depot = depot,
                        selectedVendor = vendorName,
                        onVendorSelected = { vendorName = it })
                }
                Log.d("FinalCalculationScreen", "Vendor Name: $vendorName")

                // New Vehicle No dropdown based on selected vendor name
                if (vendorType == "ATTACHED") {
                    item {
                        // TextField for manual Vehicle Number entry with validation
                        var vehicleNoInput by remember { mutableStateOf(vehicleNo) }
                        var vehicleNoError by remember { mutableStateOf("") }
                        // Regex for a typical Indian vehicle number (e.g., KA01AB1234)
                        val vehicleRegex = Regex("^[A-Z]{2}[0-9]{2}[A-Z]{1,2}[0-9]{4}$")

                        LaunchedEffect(vendorType) {
                            Log.d(
                                "VehicleNoTextField",
                                "Vendor type changed. Clearing vehicleNoInput."
                            )
                            vehicleNoInput = ""
                        }

                        TextField(
                            value = vehicleNoInput,
                            onValueChange = { input ->
                                // Force uppercase for consistency
                                vehicleNoInput = input.uppercase()
                                // Validate input against the regex
                                if (vehicleRegex.matches(vehicleNoInput)) {
                                    vehicleNoError = ""
                                    vehicleNo = vehicleNoInput
                                } else {
                                    vehicleNoError = "Invalid Vehicle Number format"
                                }
                            },
                            label = { Text("Vehicle Number") },
                            isError = vehicleNoError.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                            supportingText = {
                                if (vehicleNoError.isNotEmpty()) {
                                    Text(
                                        text = vehicleNoError,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            })
                    }
                } else if (vendorType == "OWN") {
                    // Dropdown for selecting a Vehicle Number
                    item {
                        VehicleNoDropdown(
                            vendorName = vendorName,
                            depot = depot,
                            selectedVehicle = vehicleNo,
                            onVehicleSelected = { vehicleNo = it })
                    }
                }
                Log.d("FinalCalculationScreen", "vehicle Number: $vehicleNo")

                item {
                    TextField(
                        value = freight,
                        onValueChange = { input ->
                            // Keep only digits
                            freight = input.filter { it.isDigit() }
                        },
                        label = { Text("Freight") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = (freight.toIntOrNull() ?: 0) < 0,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Log.d("FinalCalculationScreen", "Freight: $freight")

                item {
                    TextField(
                        value = godownKeeperName,
                        onValueChange = { godownKeeperName = it },
                        label = { Text("Godown Keeper Name") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false
                    )
                }
                Log.d("FinalCalculationScreen", "Godown Keeper Name: $godownKeeperName")

            } else if (decodedPrnOrThc == "THC") {
                item {
                    TextField(
                        value = closingKm,
                        onValueChange = { closingKm = it },
                        label = { Text("Closing KM") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Log.d("FinalCalculationScreen", "Closing KM: $closingKm")

                item {
                    DropdownMenuField(
                        label = "Payment Mode",
                        options = listOf("BANK", "CASH"),
                        selectedOption = paymentMode,
                        onOptionSelected = { paymentMode = it })
                }
                Log.d("FinalCalculationScreen", "Payment Mode: $paymentMode")

                item {
                    TextField(
                        value = transactionId,
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
                TextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Box(
                    modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center
                ) {
                    if (isLoading.value) {
                        LottieAnimationView()
                    } else {

                        Button(
                            onClick = {
                                coroutineScope.launch(coroutineExceptionHandler) {
                                    isLoading.value = true
                                    try {
                                        val processedItems =
                                            processScannedItems(scannedItems, reason)

                                        submitDataToServer(
                                            prnOrThc = prnOrThc,
                                            prn = prn,
                                            username = username,
                                            depot = depot,
                                            processedItems = processedItems,
                                            hamaliVendorName = hamaliVendorName,
                                            hamaliType = hamaliType,
                                            totalAmount = totalAmount,
                                            deductionAmount = deductionAmount,
                                            finalAmount = finalAmount,
                                            closingKm = if (prnOrThc == "THC") closingKm else "",
                                            unloadingHamaliReceived = if (prnOrThc == "THC") unloadingHamaliReceived else "",
                                            paymentMode = if (prnOrThc == "THC") paymentMode else "",
                                            transactionId = if (prnOrThc == "THC") transactionId else "",
                                            reason = reason,
                                            vendorType = if (prnOrThc == "PRN") vendorType else "",
                                            vendorName = if (prnOrThc == "PRN") vendorName else "",
                                            vehicleNo = if (prnOrThc == "PRN") vehicleNo else "",
                                            freight = if (prnOrThc == "PRN") freight else "",
                                            godownKeeperName = if (prnOrThc == "PRN") godownKeeperName else "",
                                            onSuccess = {
                                                // Store the processedItems in the repository when the submission is successful
                                                ProcessedItemsRepository.addProcessedItems(
                                                    processedItems
                                                )
                                                Log.d(
                                                    "SubmitButton", "Data submitted successfully."
                                                )
                                                isLoading.value = false
                                                showSuccessDialog.value = true
                                            },
                                            onFailure = { error ->
                                                Log.e(
                                                    "SubmitButton", "Failed to submit data: $error"
                                                )
                                                isLoading.value = false
                                                errorMessage = error
                                                showErrorDialog = true
                                            })
                                    } catch (e: Exception) {
                                        Log.e("SubmitButton", "Exception: ${e.message}")
                                        isLoading.value = false
                                        errorMessage = e.message ?: "An unexpected error occurred."
                                        showErrorDialog = true
                                    }
                                }
                            }, modifier = Modifier.fillMaxWidth(), enabled = isSubmitEnabled
                        ) {
                            Text("Submit")
                        }
                    }
                }

                // Show success dialog if data submission is successful
                if (showSuccessDialog.value) {
                    AlertDialog(
                        onDismissRequest = {
                        showSuccessDialog.value = false
                        onBack() // Navigate back and remove the card in PreviewInwardScreen
                    },
                        confirmButton = {
                            TextButton(onClick = {
                                showSuccessDialog.value = false
                                onBack() // Navigate back and remove the card in PreviewInwardScreen
                            }) {
                                Text("OK")
                            }
                        },
                        title = { Text("Success") },
                        text = { Text("Data submitted successfully.") })
                }

                if (showErrorDialog) {
                    AlertDialog(
                        onDismissRequest = { showErrorDialog = false },
                        title = { Text("Error", color = Color.Red) },
                        text = { Text(errorMessage, color = Color.Red) },
                        confirmButton = {
                            TextButton(onClick = { showErrorDialog = false }) {
                                Text("OK")
                            }
                        })
                }
            }
        }
    })
}

suspend fun fetchFullName(username: String): String {
    return withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val mediaType = "application/x-www-form-urlencoded".toMediaTypeOrNull()
            val requestBody = "user_name=$username".toRequestBody(mediaType)
            val request =
                Request.Builder().url("https://vtc3pl.com/fetch_fullname_user_inoutstockerapp.php")
                    .post(requestBody).build()

            val response: Response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (responseBody.isEmpty()) {
                return@withContext "Error: Empty response"
            }

            return@withContext try {
                val jsonResponse = JSONObject(responseBody)
                if (jsonResponse.optString("status") == "success") {
                    jsonResponse.optString("FullName", "Not Found")
                } else {
                    jsonResponse.optString("message", "Not Found")
                }
            } catch (e: Exception) {
                Log.e("fetchFullName", "Error parsing JSON", e)
                "Error: Invalid response"
            }
        } catch (e: Exception) {
            Log.e("fetchFullName", "Error fetching full name", e)
            "Error: Network issue"
        }
    }
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
    scannedItems: List<Pair<String, Pair<Int, List<Int>>>>, onResult: (TotalWeights) -> Unit
) {
    Log.i(
        "fetchWeightsFromServer", "Starting fetchWeightsFromServer with scannedItems: $scannedItems"
    )
    val client = OkHttpClient()

    val jsonRequest = JSONArray().apply {
        scannedItems.forEach { item ->
            val lrno = item.first
            val scannedItemCount = item.second.second.size

            Log.i(
                "fetchWeightsFromServer",
                "Processing item - LRNO: $lrno, ScannedItemCount: $scannedItemCount"
            )

            put(JSONObject().apply {
                put("LRNO", lrno)
                put("ScannedItemCount", scannedItemCount)
            })
        }
    }
    Log.i("fetchWeightsFromServer", "Constructed JSON Request: $jsonRequest")

    val body = jsonRequest.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

//    val request =
//        Request.Builder().url("https://vtc3pl.com/fetch_total_weight_qty_inoutstocker_app.php")
//            .post(body).build()

    val request =
        Request.Builder().url("https://vtc3pl.com/fetch_total_weight_qty_inoutstocker_app.php")
            .post(body).build()

    Log.i(
        "fetchWeightsFromServer",
        "HTTP Request built with URL: https://vtc3pl.com/fetch_total_weight_qty_inoutstocker_app.php"
    )

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            Log.i("fetchWeightsFromServer", "onFailure triggered")
            Log.e("fetchWeightsFromServer", "Request failed: ${e.message}")
        }

        override fun onResponse(call: Call, response: Response) {
            Log.i(
                "fetchWeightsFromServer", "onResponse triggered with status code: ${response.code}"
            )
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                Log.i("fetchWeightsFromServer", "Response body received: $responseBody")
                val jsonResponse = JSONObject(responseBody ?: "{}")

                val totalBoxQty = jsonResponse.optDouble("TotalBoxQty", 0.0)
                val totalBoxWeight = jsonResponse.optDouble("TotalBoxWeight", 0.0)
                val totalBagQty = jsonResponse.optDouble("TotalBagQty", 0.0)
                val totalBagWeight = jsonResponse.optDouble("TotalBagWeight", 0.0)
                val pkgType = jsonResponse.optString("PkgType", "")

//                val totalWeight = jsonResponse.optDouble("TotalWeight", 0.0)
                Log.i(
                    "fetchWeightsFromServer",
                    "Parsed totals - BoxQty: $totalBoxQty, BoxWeight: $totalBoxWeight, BagQty: $totalBagQty, BagWeight: $totalBagWeight"
                )
                onResult(
                    TotalWeights(
                        totalBoxQty, totalBoxWeight, totalBagQty, totalBagWeight, pkgType
                    )
                )
            } else {
                Log.i("fetchWeightsFromServer", "Response not successful: ${response.message}")
                Log.e("fetchWeightsFromServer", "Error: ${response.message}")
            }
        }
    })
}

@Composable
fun HamaliVendorDropdown(
    selectedOption: String, onOptionSelected: (String) -> Unit, depot: String
) {
    var options by remember { mutableStateOf(listOf("No Hamali Vendor")) }
    var isLoading by remember { mutableStateOf(false) }

    // Function to fetch vendors
    fun fetchVendors() {
        isLoading = true
        val url = "https://vtc3pl.com/fetch_hamalivendor_only_prn_app.php"

        CoroutineScope(Dispatchers.IO).launch {
            val startTime = System.currentTimeMillis()
            try {
                val requestBody = FormBody.Builder().add("spinnerDepo", depot).build()
                val request = Request.Builder().url(url).post(requestBody).build()
                val client = OkHttpClient()
                val response = client.newCall(request).execute()
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
fun VendorNameDropdown(
    vendorType: String, depot: String, selectedVendor: String, onVendorSelected: (String) -> Unit
) {
    var vendorOptions by remember { mutableStateOf(listOf<String>()) }
    var isLoading by remember { mutableStateOf(false) }
    val url =
        "https://vtc3pl.com/fetch_vendor_data_inoutstocker_app.php" // Update with your endpoint URL

    // Define a coroutine exception handler for this composable
    val coroutineExceptionHandler = remember {
        CoroutineExceptionHandler { _, exception ->
            Log.e("VendorNameDropdown", "Unhandled exception: ${exception.message}")
            isLoading = false
        }
    }

    // Suspend function to fetch vendor names
    suspend fun fetchVendorNames() {
        isLoading = true
        try {
            Log.d(
                "VendorNameDropdown",
                "Fetching vendor names with vendortype: $vendorType and depot: $depot"
            )
            val formBody =
                FormBody.Builder().add("vendortype", vendorType).add("depot", depot).build()
            val request = Request.Builder().url(url).post(formBody).build()
            val client = OkHttpClient()
            val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
            if (response.isSuccessful) {
                response.body?.string()?.let { responseString ->
                    val jsonArray = JSONArray(responseString)
                    val names = mutableListOf<String>()
                    for (i in 0 until jsonArray.length()) {
                        val jsonObject = jsonArray.getJSONObject(i)
                        val vendorName = if (jsonObject.has("vendorCode")) {
                            jsonObject.getString("vendorCode") + "-" + jsonObject.getString("vendorName")
                        } else {
                            jsonObject.getString("vendorName")
                        }
                        names.add(vendorName)
                    }
                    vendorOptions = names
                }
            } else {
                Log.e("VendorNameDropdown", "Response not successful: ${response.code}")
            }
        } catch (e: Exception) {
            Log.e("VendorNameDropdown", "Error fetching vendor names: ${e.message}")
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(vendorType, depot) {
        if (vendorType.isNotEmpty() && depot.isNotEmpty()) {
            launch(coroutineExceptionHandler) {
                fetchVendorNames()
            }
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        DropdownMenuField(
            label = "Vendor Name",
            options = vendorOptions,
            selectedOption = selectedVendor,
            onOptionSelected = { selected ->
                // Extract only the vendorName after the "-" (if present)
                val vendorOnly = selected.substringAfter("-", selected).trim()
                onVendorSelected(vendorOnly)
            })
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxWidth(0.1f)
            )
        }
    }
}

@Composable
fun VehicleNoDropdown(
    vendorName: String, depot: String, selectedVehicle: String, onVehicleSelected: (String) -> Unit
) {
    var vehicleOptions by remember { mutableStateOf(listOf<String>()) }
    var isLoading by remember { mutableStateOf(false) }
    val url =
        "https://vtc3pl.com/fetch_vendor_data_inoutstocker_app.php" // Update with your endpoint URL

    val coroutineExceptionHandler = remember {
        CoroutineExceptionHandler { _, exception ->
            Log.e("VehicleNoDropdown", "Unhandled exception: ${exception.message}")
            isLoading = false
        }
    }

    // Suspend function to fetch vehicle numbers
    suspend fun fetchVehicleNumbers() {
        isLoading = true
        try {
            Log.d("VehicleNoDropdown", "Fetching vehicle numbers for vendor: $vendorName")
            val formBody =
                FormBody.Builder().add("vendorname", vendorName).add("depot", depot).build()
            val request = Request.Builder().url(url).post(formBody).build()
            val client = OkHttpClient()
            val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
            if (response.isSuccessful) {
                val responseString = response.body?.string() ?: ""
                Log.d("VehicleNoDropdown", "Response received: $responseString")
                try {
                    val jsonArray = JSONArray(responseString)
                    val vehicles = mutableListOf<String>()
                    for (i in 0 until jsonArray.length()) {
                        vehicles.add(jsonArray.getString(i))
                    }
                    vehicleOptions = vehicles
                    Log.d("VehicleNoDropdown", "Fetched vehicle options: $vehicleOptions")
                } catch (e: JSONException) {
                    Log.e("VehicleNoDropdown", "JSONException: ${e.message}")
                    vehicleOptions = emptyList()
                }
            } else {
                Log.e("VehicleNoDropdown", "Response not successful: ${response.code}")
            }
        } catch (e: Exception) {
            Log.e("VehicleNoDropdown", "Error fetching vehicle numbers: ${e.message}")
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(vendorName) {
        if (vendorName.isNotEmpty()) {
            launch(coroutineExceptionHandler) {
                fetchVehicleNumbers()
            }
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        DropdownMenuField(
            label = "Vehicle No",
            options = vehicleOptions,
            selectedOption = selectedVehicle,
            onOptionSelected = onVehicleSelected
        )
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxWidth(0.1f)
            )
        }
    }
}

@Composable
fun DropdownMenuField(
    label: String, options: List<String>, selectedOption: String, onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        TextField(
            value = selectedOption,
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
    // Additional fields for THC (if not applicable for PRN, pass an empty string)
    closingKm: String,
    unloadingHamaliReceived: String,
    paymentMode: String,
    transactionId: String,
    reason: String,
    // NEW parameters for PRN only:
    vendorType: String,
    vendorName: String,
    vehicleNo: String,
    freight: String,
    godownKeeperName: String,
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
        put("deductionAmount", deductionAmount.toDoubleOrNull() ?: 0)
        put("finalAmount", finalAmount)
        put("closingKm", closingKm)
        put("unloadingHamaliReceived", unloadingHamaliReceived)
        put("paymentMode", paymentMode)
        put("transactionId", transactionId)
        put("reason", reason)
        if (prnOrThc == "PRN") {
            put("vendorType", vendorType)
            put("vendorName", vendorName)
            put("vehicleNo", vehicleNo)
            put("freight", freight.toIntOrNull() ?: 0)
            put("godownKeeperName", godownKeeperName)
        }
    }

    val body = jsonObject.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

    val request =
        Request.Builder().url("https://vtc3pl.com/save_inward_data_app.php").post(body).build()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            Handler(Looper.getMainLooper()).post {
                Log.e("OkHttp", "Request failed: ${e.message}")
                onFailure("Request failed: ${e.message}")
            }
        }

        override fun onResponse(call: Call, response: Response) {
            Handler(Looper.getMainLooper()).post {
                val responseBody = response.body?.string()
                Log.d("OkHttp", "Response: $responseBody")
                try {
                    val jsonResponse = JSONObject(responseBody ?: "{}")
                    if (jsonResponse.optString("status") == "error") {
                        onFailure(jsonResponse.optString("message", "Unknown error"))
                    } else {
                        onSuccess()
                    }
                } catch (_: Exception) {
                    onFailure("Error parsing response")
                }
            }
        }
    })
}

data class TotalWeights(
    val totalBoxQty: Double,
    val totalBoxWeight: Double,
    val totalBagQty: Double,
    val totalBagWeight: Double,
    val pkgType: String
)