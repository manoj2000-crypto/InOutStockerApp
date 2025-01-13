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
import androidx.compose.ui.Modifier
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
    val prnData = remember { mutableStateListOf<Pair<String, List<String>>>() }
    val thcData = remember { mutableStateListOf<Pair<String, List<String>>>() }
    val excessLrData = remember { mutableStateListOf<String>() }

    var showModal by remember { mutableStateOf(false) }
    var modalContent by remember { mutableStateOf<List<String>>(emptyList()) }

    val sharedViewModel: SharedViewModel = viewModel()

    LaunchedEffect(scannedItems) {
        coroutineScope.launch {
            val (prnResults, thcResults, excessLrs) = fetchInwardData(scannedItems)
            prnData.addAll(prnResults)
            thcData.addAll(thcResults)
            excessLrData.addAll(excessLrs)
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
            SectionTitle(title = "PRN:")
            ItemList(items = prnData, onArrivalClick = { prn ->

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
            }, onShowClick = { lrnos ->
                modalContent = lrnos
                showModal = true
            })

            SectionTitle(title = "THC:")
            ItemList(items = thcData, onArrivalClick = { thc ->

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
            }, onShowClick = { lrnos ->
                modalContent = lrnos
                showModal = true
            })

            SectionTitle(title = "Excess LR:")
            ExcessLRList(excessLrData)

            if (showModal) {
                LRModal(lrnos = modalContent, onDismiss = { showModal = false })
            }
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
    onArrivalClick: (String) -> Unit,
    onShowClick: (List<String>) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        items(items) { (number, lrnos) ->
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
                        Button(onClick = { onArrivalClick(number) }) {
                            Text("Arrival")
                        }
                        Button(onClick = { onShowClick(lrnos) }) {
                            Text("Show")
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
fun LRModal(lrnos: List<String>, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, confirmButton = {
        Button(onClick = onDismiss) {
            Text("Close")
        }
    }, text = {
        LazyColumn {
            items(lrnos) { lrno ->
                Text(text = lrno, modifier = Modifier.padding(4.dp))
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