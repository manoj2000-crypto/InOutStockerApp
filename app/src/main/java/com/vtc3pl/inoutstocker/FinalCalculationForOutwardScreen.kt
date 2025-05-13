package com.vtc3pl.inoutstocker

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.foundation.Image
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
fun FinalCalculationForOutwardScreen(
    username: String,
    depot: String,
    loadingSheetNo: String,
    totalBoxQty: Int,
    totalBoxWeight: Double,
    totalBagQty: Int,
    totalBagWeight: Double,
    groupCode: String,
    sharedViewModel: SharedViewModel,
    navController: NavController
) {
    val outwardScannedData = sharedViewModel.outwardScannedData
    var totalAmount by remember { mutableIntStateOf(0) }
    var deductionAmount by remember { mutableStateOf("0") }
    var finalAmount by remember { mutableIntStateOf(totalAmount) }
    var hamaliVendorName by remember { mutableStateOf("") }
    var hamaliType by remember { mutableStateOf("REGULAR") }

    // State for showing the modal dialog
    var showDialog by remember { mutableStateOf(false) }
    var drsThcMapping by remember { mutableStateOf("") }
    var additionalMessage by remember { mutableStateOf("") }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorModalMessage by remember { mutableStateOf("") }
    val clipboardManager: ClipboardManager = LocalClipboardManager.current

    var capturedImage by remember { mutableStateOf<Bitmap?>(null) }
    var selectedGateNumber by remember { mutableIntStateOf(1) }
    var isSubmitting by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            capturedImage = bitmap
        }
    }

    Log.i(
        "FinalCalculationForOutwardScreen",
        "Fetched totals - BoxQty: $totalBoxQty, BoxWeight: $totalBoxWeight, BagQty: $totalBagQty, BagWeight: $totalBagWeight"
    )

    var totalQty = totalBoxQty + totalBagQty
    var totalWeight = totalBoxWeight + totalBagWeight

    Log.i(
        "FinalCalculationForOutwardScreen",
        "totals - Qty: $totalQty, Weight: $totalWeight"
    )

    LaunchedEffect(hamaliVendorName, hamaliType) {
        if (hamaliVendorName == "No Hamali Vendor") {
            totalAmount = 0
            finalAmount = 0
        } else if (hamaliVendorName.isNotEmpty() && hamaliType.isNotEmpty()) {
            fetchHamaliRates(
                hamaliVendorName,
                depot
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
//            item { Text("Outward Scanned Data: $outwardScannedData") }
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
                DropdownMenuField(
                    label = "Gate Number",
                    options = listOf(
                        "Gate no 1",
                        "Gate no 2",
                        "Gate no 3",
                        "Gate no 4",
                        "Gate no 5"
                    ),
                    selectedOption = "Gate no $selectedGateNumber",
                    onOptionSelected = { option ->
                        // Extract digits from the option (assumes format "Gate no X")
                        selectedGateNumber = option.filter { it.isDigit() }.toIntOrNull() ?: 1
                    }
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
                                        gateNumber = selectedGateNumber,
                                        hamaliType = hamaliType,
                                        outwardScannedData = outwardScannedData,
                                        categorizedLrnos = sharedViewModel.categorizedLrnos,
                                        capturedImage = capturedImage,
//                                        navController = navController,
                                        sharedViewModel = sharedViewModel,
                                        onFailure = { error ->
                                            Log.e("FinalCalculation", "Error: $error")
                                            errorModalMessage = error
                                            isSubmitting = false
                                            showErrorDialog = true
                                        },
                                        onSuccess = { drsThcNumber, additionalMsg ->
                                            drsThcMapping = drsThcNumber
                                            additionalMessage = additionalMsg
                                            isSubmitting = false
                                            showDialog = true
                                        })
                                }, modifier = Modifier.fillMaxWidth()
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
            navController.navigate("outwardScreen/$username/$depot") {
                popUpTo("outwardScreen/$username/$depot") { inclusive = true }
            }
        },
            title = { Text("Generated Number") },
//            text = { Text("DRS/THC/MF Number: $drsThcMapping \n-------\nMessage: $additionalMessage") },
            text = {
                Column {
                    Text("DRS/THC/MF Number: $drsThcMapping")
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(thickness = 1.dp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Message: $additionalMessage")
                }
            },
            confirmButton = {
                Button(onClick = {
                    showDialog = false
                    navController.navigate("outwardScreen/$username/$depot") {
                        popUpTo("outwardScreen/$username/$depot") { inclusive = true }
                    }
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                Button(onClick = {
                    clipboardManager.setText(AnnotatedString(drsThcMapping))
                    showDialog = false
                    navController.navigate("outwardScreen/$username/$depot") {
                        popUpTo("outwardScreen/$username/$depot") { inclusive = true }
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
    gateNumber: Int,
    hamaliType: String,
    outwardScannedData: List<Pair<String, Pair<Int, List<Int>>>>,
    categorizedLrnos: Map<String, List<String>>,
    capturedImage: Bitmap?,
//    navController: NavController,
    sharedViewModel: SharedViewModel,
    onFailure: (String) -> Unit,
    onSuccess: (String, String) -> Unit
) {
    Log.d(
        "FinalCalculation",
        "submitFinalCalculation called with username: $username, depot: $depot, loadingSheetNo: $loadingSheetNo"
    )

    val client = OkHttpClient()
    val url = "https://vtc3pl.com/final_outward_calculation.php"

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
        put("gateNumber", gateNumber)
        put("hamaliType", hamaliType)

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

        val categorizedJson = JSONObject()
        categorizedLrnos.forEach { (loadingSheet, lrnos) ->
            val lrnoArray = JSONArray()
            lrnos.forEach { lrno ->
                lrnoArray.put(lrno)
            }
            categorizedJson.put(loadingSheet, lrnoArray)
        }
        put("categorizedLrnos", categorizedJson)

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
            onFailure("Failed to connect to server")
        }

        override fun onResponse(call: Call, response: Response) {
            Log.d("FinalCalculation", "Received response. Code: ${response.code}")
            response.use {
                if (!it.isSuccessful) {
                    Log.e("FinalCalculation", "Server error: ${it.code}")
                    onFailure("Server error: ${it.code}")
                    return
                }
                val responseBody = it.body?.string()
                Log.d("FinalCalculation", "Response body: $responseBody")

                try {
                    val jsonResponse = JSONObject(responseBody ?: "{}")
                    Log.d("FinalCalculation", "Parsed JSON response: $jsonResponse")
                    val success = jsonResponse.optBoolean("success", false)
                    val message = jsonResponse.optString("message", "Unknown response")

                    Handler(Looper.getMainLooper()).post {
                        if (success) {
                            Log.d("FinalCalculation", "Success: $message")

                            val manifestNumberRaw =
                                jsonResponse.optString("manifestNumber", "").trim()
                            val manifestNumber = if (manifestNumberRaw.equals(
                                    "null",
                                    ignoreCase = true
                                )
                            ) "" else manifestNumberRaw
                            var generatedNumber = if (manifestNumber.isNotEmpty()) {
                                Log.d("FinalCalculation", "Using manifestNumber: $manifestNumber")
                                manifestNumber
                            } else {
                                // Extract drsThcMapping from response
                                val drsThcMappingJson = jsonResponse.optJSONObject("drsThcMapping")
                                var mappingNumber = "N/A"
                                if (drsThcMappingJson != null) {
                                    Log.d(
                                        "FinalCalculation",
                                        "drsThcMapping found: $drsThcMappingJson"
                                    )
                                    val keys = drsThcMappingJson.keys()
                                    val numbers = mutableListOf<String>()
                                    while (keys.hasNext()) {
                                        val key = keys.next()
                                        val number = drsThcMappingJson.optString(key, "")
                                        Log.d(
                                            "FinalCalculation",
                                            "drsThcMapping: key = $key, value = $number"
                                        )
                                        numbers.add(number)
                                    }
                                    if (numbers.isNotEmpty()) {
                                        mappingNumber = numbers.joinToString(", ")
                                        Log.d(
                                            "FinalCalculation",
                                            "Generated number(s): $mappingNumber"
                                        )
                                    }
                                } else {
                                    Log.d("FinalCalculation", "No drsThcMapping found in response")
                                }
                                mappingNumber
                            }
                            val additionalMsg = jsonResponse.optString("additionalMessage", "")
                            onSuccess(generatedNumber, additionalMsg)
                            Log.d("FinalCalculation", "Clearing outward data in sharedViewModel")
                            sharedViewModel.clearOutwardData()
                        } else {
                            Log.e("FinalCalculation", "Failure response received: $message")
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