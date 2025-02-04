package com.example.inoutstocker

import android.media.SoundPool
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import com.example.inoutstocker.utils.parseScannedData
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CameraScanView(sharedViewModel: SharedViewModel, onPreview: () -> Unit) {
    val scannedData = remember { mutableStateOf("") }
    val isLoading = remember { mutableStateOf(false) }
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
                .height(220.dp)
                .padding(bottom = 8.dp)
        ) {
            if (isLoading.value) {
                val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.simple_loading_animation))
                LottieAnimation(
                    composition, modifier = Modifier
                        .size(350.dp)
                        .align(Alignment.Center)
                )
            } else {
                BarcodeScanner(modifier = Modifier.fillMaxSize(), onBarcodeScanned = { data ->
                    coroutineScope.launch {
                        isLoading.value = true
                        // Play beep sound using SoundPool
                        soundPool.play(beepSoundId, 0.3f, 0.3f, 1, 0, 1f)

                        delay(1500) // Pause scanner for 1.5 seconds
                        isLoading.value = false

                        val parsedData = parseScannedData(data)
                        parsedData?.let { (lrno, pkgsNo, boxNo) ->
                            sharedViewModel.addScannedItem(lrno, pkgsNo, boxNo)
                            scannedData.value = data
                            Log.d("CameraScanView", "Scanned Data: $data")
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
                        Text(lrno, modifier = Modifier.weight(1f))
                        Text("$totalPkgs", modifier = Modifier.weight(1f))
                        Text(boxes.joinToString(", "), modifier = Modifier.weight(1f))
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