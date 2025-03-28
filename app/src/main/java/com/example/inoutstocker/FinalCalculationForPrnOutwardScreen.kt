package com.example.inoutstocker

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
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
import java.io.ByteArrayOutputStream
import java.io.IOException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinalCalculationForPrnOutwardScreen(
    username: String,
    depot: String,
    totalBoxQty: Int,
    totalBoxWeight: Double,
    totalBagQty: Int,
    totalBagWeight: Double,
    sharedViewModel: SharedViewModel,
    navController: NavController
) {
    val outwardScannedData = sharedViewModel.prnOutwardScannedData
    Log.i("FinalCalculationForPrnOutwardScreen", "outwardScannedData : $outwardScannedData")

    var totalAmount by remember { mutableIntStateOf(0) }
    var deductionAmount by remember { mutableStateOf("0") }
    var finalAmount by remember { mutableIntStateOf(totalAmount) }
    var hamaliVendorName by remember { mutableStateOf("") }
    var hamaliType by remember { mutableStateOf("REGULAR") }
    var selectedVehicleCapacity by remember { mutableStateOf("MINI TEMPO UPTO 1 MT") }

    // State for showing the modal dialog
    var showDialog by remember { mutableStateOf(false) }
    var drsThcMapping by remember { mutableStateOf("") }
    var additionalMessage by remember { mutableStateOf("") }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorModalMessage by remember { mutableStateOf("") }
    val clipboardManager: ClipboardManager = LocalClipboardManager.current

    var capturedImage by remember { mutableStateOf<Bitmap?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    var vendorType by remember { mutableStateOf("ATTACHED") }
    var vendorName by remember { mutableStateOf("") }
    var vehicleNo by remember { mutableStateOf("") }

    LaunchedEffect(vendorType) {
        vendorName = ""
        vehicleNo = ""
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            capturedImage = bitmap
        }
    }

    Log.i(
        "FinalCalculationForPrnOutwardScreen",
        "Fetched totals - BoxQty: $totalBoxQty, BoxWeight: $totalBoxWeight, BagQty: $totalBagQty, BagWeight: $totalBagWeight"
    )

    var totalQty = totalBoxQty + totalBagQty
    var totalWeight = totalBoxWeight + totalBagWeight

    val isFormValid =
        hamaliVendorName.isNotEmpty() && vendorName.isNotEmpty() && vehicleNo.isNotEmpty() && selectedVehicleCapacity.isNotEmpty() && capturedImage != null

    Log.i(
        "FinalCalculationForPrnOutwardScreen", "totals - Qty: $totalQty, Weight: $totalWeight"
    )

    LaunchedEffect(hamaliVendorName, hamaliType) {
        if (hamaliVendorName == "No Hamali Vendor") {
            totalAmount = 0
            finalAmount = 0
        } else if (hamaliVendorName.isNotEmpty() && hamaliType.isNotEmpty()) {
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

    // Recalculate finalAmount whenever deductionAmount changes
    LaunchedEffect(deductionAmount) {
        finalAmount = totalAmount - (deductionAmount.toIntOrNull() ?: 0)
    }

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
            item { Text("Total Quantity: $totalQty") }
            item { Text("Total Weight: $totalWeight") }
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
                DropdownMenuField(
                    label = "Hamali Type",
                    options = listOf("REGULAR", "CROSSING"),
                    selectedOption = hamaliType,
                    onOptionSelected = { hamaliType = it })
            }

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
                    val vehicleRegex = Regex("^[A-Z]{2}[0-9]{2}[A-Z]{1,2}[0-9]{4}\$")

                    LaunchedEffect(vendorType) {
                        Log.d(
                            "VehicleNoTextField", "Vendor type changed. Clearing vehicleNoInput."
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
                                    text = vehicleNoError, color = MaterialTheme.colorScheme.error
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

            //Vehicle Capacity Model
            item {
                DropdownMenuField(
                    label = "Vehicle Capacity Model",
                    options = listOf(
                        "MINI TEMPO UPTO 1 MT",
                        "PICK UP 1 MT TO 2 MT",
                        "TEMPO 2 MT TO 3.5 MT",
                        "TEMPO 3.5 MT TO 5 MT",
                        "TEMPO 5 MT TO 7 MT",
                        "TRUCK 7 MT TO 9 MT",
                        "TAURAS 9 MT TO 16 MT",
                        "TAURAS 16 MT TO 21 MT",
                        "20 FT MULTI EXLE 13 TO 21 MT",
                        "20 FT MULTI EXLE 21 TO 26 MT",
                        "20 FT MULTI EXLE UPTO 13 MT",
                        "40 FT MULTI EXLE 13 TO 21 MT",
                        "40 FT MULTI EXLE 21 TO 26 MT",
                        "40 FT MULTI EXLE UPTO 13 MT"
                    ),
                    selectedOption = selectedVehicleCapacity,
                    onOptionSelected = { selectedVehicleCapacity = it })
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
                    modifier = Modifier.fillMaxWidth(),
                    enabled = hamaliVendorName != "No Hamali Vendor"
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
                if (capturedImage != null) {
                    Image(
                        bitmap = capturedImage!!.asImageBitmap(),
                        contentDescription = "Captured Image Preview",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )
                }
            }

            item {
                Box(
                    modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center
                ) {
                    if (capturedImage == null) {
                        // "Next" button opens the camera
                        Button(
                            onClick = { cameraLauncher.launch() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Next")
                        }
                    } else {
                        // Once an image is captured, show the "Submit" button
                        if (!isSubmitting) {
                            Button(
                                onClick = {
                                    isSubmitting = true
                                    submitFinalCalculationForPrnOutwardScreen(
                                        username = username,
                                        depot = depot,
                                        totalQty = totalQty,
                                        totalWeight = totalWeight,
                                        totalAmount = totalAmount,
                                        deductionAmount = deductionAmount.toIntOrNull() ?: 0,
                                        finalAmount = finalAmount,
                                        hamaliVendorName = hamaliVendorName,
                                        hamaliType = hamaliType,
                                        outwardScannedData = outwardScannedData,
                                        capturedImage = capturedImage,
                                        vendorType = vendorType,
                                        vendorName = vendorName,
                                        vehicleNo = vehicleNo,
                                        selectedVehicleCapacity = selectedVehicleCapacity,
                                        navController = navController,
                                        sharedViewModel = sharedViewModel,
                                        onFailure = { error ->
                                            Log.e("FinalCalculation", "Error: $error")
                                            errorModalMessage = error
                                            isSubmitting = false
                                            showErrorDialog = true
                                        },
                                        onSuccess = { prnNumber, additionalMsg ->
                                            drsThcMapping = prnNumber
                                            additionalMessage = additionalMsg
                                            isSubmitting = false
                                            showDialog = true
                                        })
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = isFormValid && !isSubmitting
                            ) {
                                Text("Submit")
                            }
                        } else {
                            LottieAnimationView()
                        }
                    }
                }
            }
        }
    }

    // Show the modal dialog
    if (showDialog) {
        AlertDialog(onDismissRequest = {
            showDialog = false
            navController.navigate("homeScreen/$username/$depot") {
                popUpTo("homeScreen/$username/$depot") { inclusive = true }
            }
        }, title = { Text("Generated Number") }, text = {
            Column {
                Text("PRN Number: $drsThcMapping")
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(thickness = 1.dp, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Message: $additionalMessage")
            }
        }, confirmButton = {
            Button(onClick = {
                showDialog = false
                navController.navigate("homeScreen/$username/$depot") {
                    popUpTo("homeScreen/$username/$depot") { inclusive = true }
                }
            }) {
                Text("OK")
            }
        }, dismissButton = {
            Button(onClick = {
                clipboardManager.setText(AnnotatedString(drsThcMapping))
                showDialog = false
                navController.navigate("homeScreen/$username/$depot") {
                    popUpTo("homeScreen/$username/$depot") { inclusive = true }
                }
            }) {
                Text("Copy")
            }
        })
    }

    if (showErrorDialog) {
        Dialog(onDismissRequest = { showErrorDialog = false }) {
            Surface(
                shape = MaterialTheme.shapes.medium, color = Color.Red
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Error", color = Color.White, style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        errorModalMessage,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Box(
                        modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd
                    ) {
                        Button(onClick = { showErrorDialog = false }) {
                            Text("OK")
                        }
                    }
                }
            }
        }
    }

}

fun submitFinalCalculationForPrnOutwardScreen(
    username: String,
    depot: String,
    totalQty: Int,
    totalWeight: Double,
    totalAmount: Int,
    deductionAmount: Int,
    finalAmount: Int,
    hamaliVendorName: String,
    hamaliType: String,
    outwardScannedData: List<Pair<String, Pair<Int, List<Int>>>>,
    capturedImage: Bitmap?,
    vendorType: String,
    vendorName: String,
    vehicleNo: String,
    selectedVehicleCapacity: String,
    navController: NavController,
    sharedViewModel: SharedViewModel,
    onFailure: (String) -> Unit,
    onSuccess: (String, String) -> Unit
) {
    val client = OkHttpClient()
    val url = "https://vtc3pl.com/prn_outward_insert.php"

    val jsonRequest = JSONObject().apply {
        put("username", username)
        put("depot", depot)
        put("totalQty", totalQty)
        put("totalWeight", totalWeight)
        put("totalAmount", totalAmount)
        put("deductionAmount", deductionAmount)
        put("finalAmount", finalAmount)
        put("hamaliVendorName", hamaliVendorName)
        put("hamaliType", hamaliType)
        put("vendorType", vendorType)
        put("vendorName", vendorName)
        put("vehicleNo", vehicleNo)
        put("selectedVehicleCapacity", selectedVehicleCapacity)

        Log.d("FinalCalculation", "Processing outwardScannedData: $outwardScannedData")

        val scannedDataArray = JSONArray()
        outwardScannedData.forEach { (lrno, pkgData) ->
            val (pkgNo, scannedBoxes) = pkgData
            val scannedItemJson = JSONObject().apply {
                put("LRNO", lrno)
                put("TotalPkgNo", pkgNo)
                put("ScannedBoxes", JSONArray(scannedBoxes))
            }
            Log.d("FinalCalculation", "Adding scanned item: $scannedItemJson")
            scannedDataArray.put(scannedItemJson)
        }
        put("outwardScannedData", scannedDataArray)
        Log.d("FinalCalculation", "JSON Request built: $this")

        // Convert the captured image to a Base64 string and add it to the JSON payload
        capturedImage?.let { bitmap ->
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            val imageBytes = stream.toByteArray()
            val base64Image = Base64.encodeToString(imageBytes, Base64.DEFAULT)
            put("capturedImage", base64Image)
        }
    }

    val requestBody =
        jsonRequest.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

    val request = Request.Builder().url(url).post(requestBody).build()
    Log.d("FinalCalculation", "Request built successfully. Sending request...")

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            Log.e("FinalCalculation", "Request failed: ${e.message}")
            Handler(Looper.getMainLooper()).post {
                onFailure("Failed to connect to server")
            }
        }

        override fun onResponse(call: Call, response: Response) {
            Log.d("FinalCalculation", "Received response. Code: ${response.code}")
            response.use {
                if (!it.isSuccessful) {
                    Handler(Looper.getMainLooper()).post {
                        onFailure("Server error: ${it.code}")
                    }
                    return
                }
                val responseBody = it.body?.string().orEmpty()
                Log.d("FinalCalculation", "Response body: $responseBody")

                try {
                    // If the response contains multiple JSON objects, extract the last one.
                    val trimmedResponse = responseBody.trim()
                    val jsonResponse = if (trimmedResponse.contains("}{")) {
                        // Split by "}{", then add missing braces to the last part.
                        val parts = trimmedResponse.split("}{")
                        // Reconstruct the last JSON object:
                        JSONObject("{" + parts.last())
                    } else {
                        JSONObject(trimmedResponse)
                    }
                    Log.d("FinalCalculation", "Parsed JSON response: $jsonResponse")

                    val success = jsonResponse.optBoolean("success", false)
                    if (success) {
                        val prnNumber = jsonResponse.optString("PRNNumber", "")
                        val additionalMsg = jsonResponse.optString("message", "")
                        Handler(Looper.getMainLooper()).post {
                            onSuccess(prnNumber, additionalMsg)
                            sharedViewModel.clearPrnOutwardData()
                        }
                    } else {
                        val errorMsg = jsonResponse.optString("error", "Unknown error")
                        Handler(Looper.getMainLooper()).post {
                            onFailure(errorMsg)
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