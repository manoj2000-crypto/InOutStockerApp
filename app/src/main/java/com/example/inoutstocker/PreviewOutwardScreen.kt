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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@SuppressLint("SourceLockedOrientationActivity")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewOutwardScreen(
    sharedViewModel: SharedViewModel = viewModel(),
    username: String,
    depot: String,
    onBack: () -> Unit
) {
    // Lock orientation to portrait
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

    // Set feature type to OUTWARD to fetch outward scanned data
    sharedViewModel.setFeatureType(SharedViewModel.FeatureType.OUTWARD)

    val outwardScannedData = sharedViewModel.scannedItems

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
        }
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