@file:Suppress("DEPRECATION")

package com.vtc3pl.inoutstocker

import android.media.SoundPool
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.accompanist.placeholder.PlaceholderHighlight
import com.google.accompanist.placeholder.material.placeholder
import com.google.accompanist.placeholder.material.shimmer
import com.vtc3pl.inoutstocker.utils.parseScannedData
import kotlinx.coroutines.launch

@Composable
fun CameraScanView(sharedViewModel: SharedViewModel, onPreview: () -> Unit, callerContext: String) {
    val scannedData = remember { mutableStateOf("") }
//    val isLoading = remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // SoundPool for Beep Sound
    val soundPool = remember {
        SoundPool.Builder().setMaxStreams(1) // Only one sound at a time
            .build()
    }
    val beepSoundId = remember {
        soundPool.load(context, R.raw.beep_sound, 1)
    }

    DisposableEffect(Unit) {
        onDispose {
            soundPool.release() // Release resources when the composable is disposed
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Barcode Scanner Section with dynamic height adjustment
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .requiredHeight(220.dp)
                .padding(bottom = 8.dp)
        ) {
//            if (isLoading.value) {
//                val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.simple_loading_animation))
//                LottieAnimation(
//                    composition, modifier = Modifier
//                        .size(350.dp)
//                        .align(Alignment.Center)
//                )
//            } else {
            BarcodeScanner(
                modifier = Modifier.fillMaxSize(), onBarcodeScanned = { data ->
                    coroutineScope.launch {
//                            isLoading.value = true
                        // Play beep sound using SoundPool
                        soundPool.play(beepSoundId, 0.3f, 0.3f, 1, 0, 1f)

//                            delay(100) // Pause scanner for 100 milliseconds (0.1) seconds for much faster scanning.
//                            isLoading.value = false

                        val parsedData = parseScannedData(data)
                        parsedData?.let { (lrno, pkgsNo, boxNo) ->
                            sharedViewModel.addScannedItem(lrno, pkgsNo, boxNo)
                            scannedData.value = data
                            Log.d("CameraScanView", "Scanned Data: $data")
                        }
                    }
                }, callerContext = callerContext, sharedViewModel = sharedViewModel
            )
//            }
        }

        // Spacer to create space between the barcode scanner and the table header
        Spacer(modifier = Modifier.height(8.dp))

        // If there is no scanned data, show the skeleton (placeholder) UI.
        if (sharedViewModel.scannedItems.isEmpty()) {
            // --- Skeleton Table Header ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Each Box below acts as a placeholder for the header text.
                Box(
                    modifier = Modifier
                        .weight(1.6f)
                        .padding(8.dp)
                        .placeholder(
                            visible = true,
                            highlight = PlaceholderHighlight.shimmer(),
                            color = Color.LightGray
                        )
                )
                Box(
                    modifier = Modifier
                        .weight(0.7f)
                        .padding(8.dp)
                        .placeholder(
                            visible = true,
                            highlight = PlaceholderHighlight.shimmer(),
                            color = Color.LightGray
                        )
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(8.dp)
                        .placeholder(
                            visible = true,
                            highlight = PlaceholderHighlight.shimmer(),
                            color = Color.LightGray
                        )
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(8.dp)
                        .placeholder(
                            visible = true,
                            highlight = PlaceholderHighlight.shimmer(),
                            color = Color.LightGray
                        )
                )
            }

            // --- Skeleton Tabular Data & Preview Button ---
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                // Show a fixed number of skeleton rows (e.g., 1)
                items(1) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.LightGray
                        ),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(2f)
                                    .padding(8.dp)
                                    .placeholder(
                                        visible = true,
                                        highlight = PlaceholderHighlight.shimmer(),
                                        color = Color.Gray
                                    )
                            )
                            Box(
                                modifier = Modifier
                                    .weight(0.7f)
                                    .padding(8.dp)
                                    .placeholder(
                                        visible = true,
                                        highlight = PlaceholderHighlight.shimmer(),
                                        color = Color.Gray
                                    )
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(8.dp)
                                    .placeholder(
                                        visible = true,
                                        highlight = PlaceholderHighlight.shimmer(),
                                        color = Color.Gray
                                    )
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(8.dp)
                                    .placeholder(
                                        visible = true,
                                        highlight = PlaceholderHighlight.shimmer(),
                                        color = Color.Gray
                                    )
                            )
                        }
                    }
                }

                // Skeleton for the preview button
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .placeholder(
                                    visible = true,
                                    highlight = PlaceholderHighlight.shimmer(),
                                    color = Color.Gray
                                )
                        )
                    }
                }
            }
        } else {
            // Table Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "LRNO",
                    modifier = Modifier
                        .weight(1.6f)
                        .padding(8.dp),
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Text(
                    "PkgNo",
                    modifier = Modifier
                        .weight(0.7f)
                        .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Text(
                    "ItemNo",
                    modifier = Modifier
                        .weight(1f)
                        .padding(8.dp),
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Text(
                    "MissNo",
                    modifier = Modifier
                        .weight(1f)
                        .padding(8.dp),
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
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

                    // Compute values based on whether scanning is complete
                    val displayScanned = if (isComplete) "OK" else boxes.joinToString(", ")
                    val boxesStr = boxes.map { it.toString() }
                    val missing = (1..totalPkgs).filter { it.toString() !in boxesStr }
                    val displayMissing = if (isComplete) "-" else missing.joinToString(", ")

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isComplete) Color.Green else Color.Red
                        ),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                lrno,
                                modifier = Modifier
                                    .weight(2f)
                                    .padding(8.dp),
                                textAlign = TextAlign.Start,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Visible
                            )
                            Text(
                                "$totalPkgs",
                                modifier = Modifier
                                    .weight(0.7f)
                                    .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                displayScanned,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(8.dp),
                                textAlign = TextAlign.Center,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                displayMissing,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(8.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
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
    }
}