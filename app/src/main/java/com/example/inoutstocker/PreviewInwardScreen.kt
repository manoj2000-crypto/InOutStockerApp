package com.example.inoutstocker

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import androidx.lifecycle.viewmodel.compose.viewModel
import okhttp3.FormBody

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SourceLockedOrientationActivity")
@Composable
fun PreviewInwardScreen(
    scannedItems: List<Pair<String, Pair<Int, List<Int>>>>,
    username: String,
    depot: String,
    onBack: () -> Unit,
    navigateToFinalCalculation: (String, String, String, String, List<Pair<String, Pair<Int, List<Int>>>>) -> Unit
) {
    // Log scannedItems for debugging
    Log.d("PreviewInwardScreen", "Scanned Items: $scannedItems")

    val context = LocalContext.current
    val activity = context as? ComponentActivity
    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

    val coroutineScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    val prnData = remember { mutableStateListOf<Pair<String, List<String>>>() }
    val thcData = remember { mutableStateListOf<Pair<String, List<String>>>() }
    val excessLrData = remember { mutableStateListOf<String>() }

    var showModal by remember { mutableStateOf(false) }
    var modalContent by remember { mutableStateOf<List<String>>(emptyList()) }
    val processedNumbers = remember { mutableStateListOf<String>() } // Track processed PRNs/THCs

    val sharedViewModel: SharedViewModel = viewModel()

    // New state for MissingLR modal:
    var showMissingModal by remember { mutableStateOf(false) }
    var missingModalTitle by remember { mutableStateOf("") }
    var missingModalContent by remember { mutableStateOf<List<String>>(emptyList()) }

    // A state to track processed excess LR numbers so that we send each only once.
    val processedExcessLrs = remember { mutableStateListOf<String>() }
    // Declare an error state at the top level of your composable.
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(scannedItems) {
        coroutineScope.launch {
            val (prnResults, thcResults, excessLrs) = fetchInwardData(scannedItems)
            prnData.addAll(prnResults)
            thcData.addAll(thcResults)
            excessLrData.addAll(excessLrs)

            isLoading = false
        }
    }

    // Set feature type to INWARD to get the correct scanned data
    sharedViewModel.setFeatureType(SharedViewModel.FeatureType.INWARD)

    Scaffold(topBar = {
        TopAppBar(title = { Text("Preview Inward Screen") }, navigationIcon = {
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

            if (isLoading) {
                // Show Lottie loading animation while fetching data
                Box(
                    modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                ) {
                    LottieAnimationView()
                }
            } else {

                SectionTitle(title = "PRN:")
                ItemList(items = prnData,
                    processedNumbers = processedNumbers,
                    scannedItems = scannedItems,
                    onArrivalClick = { prn ->

                        // Filter scannedItems based on LRNOs associated with the selected PRN
                        val lrnosForPrn = prnData.find { it.first == prn }?.second ?: emptyList()
                        val filteredScannedItems = scannedItems.filter { it.first in lrnosForPrn }

                        navigateToFinalCalculation(
                            "PRN",
                            URLEncoder.encode(prn, StandardCharsets.UTF_8.toString()),
                            username,
                            depot,
                            filteredScannedItems
                        )
                        processedNumbers.add(prn)
                    },
                    onShowClick = { lrnos ->
                        modalContent = lrnos
                        showModal = true
                    },
                    onMissingLRClick = { lrnos ->
                        missingModalContent = lrnos
                        missingModalTitle = "Missing LR for PRN"
                        showMissingModal = true
                    })

                SectionTitle(title = "THC:")
                ItemList(items = thcData,
                    processedNumbers = processedNumbers,
                    scannedItems = scannedItems,
                    onArrivalClick = { thc ->

                        // Filter scannedItems based on LRNOs associated with the selected THC
                        val lrnosForThc = thcData.find { it.first == thc }?.second ?: emptyList()
                        val filteredScannedItems = scannedItems.filter { it.first in lrnosForThc }

                        navigateToFinalCalculation(
                            "THC",
                            URLEncoder.encode(thc, StandardCharsets.UTF_8.toString()),
                            username,
                            depot,
                            filteredScannedItems
                        )
                        processedNumbers.add(thc)
                    },
                    onShowClick = { lrnos ->
                        modalContent = lrnos
                        showModal = true
                    },
                    onMissingLRClick = { lrnos ->
                        missingModalContent = lrnos
                        missingModalTitle = "Missing LR for THC"
                        showMissingModal = true
                    })

                SectionTitle(title = "Excess LR:")
                ExcessLRList(excessLrData)

                // Automatically send each Excess LR data to the PHP backend.
                LaunchedEffect(excessLrData) {
                    excessLrData.forEach { lr ->
                        if (!processedExcessLrs.contains(lr)) {
                            // Find the scanned record for this LR.
                            val scannedRecord = scannedItems.find { it.first == lr }
                            if (scannedRecord != null) {
                                val totalPkg = scannedRecord.second.first
                                val scannedBoxes = scannedRecord.second.second
                                val scannedCount = scannedBoxes.size
                                val totalDiff = totalPkg - scannedCount

                                // Create a comma-separated list of scanned boxes.
                                val scannedItemsStr = scannedBoxes.joinToString(",")

                                // Compute missing items (if any)
                                val expectedBoxes = (1..totalPkg).toList()
                                val missingBoxes = expectedBoxes.filter { it !in scannedBoxes }
                                val missingItemsStr =
                                    if (missingBoxes.isNotEmpty()) missingBoxes.joinToString(",") else ""

                                // Determine the ExcessLrType.
                                // (In your app, you might determine this dynamically.
                                // Here, we assume "PRN" as an example.)
                                val excessLrType = "PRN"
                                val excessFeatureType = "INWARD" // since this is the inward screen

                                // Call the helper function to send the data.
                                sendExcessLRData(lr = lr,
                                    scannedCount = scannedBoxes.size,
                                    totalDiff = totalDiff,
                                    missingItemsStr = missingItemsStr,
                                    username = username,
                                    depot = depot,
                                    excessLrType = excessLrType,
                                    excessFeatureType = excessFeatureType,
                                    onError = { error ->
                                        // Update error state on error
                                        errorMessage = error
                                    })
                            }
                            processedExcessLrs.add(lr)
                        }
                    }
                }

                // Then, display an AlertDialog when errorMessage is not null:
                if (errorMessage != null) {
                    AlertDialog(onDismissRequest = { errorMessage = null },
                        title = { Text("Error") },
                        text = { Text(errorMessage ?: "") },
                        confirmButton = {
                            Button(onClick = { errorMessage = null }) {
                                Text("Close")
                            }
                        })
                }

                if (showModal) {
                    LRModal(lrnos = modalContent,
                        scannedItems = scannedItems,
                        onDismiss = { showModal = false })
                }

                if (showMissingModal) {
                    MissingLRModal(title = missingModalTitle,
                        lrnos = missingModalContent,
                        scannedItems = scannedItems,
                        onDismiss = { showMissingModal = false })
                }

            }
        }
    }
}

suspend fun sendExcessLRData(
    lr: String,
    scannedCount: Int,
    totalDiff: Int,
    missingItemsStr: String,
    username: String,
    depot: String,
    excessLrType: String,
    excessFeatureType: String,
    onError: (String) -> Unit
) {
    val client = OkHttpClient()
    val url = "https://vtc3pl.com/insert_excess_lr.php"

    val formBody =
        FormBody.Builder().add("ScanUser", username).add("ScanDepot", depot).add("LRNO", lr)
            .add("ScannedItems", scannedCount.toString()).add("TotalDiff", totalDiff.toString())
            .add("MissingItems", missingItemsStr).add("ExcessLrType", excessLrType)
            .add("ExcessFeatureType", excessFeatureType).build()

    val request = Request.Builder().url(url).post(formBody).build()

    withContext(Dispatchers.IO) {
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorMsg =
                        "Failed to send data for LR: $lr. Response code: ${response.code}"
                    Log.e("sendExcessLRData", errorMsg)
                    onError(errorMsg) // trigger error callback
                } else {
                    Log.d("sendExcessLRData", "Successfully sent data for LR: $lr")
                }
            }
        } catch (e: Exception) {
            val errorMsg = "Exception while sending data for LR: $lr, error: ${e.message}"
            Log.e("sendExcessLRData", errorMsg)
            onError(errorMsg) // trigger error callback
        }
    }
}


