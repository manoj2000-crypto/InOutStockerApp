package com.example.inoutstocker

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.util.Log
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@SuppressLint("SourceLockedOrientationActivity")
@Composable
fun AuditScreen(
    username: String, depot: String, onPreview: () -> Unit, sharedViewModel: SharedViewModel
) {
    //Stopping user to change the orientation from vertical to portrait
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

    val scannedData = remember { mutableStateOf("") }
//    val scannedItems = remember { mutableStateListOf<Pair<String, Pair<Int, List<Int>>>>() }
    val isLoading = remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Barcode Scanner Section with dynamic height adjustment
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .padding(bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading.value) {
                    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.simple_loading_animation))
                    LottieAnimation(
                        composition, modifier = Modifier
                            .size(350.dp)
                            .align(Alignment.Center)
                    )
                } else {
                    BarcodeScanner(modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp), // Dynamic height passed here
                        onBarcodeScanned = { data ->
                            coroutineScope.launch {
                                isLoading.value = true
                                delay(2000) // Pause scanner for 2 seconds
                                isLoading.value = false

                                val parsedData = parseScannedData(data)
                                parsedData?.let { (lrno, pkgsNo, boxNo) ->
                                    sharedViewModel.addScannedItem(lrno, pkgsNo, boxNo)
                                    scannedData.value = data
                                    Log.d("AuditScreen", "Scanned Data: $data")
                                }
                            }
                        })
                }
            }

            // Spacer to create space between the barcode scanner and the table header
            Spacer(modifier = Modifier.height(150.dp))

            // Table Header - Visible only if there are scanned items
            if (sharedViewModel.scannedItems.isNotEmpty()) {
                // Table Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "LRNO", modifier = Modifier
                            .weight(1f)
                            .padding(8.dp), color = Color.White
                    )
                    Text(
                        "PkgNo", modifier = Modifier
                            .weight(1f)
                            .padding(8.dp), color = Color.White
                    )
                    Text(
                        "BoxNo", modifier = Modifier
                            .weight(1f)
                            .padding(8.dp), color = Color.White
                    )
                }
            }

            // Tabular Data
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                items(sharedViewModel.scannedItems) { (lrno, pair) ->
                    val (totalPkgs, boxes) = pair
                    val isComplete = boxes.size == totalPkgs
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isComplete) Color.Green else Color.White
                        ),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(lrno, modifier = Modifier.weight(1f))
                            Text("$totalPkgs", modifier = Modifier.weight(1f))
                            Text(boxes.joinToString(", "), modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f)) // Push buttons to the bottom
            // Button - Visible only if there are scanned items
            if (sharedViewModel.scannedItems.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = { onPreview() }, modifier = Modifier.weight(1f)
                    ) {
                        Text("Preview")
                    }
                }
            }
        }
    }
}

private fun parseScannedData(data: String): Triple<String, Int, Int>? {
    // Example data: "LRNO=PNA0001009366;PkgsNo=28;BoxNo=1;"
    val map = data.split(";").mapNotNull {
        val parts = it.split("=")
        if (parts.size == 2) parts[0] to parts[1] else null
    }.toMap()

    val lrno = map["LRNO"] ?: return null
    val pkgsNo = map["PkgsNo"]?.toIntOrNull() ?: return null
    val boxNo = map["BoxNo"]?.toIntOrNull() ?: return null

    return Triple(lrno, pkgsNo, boxNo)
}