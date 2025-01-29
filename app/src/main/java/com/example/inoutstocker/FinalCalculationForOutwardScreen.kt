package com.example.inoutstocker

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun FinalCalculationForOutwardScreen(
    username: String,
    depot: String,
    loadingSheetNo: String,
    totalQty: Int,
    totalWeight: Double,
    outwardScannedData: List<OutwardScannedData>,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Final Calculation", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(16.dp))

        Text("Username: $username")
        Text("Depot: $depot")
        Text("Loading Sheet No: $loadingSheetNo")
        Text("Total Quantity: $totalQty")
        Text("Total Weight: $totalWeight")

        Spacer(modifier = Modifier.height(16.dp))

        // Display outwardScannedData (for example, show the data in a list or use it for further calculations)
        Text("Outward Scanned Data: $outwardScannedData")

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Submit")
        }
    }
}