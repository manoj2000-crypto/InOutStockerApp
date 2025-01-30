package com.example.inoutstocker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinalCalculationForOutwardScreen(
    username: String,
    depot: String,
    loadingSheetNo: String,
    totalQty: Int,
    totalWeight: Double,
    sharedViewModel: SharedViewModel,
    onBack: () -> Unit
) {
    val outwardScannedData = sharedViewModel.outwardScannedData
    var totalAmount by remember { mutableIntStateOf(0) }
    var deductionAmount by remember { mutableStateOf("") }
    var finalAmount by remember { mutableIntStateOf(totalAmount) }
    var hamaliVendorName by remember { mutableStateOf("") }
    var hamaliType by remember { mutableStateOf("") }

    // Fetch total weight and quantity from scanned data
    LaunchedEffect(outwardScannedData) {
        totalAmount = calculateTotalAmount(totalQty, totalWeight)
        finalAmount = totalAmount - (deductionAmount.toIntOrNull() ?: 0)
    }

    // Fetch hamali rates based on selected vendor and type
    LaunchedEffect(hamaliVendorName, hamaliType) {
        if (hamaliVendorName.isNotEmpty() && hamaliType.isNotEmpty()) {
            fetchHamaliRates(
                hamaliVendorName, depot
            ) { regular, crossing, regularBag, crossingBag ->
                val rate = when (hamaliType) {
                    "REGULAR" -> if (regular > 0) regular else regularBag
                    "CROSSING" -> if (crossing > 0) crossing else crossingBag
                    else -> 0.0
                }

                // Update totalAmount based on the rates
                totalAmount = calculateTotalAmount(totalQty, totalWeight, rate)
                finalAmount = totalAmount - (deductionAmount.toIntOrNull() ?: 0)
            }
        }
    }

    // Recalculate finalAmount whenever deductionAmount changes
    LaunchedEffect(deductionAmount) {
        finalAmount = totalAmount - (deductionAmount.toIntOrNull() ?: 0)
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Final Calculation") })
    }) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Text("Username: $username") }
            item { Text("Depot: $depot") }
            item { Text("Loading Sheet No: $loadingSheetNo") }
            item { Text("Total Quantity: $totalQty") }
            item { Text("Total Weight: $totalWeight") }
//            item { Text("Outward Scanned Data: $outwardScannedData") }
            // Hamali Vendor Dropdown
            item {
                HamaliVendorDropdown(
                    selectedOption = hamaliVendorName,
                    onOptionSelected = { hamaliVendorName = it },
                    depot = depot
                )
            }

            // Hamali Type Dropdown
            item {
                DropdownMenuField(label = "Hamali Type",
                    options = listOf("REGULAR", "CROSSING"),
                    selectedOption = hamaliType,
                    onOptionSelected = { hamaliType = it })
            }

            // Display Amounts and Deduction
            item {
                TextField(
                    value = totalAmount.toString(),
                    onValueChange = {},
                    label = { Text("Total Amount") },
                    enabled = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                TextField(
                    value = deductionAmount,
                    onValueChange = { deductionAmount = it },
                    label = { Text("Deduction Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                TextField(
                    value = finalAmount.toString(),
                    onValueChange = {},
                    label = { Text("Final Amount") },
                    enabled = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Box(
                    modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = {
                            // Submit button logic
                            onBack() // navigate back
                        }, modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Submit")
                    }
                }
            }
        }
    }
}

// Calculate Total Amount (implement based on your outward logic)
fun calculateTotalAmount(
    totalQty: Int, totalWeight: Double, rate: Double = 0.0
): Int {
    val totalPkgAmount = totalQty * rate
    return (totalPkgAmount + totalWeight).toInt()
}