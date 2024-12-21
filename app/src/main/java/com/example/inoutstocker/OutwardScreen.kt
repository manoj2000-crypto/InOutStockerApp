package com.example.inoutstocker

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun OutwardScreen(username: String, depot: String) {
    val scannedData = remember { mutableStateOf("") }

    Scaffold { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            if (scannedData.value.isEmpty()) {
                BarcodeScanner(onBarcodeScanned = { data ->
                    scannedData.value = data
                })
            } else {
                Text(text = "Scanned Data: ${scannedData.value}\nUser: $username\nDepot: $depot")
            }
        }
    }
}