package com.example.inoutstocker

import android.app.DatePickerDialog
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.Calendar

@Composable
fun OutwardScreen(
    navController: NavController, username: String, depot: String, sharedViewModel: SharedViewModel
) {
    val scannedData = remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    var fromDate by remember { mutableStateOf(sharedViewModel.fromDate) }
    var toDate by remember { mutableStateOf(sharedViewModel.toDate) }
    var number by remember { mutableStateOf("") }
    var tableData by remember { mutableStateOf(sharedViewModel.tableData) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var showTable by remember { mutableStateOf(sharedViewModel.tableData.isNotEmpty()) }

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
                text = "Outward",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Input fields for From Date, To Date
            OutlinedTextField(
                value = fromDate,
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

            OutlinedTextField(
                value = toDate,
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

            Row(
                modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                )
                Text(
                    text = "OR",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Input field for Loading sheet Number
            OutlinedTextField(
                value = number,
                onValueChange = { number = it },
                label = { Text("Loading Sheet Number") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                trailingIcon = {
                    if (number.isNotEmpty()) {
                        IconButton(onClick = { number = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear Number")
                        }
                    }
                })

            Spacer(modifier = Modifier.height(16.dp))

            // Fetch Data Button
            Button(
                onClick = {
                    coroutineScope.launch {
                        isLoading = true
                        errorMessage = null
                        showTable = false
                        val result = fetchData(number, fromDate, toDate, username, depot)
                        isLoading = false
                        result.onSuccess { data ->
                            if (data.isEmpty()) {
                                errorMessage = "Nothing found !!! Please select another date range."
                            } else {
                                tableData = data
                                sharedViewModel.updateTableData(data)
                                sharedViewModel.setDates(fromDate, toDate)
                                showTable = true
                            }
                        }.onFailure { error ->
                            errorMessage = error.message
                        }
                    }
                }, modifier = Modifier.fillMaxWidth(), enabled = isFormValid
            ) {
                Text("Fetch Records")
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else if (errorMessage != null) {
                Text(
                    text = errorMessage ?: "",
                    color = Color.Red,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp)
                )
            } else if (showTable) {
                TableView(tableData, navController, username, depot)
            }
        }
    }
}

@Composable
fun TableView(
    data: List<TableRowData>, navController: NavController, username: String, depot: String
) {
    // Group data by groupCode
    val groupedData = data.groupBy { row -> row.groupCode }

    Column(modifier = Modifier.fillMaxWidth()) {
        groupedData.forEach { (groupCode, rows) ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.medium
                    ),
                elevation = CardDefaults.cardElevation(8.dp),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Display common data
                    val commonVehicleNo = rows.firstOrNull()?.vehicleNo ?: "N/A"
                    val commonDriverName = rows.firstOrNull()?.driverName ?: "N/A"
                    val commonDriverContact = rows.firstOrNull()?.driverContact ?: "N/A"

                    LabelWithValue(label = "Vehicle No", value = commonVehicleNo)
                    Spacer(modifier = Modifier.height(4.dp))
                    LabelWithValue(label = "Driver Name", value = commonDriverName)
                    Spacer(modifier = Modifier.height(4.dp))
                    LabelWithValue(label = "Driver Contact", value = commonDriverContact)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Display unique data for each row in the group
                    rows.forEachIndexed { index, row ->
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LabelWithValue(label = "Loading Sheet No", value = row.loadingSheetNo)
                        Spacer(modifier = Modifier.height(4.dp))
                        LabelWithValue(label = "Loading Sheet Date", value = row.loadingSheetDate)
                        Spacer(modifier = Modifier.height(4.dp))
                        LabelWithValue(label = "Quantity", value = row.quantity.toString())
                        Spacer(modifier = Modifier.height(4.dp))
                        LabelWithValue(label = "Weight", value = row.weight.toString())
                        Spacer(modifier = Modifier.height(4.dp))
                        LabelWithValue(label = "DRS or THC", value = row.drsOrThc)
                        Spacer(modifier = Modifier.height(4.dp))
                        LabelWithValue(label = "Location", value = row.location)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Collect all loadingSheetNo for this group
                    val loadingSheetNos = rows.joinToString(",") { it.loadingSheetNo }

                    // Add the "Submit" button
                    Box(
                        modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center
                    ) {
                        Button(onClick = {
                            val encodedGroupCode = Uri.encode(groupCode)
                            // Handle Submit button click here
                            Log.d("TableView", "Submit clicked for Group Code: $groupCode")
                            navController.navigate("outwardScanScreen/$username/$depot/$loadingSheetNos/$encodedGroupCode") // Navigate to OutwardScanScreen
                        }) {
                            Text(text = "Submit")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LabelWithValue(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = value, style = MaterialTheme.typography.bodyMedium
        )
    }
}

suspend fun fetchData(
    number: String, fromDate: String, toDate: String, username: String, depot: String
): Result<List<TableRowData>> {
    return withContext(Dispatchers.IO) {
        val client = OkHttpClient()
        val url = "https://vtc3pl.com/fetch_inward_outward_search_details.php"
        val json = JSONObject().apply {
            put("number", number)
            put("username", username)
            put("depot", depot)
            put("fromDate", fromDate)
            put("toDate", toDate)
        }
        val requestBody = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder().url(url).post(requestBody).build()

        try {
            client.newCall(request).execute().use { response ->
                Log.i("fetchData", "Request URL: $url")
                Log.i("fetchData", "Request Body: ${json.toString()}")

                if (!response.isSuccessful) {
                    Log.i("fetchData", "Response Code: ${response.code}")
                    return@withContext Result.failure(IOException("Unexpected response code: ${response.code}"))
                }

                val responseBody = response.body?.string()
                if (responseBody == null) {
                    Log.i("fetchData", "Empty response body")
                    return@withContext Result.failure(IOException("Empty response body"))
                }
                Log.i("fetchData", "Response Body: $responseBody")

                val jsonResponse = JSONObject(responseBody)
                if (jsonResponse.getBoolean("success")) {
                    val data = jsonResponse.getJSONArray("data")
                    val tableData = List(data.length()) {
                        val item = data.getJSONObject(it)
                        TableRowData(
                            groupCode = item.getString("groupcodevpd"),
                            drsOrThc = item.getString("drsorthc"),
                            loadingSheetNo = item.getString("lsno"),
                            loadingSheetDate = item.getString("lsdate"),
                            quantity = item.getInt("qty"),
                            weight = item.getDouble("weight"),
                            vehicleNo = item.getString("vehicleno"),
                            driverName = item.getString("drivername"),
                            driverContact = item.getString("driverno"),
                            arrivalDate = item.getString("arrivaldate"),
                            location = item.getString("location")
                        )
                    }
                    Log.i("fetchData", "Parsed Table Data: $tableData")
                    Result.success(tableData)
                } else {
                    val errorMessage = jsonResponse.optString("message", "Unknown error occurred")
                    Log.i("fetchData", "Error Message: $errorMessage")
                    Result.failure(IOException(errorMessage))
                }
            }
        } catch (e: Exception) {
            Log.e("fetchData", "Exception: ${e.message}", e)
            Result.failure(e)
        }
    }
}

data class TableRowData(
    val groupCode: String,
    val drsOrThc: String,
    val loadingSheetNo: String,
    val loadingSheetDate: String,
    val quantity: Int,
    val weight: Double,
    val vehicleNo: String,
    val driverName: String,
    val driverContact: String,
    val arrivalDate: String,
    val location: String
)