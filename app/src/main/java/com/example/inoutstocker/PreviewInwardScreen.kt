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
import androidx.compose.ui.res.painterResource
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
import com.google.gson.Gson
import okhttp3.FormBody
import java.util.concurrent.TimeUnit

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
    Log.d("PreviewInwardScreen", "Scanned Items: $scannedItems")

    val context = LocalContext.current
    val activity = context as? ComponentActivity
    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

    val coroutineScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    val prnData = remember { mutableStateListOf<Pair<String, List<String>>>() }
    val thcData = remember { mutableStateListOf<Pair<String, List<String>>>() }
    val drsData = remember { mutableStateListOf<Pair<String, List<String>>>() }
    val excessLrData = remember { mutableStateListOf<String>() }

    var showModal by remember { mutableStateOf(false) }
    var modalContent by remember { mutableStateOf<List<String>>(emptyList()) }
    val processedNumbers = remember { mutableStateListOf<String>() } // Track processed PRNs/THCs

    val sharedViewModel: SharedViewModel = viewModel()

    var showMissingModal by remember { mutableStateOf(false) }
    var missingModalTitle by remember { mutableStateOf("") }
    var missingModalContent by remember { mutableStateOf<List<String>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val missingStatusMap = remember { mutableStateMapOf<String, Boolean>() }

    // Get the list of LR numbers that have been processed from our repository
    val processedLRNos = ProcessedItemsRepository.getProcessedLRNos()
    // Filter out the processed LR numbers from the excess list
    val filteredExcessLrData = excessLrData.filterNot { it in processedLRNos }

    LaunchedEffect(scannedItems) {
        coroutineScope.launch {
            val (prnResults, thcResults, drsResults, excessLrs) = fetchInwardData(
                scannedItems, username, depot
            )
            prnData.addAll(prnResults)
            thcData.addAll(thcResults)
            drsData.addAll(drsResults)
            excessLrData.addAll(excessLrs)

            isLoading = false
        }
    }

    LaunchedEffect(prnData, scannedItems) {
        prnData.forEach { (token, lrnos) ->
            val lrDetails = fetchLRDetailsForToken(token = token, type = "PRN")
            val hasMissingFromFetched = lrnos.any { lrno ->
                val detail = lrDetails.find { it.lrno == lrno }
                // If detail exists then compare expected versus scanned.
                val scannedRecord = scannedItems.find { it.first == lrno }
                if (detail != null) {
                    if (scannedRecord != null) {
                        val scannedBoxes = scannedRecord.second.second
                        (1..detail.totalPkg).toList().any { it !in scannedBoxes }
                    } else {
                        true
                    }
                } else {
                    // If we did not fetch details, fall back to scannedItems.
                    if (scannedRecord != null) {
                        val totalPkgs = scannedRecord.second.first
                        scannedRecord.second.second.size < totalPkgs
                    } else {
                        true
                    }
                }
            }
            missingStatusMap[token] = hasMissingFromFetched
        }
    }

    LaunchedEffect(thcData, scannedItems) {
        thcData.forEach { (token, lrnos) ->
            val lrDetails = fetchLRDetailsForToken(token = token, type = "THC")
            val hasMissingFromFetched = lrnos.any { lrno ->
                val detail = lrDetails.find { it.lrno == lrno }
                val scannedRecord = scannedItems.find { it.first == lrno }
                if (detail != null) {
                    if (scannedRecord != null) {
                        val scannedBoxes = scannedRecord.second.second
                        (1..detail.totalPkg).toList().any { it !in scannedBoxes }
                    } else {
                        true
                    }
                } else {
                    if (scannedRecord != null) {
                        val totalPkgs = scannedRecord.second.first
                        scannedRecord.second.second.size < totalPkgs
                    } else {
                        true
                    }
                }
            }
            missingStatusMap[token] = hasMissingFromFetched
        }
    }

    sharedViewModel.setFeatureType(SharedViewModel.FeatureType.INWARD)

    var showArrivalConfirmation by remember { mutableStateOf(false) }
    var pendingArrivalData by remember { mutableStateOf<ArrivalData?>(null) }

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
                ItemList(
                    items = prnData,
                    processedNumbers = processedNumbers,
                    missingStatusMap = missingStatusMap,
                    scannedItems = scannedItems,
                    onArrivalClick = { token, lrnos ->
                        coroutineScope.launch {
                            val lrDetails = fetchLRDetailsForToken(token = token, type = "PRN")
                            val hasMissingFromFetched = lrnos.any { lrno ->
                                val detail = lrDetails.find { it.lrno == lrno }
                                val scannedRecord = scannedItems.find { it.first == lrno }
                                if (detail != null) {
                                    if (scannedRecord != null) {
                                        val scannedBoxes = scannedRecord.second.second
                                        (1..detail.totalPkg).toList().any { it !in scannedBoxes }
                                    } else {
                                        true
                                    }
                                } else {
                                    true
                                }
                            }
                            missingStatusMap[token] = hasMissingFromFetched

                            if (hasMissingFromFetched) {
                                // Build a new scannedItems list with missing LR(s) “completed”
//                                val modifiedScannedItems = lrnos.mapNotNull { lrno ->
//                                    val detail = lrDetails.find { it.lrno == lrno }
//                                    if (detail != null) {
//                                        Pair(
//                                            lrno,
//                                            Pair(detail.totalPkg, (1..detail.totalPkg).toList())
//                                        )
//                                    } else {
//                                        scannedItems.find { it.first == lrno }
//                                    }
//                                }
                                val modifiedScannedItems = lrnos.mapNotNull { lrno ->
                                    scannedItems.find { it.first == lrno }
                                }
                                pendingArrivalData = ArrivalData(
                                    type = "PRN",
                                    number = token,
                                    scannedItems = modifiedScannedItems,
                                    lrnos = lrnos
                                )
                                showArrivalConfirmation = true
                            } else {
                                navigateToFinalCalculation(
                                    "PRN",
                                    URLEncoder.encode(token, StandardCharsets.UTF_8.toString()),
                                    username,
                                    depot,
                                    scannedItems.filter { it.first in lrnos })
                                processedNumbers.add(token)
                            }
                        }
                    },
                    onShowClick = { lrnos ->
                        modalContent = lrnos
                        showModal = true
                    },
                    onMissingLRClick = { token, lrnos ->
                        coroutineScope.launch {
                            val lrDetails = fetchLRDetailsForToken(token = token, type = "PRN")
                            val details: List<Pair<String, String>> = lrDetails.map { detail ->
                                val scannedRecord = scannedItems.find { it.first == detail.lrno }
                                if (scannedRecord == null) {
                                    Log.d("MissingLR", "LRNO not scanned at all: ${detail.lrno}")
                                }
                                val missing = if (scannedRecord != null) {
                                    val scannedBoxes = scannedRecord.second.second
                                    val expectedBoxes = (1..detail.totalPkg).toList()
                                    val missingBoxes = expectedBoxes.filter { it !in scannedBoxes }
                                    if (missingBoxes.isNotEmpty()) missingBoxes.joinToString(",") else "None"
                                } else {
                                    (1..detail.totalPkg).joinToString(",")
                                }
                                detail.lrno to missing
                            }.filter { (_, missing) ->
                                missing != "None"
                            }
                            missingModalContent =
                                details.map { (lrno, missing) -> "$lrno: $missing" }
                            missingModalTitle = "Missing LR for PRN"
                            showMissingModal = true
                        }
                    })

                SectionTitle(title = "THC:")
                ItemList(
                    items = thcData,
                    processedNumbers = processedNumbers,
                    missingStatusMap = missingStatusMap,
                    scannedItems = scannedItems,
                    onArrivalClick = { token, lrnos ->
                        coroutineScope.launch {
                            val lrDetails = fetchLRDetailsForToken(token = token, type = "THC")
                            val hasMissingFromFetched = lrnos.any { lrno ->
                                val detail = lrDetails.find { it.lrno == lrno }
                                val scannedRecord = scannedItems.find { it.first == lrno }
                                if (detail != null) {
                                    if (scannedRecord != null) {
                                        val scannedBoxes = scannedRecord.second.second
                                        (1..detail.totalPkg).toList().any { it !in scannedBoxes }
                                    } else {
                                        true
                                    }
                                } else {
                                    true
                                }
                            }
                            missingStatusMap[token] = hasMissingFromFetched

                            if (hasMissingFromFetched) {
                                // Build a “complete” scanned record for missing LR(s)
//                                val modifiedScannedItems = lrnos.mapNotNull { lrno ->
//                                    val detail = lrDetails.find { it.lrno == lrno }
//                                    if (detail != null) {
//                                        Pair(
//                                            lrno,
//                                            Pair(detail.totalPkg, (1..detail.totalPkg).toList())
//                                        )
//                                    } else {
//                                        scannedItems.find { it.first == lrno }
//                                    }
//                                }
                                val modifiedScannedItems = lrnos.mapNotNull { lrno ->
                                    scannedItems.find { it.first == lrno }
                                }
                                pendingArrivalData = ArrivalData(
                                    type = "THC",
                                    number = token,
                                    scannedItems = modifiedScannedItems,
                                    lrnos = lrnos
                                )
                                showArrivalConfirmation = true
                            } else {
                                navigateToFinalCalculation(
                                    "THC",
                                    URLEncoder.encode(token, StandardCharsets.UTF_8.toString()),
                                    username,
                                    depot,
                                    scannedItems.filter { it.first in lrnos })
                                processedNumbers.add(token)
                            }
                        }
                    },
                    onShowClick = { lrnos ->
                        modalContent = lrnos
                        showModal = true
                    },
                    onMissingLRClick = { token, lrnos ->
                        coroutineScope.launch {
                            val lrDetails = fetchLRDetailsForToken(token = token, type = "THC")
                            val details: List<Pair<String, String>> = lrDetails.map { detail ->
                                val scannedRecord = scannedItems.find { it.first == detail.lrno }
                                val missing = if (scannedRecord != null) {
                                    val scannedBoxes = scannedRecord.second.second
                                    val expectedBoxes = (1..detail.totalPkg).toList()
                                    val missingBoxes = expectedBoxes.filter { it !in scannedBoxes }
                                    if (missingBoxes.isNotEmpty()) missingBoxes.joinToString(",") else "None"
                                } else {
                                    (1..detail.totalPkg).joinToString(",")
                                }
                                detail.lrno to missing
                            }.filter { (_, missing) ->
                                missing != "None"
                            }
                            missingModalContent =
                                details.map { (lrno, missing) -> "$lrno: $missing" }
                            missingModalTitle = "Missing LR for THC"
                            showMissingModal = true
                        }
                    })

                // Add this after the THC section and before the Excess LR section.
                SectionTitle(title = "DRS:")
                ItemList(
                    items = drsData,
                    processedNumbers = processedNumbers,
                    missingStatusMap = missingStatusMap,
                    scannedItems = scannedItems,
                    onArrivalClick = { token, lrnos ->
                        coroutineScope.launch {
                            Log.i("DRS: ", "DRS ARRIVAL BUTTON IS CLICKED.")
                            // WE HAVE TO CREATE A NEW PAGE FOR THIS BECAUSE FOR THIS USER WILL ONLY SELECT THE OPTIONS AND ENTER THE REASON
                            //AND SUBMIT THE LR WISE BUTTON SO THAT LR WILL BE IN 'DETENTION'.
                            // TAKE REFERENCE FORM THESE FILE : detention.php
//                            navigateToFinalCalculation(
//                                "DRS",
//                                URLEncoder.encode(token, StandardCharsets.UTF_8.toString()),
//                                username,
//                                depot,
//                                scannedItems.filter { it.first in lrnos })
                            processedNumbers.add(token)
                        }
                    },
                    onShowClick = { lrnos ->
                        modalContent = lrnos
                        showModal = true
                    },
                    onMissingLRClick = { token, lrnos ->
                        // Optionally, add missing LR handling for DRS if applicable.
                    })

                SectionTitle(title = "Excess LR:")
                ExcessLRList(excessLrData = filteredExcessLrData)

                LaunchedEffect(filteredExcessLrData, sharedViewModel.processedExcessLrs) {
                    val computedExcessLrType = when {
                        prnData.isNotEmpty() && thcData.isNotEmpty() -> "PRN_THC"
                        prnData.isNotEmpty() -> "PRN"
                        thcData.isNotEmpty() -> "THC"
                        else -> "NONE"
                    }

                    val uniqueExcessLrs = filteredExcessLrData.distinct()
                    Log.i("PreviewInwardScreen : ", "uniqueExcessLrs : $uniqueExcessLrs")

                    // Filter out LRNOs that already exist in the processed list.
                    val newExcessLRs = uniqueExcessLrs.filterNot { lr ->
                        lr in sharedViewModel.processedExcessLrs
                    }
                    Log.d("ExcessLRInfo", "Filtered New Excess LR Numbers: $newExcessLRs")

                    if (newExcessLRs.isNotEmpty()) {
                        val excessLRInfoList = newExcessLRs.mapNotNull { lr ->
                            scannedItems.find { it.first == lr }?.let { scannedRecord ->
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
                                excessFeatureType = "INWARD",
                                onError = { error -> errorMessage = error },
                                onSuccess = {
                                    newExcessLRs.forEach { lr ->
                                        sharedViewModel.addProcessedExcessLr(lr)
                                        Log.i("LRNO inserted in sharedViewModel", "LRNO:  $lr")
                                    }
                                })
                        }
                    }
                }

                if (errorMessage != null) {
                    AlertDialog(
                        onDismissRequest = { errorMessage = null },
                        title = { Text("Error") },
                        text = { Text(errorMessage ?: "") },
                        confirmButton = {
                            Button(onClick = { errorMessage = null }) {
                                Text("Close")
                            }
                        })
                }

                if (showModal) {
                    LRModal(
                        lrnos = modalContent,
                        scannedItems = scannedItems,
                        onDismiss = { showModal = false })
                }

                if (showMissingModal) {
                    MissingLRModal(
                        title = missingModalTitle,
                        missingDetails = missingModalContent,
                        onDismiss = { showMissingModal = false })
                }

            }

            if (showArrivalConfirmation && pendingArrivalData != null) {
                AlertDialog(onDismissRequest = {
                    showArrivalConfirmation = false
                    pendingArrivalData = null
                }, title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.warning_icon),
                            contentDescription = "Warning",
                            tint = Color.Red,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Confirmation")
                    }
                }, text = { Text("Do you want to continue?") }, confirmButton = {
                    Button(onClick = {
                        pendingArrivalData?.let { data ->
                            navigateToFinalCalculation(
                                data.type, URLEncoder.encode(
                                    data.number, StandardCharsets.UTF_8.toString()
                                ), username, depot, data.scannedItems
                            )
                            processedNumbers.add(data.number)
                        }
                        showArrivalConfirmation = false
                        pendingArrivalData = null
                    }) {
                        Text("Yes")
                    }
                }, dismissButton = {
                    Button(onClick = {
                        showArrivalConfirmation = false
                        pendingArrivalData = null
                    }) {
                        Text("No")
                    }
                })
            }

        }
    }
}

