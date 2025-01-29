package com.example.inoutstocker

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

@SuppressLint("SourceLockedOrientationActivity")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewOutwardScreen(
    sharedViewModel: SharedViewModel = viewModel(),
    username: String,
    depot: String,
    loadingSheetNo: String,
    onBack: () -> Unit
) {
    // Lock orientation to portrait
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

    // Set feature type to OUTWARD to fetch outward scanned data
    sharedViewModel.setFeatureType(SharedViewModel.FeatureType.OUTWARD)

    val outwardScannedData = sharedViewModel.scannedItems

    val drsLoadingSheets = loadingSheetNo.split(",").filter { it.startsWith("LSD") }
    val thcLoadingSheets = loadingSheetNo.split(",").filter { it.startsWith("LST") }

    var drsLrnos by remember { mutableStateOf(emptyList<String>()) }
    var thcLrnos by remember { mutableStateOf(emptyList<String>()) }
    val outwardScannedLrnos = outwardScannedData.map { it.first }

    LaunchedEffect(loadingSheetNo) {
        drsLrnos = fetchLrnosFromServer(drsLoadingSheets, "fetch_drs_lrnos.php")
        thcLrnos = fetchLrnosFromServer(thcLoadingSheets, "fetch_thc_lrnos.php")
    }

    val excessLrnos = outwardScannedLrnos.filter { it !in drsLrnos && it !in thcLrnos }


    Log.d("PreviewOutwardScreen", "Received LoadingSheetNo: $loadingSheetNo")

    Log.d("PreviewOutwardScreen", "Outward Scanned Data: $outwardScannedData")

    Scaffold(topBar = {
        TopAppBar(title = { Text("Preview Outward Scans") }, navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        })
    }) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text("Scanned LR Numbers", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))

            if (outwardScannedData.isEmpty()) {
                Text("No data available.", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn {
                    items(outwardScannedData) { (lrno, pkgNoPair) ->
                        val (pkgNo, scannedBoxes) = pkgNoPair
                        OutwardItem(lrno, pkgNo, scannedBoxes)
                    }
                }
            }

            Text("DRS LR Numbers", style = MaterialTheme.typography.headlineSmall)
            LazyColumn {
                items(drsLrnos) { lrno -> Text("LRNO: $lrno") }
            }

            Text("THC LR Numbers", style = MaterialTheme.typography.headlineSmall)
            LazyColumn {
                items(thcLrnos) { lrno -> Text("LRNO: $lrno") }
            }

            Text("Excess LR Numbers", style = MaterialTheme.typography.headlineSmall)
            LazyColumn {
                items(excessLrnos) { lrno -> Text("LRNO: $lrno", color = Color.Red) }
            }

        }
    }
}

private val client = OkHttpClient()
suspend fun fetchLrnosFromServer(loadingSheets: List<String>, endpoint: String): List<String> {
    if (loadingSheets.isEmpty()) return emptyList()

    val url = "https://vtc3pl.com/$endpoint"
    val requestBody =
        FormBody.Builder().add("loadingSheetNos", loadingSheets.joinToString(",")).build()

    val request = Request.Builder().url(url).post(requestBody).build()

    return try {
        withContext(Dispatchers.IO) {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e("PreviewOutwardScreen", "Server Error: ${response.code}")
                return@withContext emptyList()
            }

            val responseBody = response.body?.string()
            if (responseBody.isNullOrEmpty()) {
                Log.e("PreviewOutwardScreen", "Empty response body")
                return@withContext emptyList()
            }

            Log.d("PreviewOutwardScreen", "Response: $responseBody")

            // Parse JSON response
            val jsonArray = JSONArray(responseBody)
            List(jsonArray.length()) { index -> jsonArray.getString(index) }
        }
    } catch (e: Exception) {
        Log.e("PreviewOutwardScreen", "Error fetching LRNOs: ${e.message}")
        emptyList()
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