@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(8.dp)
    )
}

@Composable
fun ItemList(
    items: List<Pair<String, List<String>>>,
    scannedItems: List<Pair<String, Pair<Int, List<Int>>>>,
    processedNumbers: List<String>,
    onArrivalClick: (String) -> Unit,
    onShowClick: (List<String>) -> Unit,
    onMissingLRClick: (List<String>) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        items(items) { (number, lrnos) ->
            val isProcessed = processedNumbers.contains(number)

            // Compute if any LR in this group is missing:
            val hasMissing = lrnos.any { lrno ->
                val scanned = scannedItems.find { it.first == lrno }
                if (scanned != null) {
                    val totalPkgs = scanned.second.first
                    val scannedCount = scanned.second.second.size
                    totalPkgs > scannedCount  // missing if scannedCount is less than required
                } else {
                    true // if no scanned record exists, assume it's missing
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("Number: $number")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = { onMissingLRClick(lrnos) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (hasMissing) Color.Red else MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("Miss")
                        }
                        Button(onClick = { onShowClick(lrnos) }) {
                            Text("Show")
                        }
                        Button(onClick = { onArrivalClick(number) }, enabled = !isProcessed) {
                            Text("Arrival")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExcessLRList(excessLrData: List<String>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        items(excessLrData) { lr ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp)
            ) {
                Text(
                    text = "Excess LR: $lr", modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

@Composable
fun LRModal(
    lrnos: List<String>,
    scannedItems: List<Pair<String, Pair<Int, List<Int>>>>,
    onDismiss: () -> Unit
) {
    AlertDialog(onDismissRequest = onDismiss, confirmButton = {
        Button(onClick = onDismiss) {
            Text("Close")
        }
    }, text = {
        Column {
            // Table Headers
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text(
                    text = "LRNO",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Qty",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            HorizontalDivider()

            // Display LRNO and Qty
            LazyColumn {
                items(lrnos) { lrno ->
                    val qty = scannedItems.find { it.first == lrno }?.second?.first ?: 0
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        Text(
                            text = lrno, modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = qty.toString(), modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    })
}

@Composable
fun MissingLRModal(
    title: String,
    lrnos: List<String>,
    scannedItems: List<Pair<String, Pair<Int, List<Int>>>>,
    onDismiss: () -> Unit
) {
    AlertDialog(onDismissRequest = onDismiss, confirmButton = {
        Button(onClick = onDismiss) {
            Text("Close")
        }
    }, title = { Text(title) }, text = {
        Column {
            // Table Headers
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text(
                    text = "LRNO",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Missing Items",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            HorizontalDivider()
            LazyColumn {
                items(lrnos) { lrno ->
                    // Find the scanned record for this LRNO.
                    val scannedItem = scannedItems.find { it.first == lrno }

                    // Compute missing list as comma separated string.
                    val missingListText = if (scannedItem != null) {
                        val totalPkgs = scannedItem.second.first
                        val scannedBoxes = scannedItem.second.second
                        // Generate the complete list of expected box numbers (assumes numbering starts at 1).
                        val expectedBoxes = (1..totalPkgs).toList()
                        // Determine which expected boxes are missing.
                        val missingBoxes = expectedBoxes.filter { it !in scannedBoxes }
                        if (missingBoxes.isNotEmpty()) missingBoxes.joinToString(", ") else ""
                    } else {
                        "Not Scanned"
                    }

                    // Only show rows where something is missing (or not scanned).
                    if (missingListText.isNotEmpty() && missingListText != "0") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                        ) {
                            Text(text = lrno, modifier = Modifier.weight(1f))
                            Text(text = missingListText, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    })
}

suspend fun fetchInwardData(
    scannedItems: List<Pair<String, Pair<Int, List<Int>>>>
): Triple<List<Pair<String, List<String>>>, List<Pair<String, List<String>>>, List<String>> {
    val client = OkHttpClient()
    val url = "https://vtc3pl.com/fetch_and_find_inward_data_PRN_THC_app.php"

    val jsonArray = JSONArray()
    scannedItems.forEach { (lrno, _) ->
        jsonArray.put(lrno)
    }

    val requestBody = jsonArray.toString().toRequestBody("application/json".toMediaTypeOrNull())
    val request = Request.Builder().url(url).post(requestBody).build()

    return withContext(Dispatchers.IO) {
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: ""
                    val jsonResponse = JSONObject(responseBody)

                    val prnResults = jsonResponse.getJSONArray("prn").let { prnArray ->
                        (0 until prnArray.length()).map {
                            val prnObject = prnArray.getJSONObject(it)
                            prnObject.getString("prnNumber") to prnObject.getJSONArray("lrnos")
                                .let { lrnoArray ->
                                    (0 until lrnoArray.length()).map { idx ->
                                        lrnoArray.getString(idx)
                                    }
                                }
                        }
                    }

                    val thcResults = jsonResponse.getJSONArray("thc").let { thcArray ->
                        (0 until thcArray.length()).map {
                            val thcObject = thcArray.getJSONObject(it)
                            thcObject.getString("thcNumber") to thcObject.getJSONArray("lrnos")
                                .let { lrnoArray ->
                                    (0 until lrnoArray.length()).map { idx ->
                                        lrnoArray.getString(idx)
                                    }
                                }
                        }
                    }

                    val excessLrs = jsonResponse.getJSONArray("excess").let { excessArray ->
                        (0 until excessArray.length()).map { excessArray.getString(it) }
                    }

                    Triple(prnResults, thcResults, excessLrs)
                } else {
                    Triple(emptyList(), emptyList(), emptyList())
                }
            }
        } catch (e: Exception) {
            Triple(emptyList(), emptyList(), emptyList())
        }
    }
}