suspend fun fetchInwardData(
    scannedItems: List<Pair<String, Pair<Int, List<Int>>>>, username: String, depot: String
): InwardDataResult {
//    val client = OkHttpClient()
    val client = OkHttpClient.Builder().connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS).build()
    // val url = "https://vtc3pl.com/fetch_and_find_inward_data_PRN_THC_app.php"
    val url = "https://vtc3pl.com/fetch_and_find_inward_data_PRN_THC_DRS_app.php"

    val jsonObject = JSONObject().apply {
        put("username", username)
        put("depot", depot)
        val lrnosArray = JSONArray()
        scannedItems.forEach { (lrno, _) ->
            lrnosArray.put(lrno)
        }
        put("lrnos", lrnosArray)
    }

    val requestBody = jsonObject.toString().toRequestBody("application/json".toMediaTypeOrNull())
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

                    // FOR DRS
                    val drsResults = jsonResponse.getJSONArray("drs").let { drsArray ->
                        (0 until drsArray.length()).map {
                            val drsObject = drsArray.getJSONObject(it)
                            drsObject.getString("drsNumber") to drsObject.getJSONArray("lrnos")
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

                    InwardDataResult(prnResults, thcResults, drsResults, excessLrs)
                } else {
                    InwardDataResult(emptyList(), emptyList(), emptyList(), emptyList())
                }
            }
        } catch (e: Exception) {
            Log.e("fetchInwardData", "Error fetching inward data: ${e.message}")
            InwardDataResult(emptyList(), emptyList(), emptyList(), emptyList())
        }
    }
}

