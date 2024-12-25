package com.example.inoutstocker

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

@Composable
fun InwardScreen(navController: NavController, username: String, depot: String) {
    val coroutineScope = rememberCoroutineScope()
    var selectedOption by remember { mutableStateOf("PRN") }
    var fromDate by remember { mutableStateOf("") }
    var toDate by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    var tableData by remember { mutableStateOf<List<TableRowData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var showTable by remember { mutableStateOf(false) }

    val calendar = Calendar.getInstance()
    val context = LocalContext.current

    fun showDatePicker(context: android.content.Context, onDateSelected: (String) -> Unit) {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                onDateSelected("$year-${month + 1}-$dayOfMonth")
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    val isFormValid = (fromDate.isNotEmpty() && toDate.isNotEmpty()) || number.isNotEmpty()

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Box Inward",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Radio Buttons for PRN and THC
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selectedOption == "PRN", onClick = {
                    if (selectedOption != "PRN") {
                        selectedOption = "PRN"
                        tableData = emptyList()
                        showTable = false
                    }
                })
                Text(text = "PRN", modifier = Modifier.padding(end = 16.dp))

                RadioButton(selected = selectedOption == "THC", onClick = {
                    if (selectedOption != "THC") {
                        selectedOption = "THC"
                        tableData = emptyList()
                        showTable = false
                    }
                })
                Text(text = "THC")
            }

            // Input fields for From Date, To Date
            OutlinedTextField(value = fromDate,
                onValueChange = {},
                label = { Text("From Date") },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                trailingIcon = {
                    IconButton(onClick = { showDatePicker(context) { fromDate = it } }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Select From Date")
                    }
                })

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(value = toDate,
                onValueChange = {},
                label = { Text("To Date") },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                trailingIcon = {
                    IconButton(onClick = { showDatePicker(context) { toDate = it } }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Select To Date")
                    }
                })

            Spacer(modifier = Modifier.height(16.dp))

            // Improved "OR" label
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "OR",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Input field for Number (PRN or THC No)
            OutlinedTextField(value = number,
                onValueChange = { number = it },
                label = { Text(if (selectedOption == "PRN") "PRN" else "THC No") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Fetch Data Button
            Button(
                onClick = {
                    coroutineScope.launch {
                        isLoading = true
                        tableData = fetchData(selectedOption, number, fromDate, toDate)
                        delay(2000)
                        isLoading = false
                        showTable = true
                    }
                }, modifier = Modifier.fillMaxWidth(), enabled = isFormValid
            ) {
                Text("Fetch Records")
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else if (showTable) {
                // Display Data in Cards
                TableView(tableData, navController, username, depot)
            }
        }
    }
}

@Composable
fun TableView(
    data: List<TableRowData>, navController: NavController, username: String, depot: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        data.forEachIndexed { index, row ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                elevation = CardDefaults.cardElevation(8.dp),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "SrNo: ${index + 1}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Number: ${if (row.number.isNotEmpty()) row.number else "N/A"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Date: ${row.date}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Vehicle No: ${row.vehicleNo}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Horizontal Button for Arrival
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                navController.navigate("ScanningScreen/${row.number}")
                            }, modifier = Modifier.width(120.dp)
                        ) {
                            Text("Arrival")
                        }
                    }
                }
            }
        }
    }
}

suspend fun fetchData(
    option: String, number: String, fromDate: String, toDate: String
): List<TableRowData> {
    // Replace with actual network call using OkHttp
    return listOf(
        TableRowData("123", "2024-12-24", "MH12AB1234"),
        TableRowData("124", "2024-12-25", "MH12CD5678")
    )
}

data class TableRowData(val number: String, val date: String, val vehicleNo: String)