//suspend fun fetchInwardData(
//    scannedItems: List<Pair<String, Pair<Int, List<Int>>>>, username: String, depot: String
//): Triple<List<Pair<String, List<String>>>, List<Pair<String, List<String>>>, List<Pair<String, List<String>>>, List<String>> {
//    val client = OkHttpClient()
////    val url = "https://vtc3pl.com/fetch_and_find_inward_data_PRN_THC_app.php"
//    val url = "https://vtc3pl.com/fetch_and_find_inward_data_PRN_THC_DRS_app.php"
//
//    val jsonObject = JSONObject().apply {
//        put("username", username)
//        put("depot", depot)
//        val lrnosArray = JSONArray()
//        scannedItems.forEach { (lrno, _) ->
//            lrnosArray.put(lrno)
//        }
//        put("lrnos", lrnosArray)
//    }
//
//    val requestBody = jsonObject.toString().toRequestBody("application/json".toMediaTypeOrNull())
//    val request = Request.Builder().url(url).post(requestBody).build()
//
//    return withContext(Dispatchers.IO) {
//        try {
//            client.newCall(request).execute().use { response ->
//                if (response.isSuccessful) {
//                    val responseBody = response.body?.string() ?: ""
//                    val jsonResponse = JSONObject(responseBody)
//
//                    val prnResults = jsonResponse.getJSONArray("prn").let { prnArray ->
//                        (0 until prnArray.length()).map {
//                            val prnObject = prnArray.getJSONObject(it)
//                            prnObject.getString("prnNumber") to prnObject.getJSONArray("lrnos")
//                                .let { lrnoArray ->
//                                    (0 until lrnoArray.length()).map { idx ->
//                                        lrnoArray.getString(idx)
//                                    }
//                                }
//                        }
//                    }
//
//                    val thcResults = jsonResponse.getJSONArray("thc").let { thcArray ->
//                        (0 until thcArray.length()).map {
//                            val thcObject = thcArray.getJSONObject(it)
//                            thcObject.getString("thcNumber") to thcObject.getJSONArray("lrnos")
//                                .let { lrnoArray ->
//                                    (0 until lrnoArray.length()).map { idx ->
//                                        lrnoArray.getString(idx)
//                                    }
//                                }
//                        }
//                    }
//
//                    //FOR DRS
//                    val drsResults = jsonResponse.getJSONArray("drs").let { drsArray ->
//                        (0 until drsArray.length()).map {
//                            val drsObject = drsArray.getJSONObject(it)
//                            drsObject.getString("drsNumber") to drsObject.getJSONArray("lrnos")
//                                .let { lrnoArray ->
//                                    (0 until lrnoArray.length()).map { idx ->
//                                        lrnoArray.getString(idx)
//                                    }
//                                }
//                        }
//                    }
//
//                    val excessLrs = jsonResponse.getJSONArray("excess").let { excessArray ->
//                        (0 until excessArray.length()).map { excessArray.getString(it) }
//                    }
//
//                    Triple(prnResults, thcResults, drsResults, excessLrs)
//                } else {
//                    Triple(emptyList(), emptyList(), emptyList(), emptyList())
//                }
//            }
//        } catch (e: Exception) {
//            Triple(emptyList(), emptyList(), emptyList(), emptyList())
//        }
//    }
//}

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
    missingStatusMap: Map<String, Boolean>,
    onArrivalClick: (number: String, lrnos: List<String>) -> Unit,
    onShowClick: (List<String>) -> Unit,
    onMissingLRClick: (token: String, lrnos: List<String>) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        items(items) { (number, lrnos) ->
            val isProcessed = processedNumbers.contains(number)
            val hasMissing = (missingStatusMap[number] == true) || lrnos.any { lrno ->
                val scanned = scannedItems.find { it.first == lrno }
                scanned == null || (scanned.second.second.size < scanned.second.first)
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
                            onClick = { onMissingLRClick(number, lrnos) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (hasMissing) Color.Red else MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("Miss")
                        }
                        Button(onClick = { onShowClick(lrnos) }) {
                            Text("Show")
                        }
                        Button(
                            onClick = { onArrivalClick(number, lrnos) }, enabled = !isProcessed
                        ) {
                            Text("Arrival")
                        }
                    }
                }
            }
        }
    }
}

suspend fun fetchLRDetailsForToken(token: String, type: String): List<LRDetails> {
    val url = when (type) {
        "PRN" -> "https://vtc3pl.com/fetch_lr_details_for_prn_inoutstockerapp.php"
        "THC" -> "https://vtc3pl.com/fetch_lr_details_for_thc_inoutstockerapp.php"
        "DRS" -> "https://vtc3pl.com/fetch_lr_details_for_drs_inoutstockerapp.php"
        else -> return emptyList()
    }

    val jsonBody = JSONObject().apply {
        put("token", token)
    }.toString().toRequestBody("application/json".toMediaTypeOrNull())

    val request = Request.Builder().url(url).post(jsonBody).build()

    val client = OkHttpClient()
    return withContext(Dispatchers.IO) {
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: "[]"
                    val jsonArray = JSONArray(responseBody)
                    (0 until jsonArray.length()).map { idx ->
                        val obj = jsonArray.getJSONObject(idx)
                        LRDetails(
                            lrno = obj.getString("LRNO"), totalPkg = obj.getInt("PkgsNo")
                        )
                    }
                } else {
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e("fetchLRDetailsForToken", "Error fetching LR details: ${e.message}")
            emptyList()
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

suspend fun sendExcessLRData(
    excessLRInfoList: List<ExcessLRInfo>,
    username: String,
    depot: String,
    excessLrType: String,
    excessFeatureType: String,
    onError: (String) -> Unit,
    onSuccess: () -> Unit
) {
    val client = OkHttpClient()
    val url = "https://vtc3pl.com/insert_excess_lr.php"

    val gson = Gson()
    val jsonData = gson.toJson(excessLRInfoList)

    val formBody = FormBody.Builder().add("ScanUser", username).add("ScanDepot", depot)
        .add("ExcessLrType", excessLrType).add("ExcessFeatureType", excessFeatureType)
        .add("excessLRData", jsonData).build()

    val request = Request.Builder().url(url).post(formBody).build()

    withContext(Dispatchers.IO) {
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorMsg = "Failed to send batch data. Response code: ${response.code}"
                    Log.e("sendExcessLRData", errorMsg)
                    onError(errorMsg)
                } else {
                    Log.d("sendExcessLRData", "Successfully sent batch data")
                    onSuccess()
                }
            }
        } catch (e: Exception) {
            val errorMsg = "Exception while sending batch data: ${e.message}"
            Log.e("sendExcessLRData", errorMsg)
            onError(errorMsg)
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
    title: String, missingDetails: List<String>, onDismiss: () -> Unit
) {
    AlertDialog(onDismissRequest = onDismiss, confirmButton = {
        Button(onClick = onDismiss) {
            Text("Close")
        }
    }, title = { Text(title) }, text = {
        Column {
            if (missingDetails.isEmpty()) {
                Text(
                    text = "Nothing is missing! Everything is fine. You can proceed with arrival without worry.",
                    color = Color.Green,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(8.dp)
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Text(
                        text = "LRNO and Missing Items",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                HorizontalDivider()
                LazyColumn {
                    items(missingDetails) { detail ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                        ) {
                            Text(text = detail)
                        }
                    }
                }
            }
        }
    })
}

data class ArrivalData(
    val type: String, // "PRN" or "THC"
    val number: String,
    val scannedItems: List<Pair<String, Pair<Int, List<Int>>>>,
    val lrnos: List<String>
)

data class LRDetails(
    val lrno: String, val totalPkg: Int
)

data class InwardDataResult(
    val prnResults: List<Pair<String, List<String>>>,
    val thcResults: List<Pair<String, List<String>>>,
    val drsResults: List<Pair<String, List<String>>>,
    val excessLrs: List<String>
)