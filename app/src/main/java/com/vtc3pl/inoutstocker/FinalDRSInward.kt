package com.vtc3pl.inoutstocker

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.stream.JsonReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.StringReader
import androidx.compose.material3.AlertDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinalDRSInward(
    prnOrThc: String,
    drs: String,
    username: String,
    depot: String,
    scannedItems: List<Pair<String, Pair<Int, List<Int>>>>
//    onBack: () -> Unit
) {
    val (drsDetails, setDrsDetails) = remember { mutableStateOf<List<DRSDetail>>(emptyList()) }
    val (returnOptions, setReturnOptions) = remember { mutableStateOf<List<ReturnOption>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var currentScannedItems by remember { mutableStateOf(scannedItems) }

    LaunchedEffect(key1 = drs) {
        try {
            val response = fetchDRSDetails(drs, username, depot)
            setDrsDetails(response.data)
            setReturnOptions(response.options)
            Log.d("FinalDRSInward", "Return options: ${response.options}")
        } catch (e: Exception) {
            Log.e("FinalDRSInward", "Error fetching DRS details", e)
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background, topBar = {
            TopAppBar(
                title = { Text("DRS INWARD", color = MaterialTheme.colorScheme.onBackground) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    items(drsDetails) { detail ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .border(
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    shape = MaterialTheme.shapes.medium
                                ), colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "DRS No: ${detail.drsno}",
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "DRS Date: ${detail.drsdate}",
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "Vehicle No: ${detail.vehicleNo}",
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "Driver Name: ${detail.driverName}",
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "Driver Mobile: ${detail.driverMobile}",
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "Vendor Name: ${detail.vendorName}",
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    item {
                        Text(
                            text = "Scanned Items",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    // Section 2: Scanned Items with LRNO cards.
                    items(currentScannedItems) { item ->
                        val lrno = item.first
                        val scannedCount = item.second.second.size

                        ScannedItemCard(
                            lrno = lrno,
                            scannedCount = scannedCount,
                            returnOptions = returnOptions,
                            drs = drs,
                            username = username,
                            depot = depot,
                            onSubmit = { submittedLrno ->
                                currentScannedItems =
                                    currentScannedItems.filter { it.first != submittedLrno }
                            })
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }

    Log.d(
        "FinalDRSInward",
        "prnOrThc: $prnOrThc, prn: $drs, username: $username, depot: $depot, scannedItems: $scannedItems"
    )
}

@Composable
fun ScannedItemCard(
    lrno: String,
    scannedCount: Int,
    returnOptions: List<ReturnOption>,
    drs: String,
    username: String,
    depot: String,
    onSubmit: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedOption by remember { mutableStateOf<ReturnOption?>(null) }
    var reasonText by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    var showSuccessDialog by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var dialogMessage by remember { mutableStateOf("") }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("LRNO: $lrno", style = MaterialTheme.typography.titleMedium)

            // Dropdown for selecting a Remark.
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedOption?.value ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Select Remark") },
                    modifier = Modifier.fillMaxWidth()
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { expanded = true })
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    returnOptions.forEach { option ->
                        DropdownMenuItem(onClick = {
                            selectedOption = option
                            expanded = false
                        }, text = { Text(text = option.value) })
                    }
                }
            }

            OutlinedTextField(
                value = reasonText,
                onValueChange = { reasonText = it },
                label = { Text("Reason") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )

            Button(
                onClick = {
                    coroutineScope.launch {
                        try {
                            val response = submitDetentionData(
                                lrno = lrno,
                                scannedCount = scannedCount,
                                remark = selectedOption?.key ?: "",
                                reason = reasonText,
                                drs = drs,
                                username = username,
                                depot = depot
                            )
                            if (response.status == "success") {
                                dialogMessage = response.message
                                showSuccessDialog = true
                            } else {
                                dialogMessage = response.message
                                showErrorDialog = true
                                Log.e("ScannedItemCard", "Server error: ${response.message}")
                            }
                        } catch (e: Exception) {
                            dialogMessage = e.message ?: "Unknown error"
                            showErrorDialog = true
                            Log.e("ScannedItemCard", "Submission error: ${e.message}")
                        }
                    }
                },
                enabled = selectedOption != null && reasonText.isNotBlank(),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("Submit")
            }
        }
    }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { /* Prevent dismiss by clicking outside */ },
            title = { Text("Success") },
            text = { Text(dialogMessage) },
            confirmButton = {
                Button(
                    onClick = {
                        showSuccessDialog = false
                        selectedOption = null
                        reasonText = ""
                        onSubmit(lrno)
                    }) {
                    Text("OK")
                }
            })
    }

    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            title = { Text("Error") },
            text = { Text(dialogMessage) },
            confirmButton = {
                Button(
                    onClick = { showErrorDialog = false }) {
                    Text("OK")
                }
            })
    }

}

suspend fun submitDetentionData(
    lrno: String,
    scannedCount: Int,
    remark: String,
    reason: String,
    drs: String,
    username: String,
    depot: String
): DetentionResponse {
    return withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val url = "https://vtc3pl.com/submit_detention_inoutstockerapp.php"
            val formBody =
                FormBody.Builder().add("LRNO", lrno).add("scannedCount", scannedCount.toString())
                    .add("remark", remark).add("reason", reason).add("drs", drs)
                    .add("username", username).add("depot", depot).build()
            val request = Request.Builder().url(url).post(formBody).build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("Network error: ${response.code}")
                }
                val json = response.body?.string() ?: ""
                Gson().fromJson(json, DetentionResponse::class.java)
            }
        } catch (e: Exception) {
            Log.e("submitDetentionData", "Error submitting detention: ${e.message}", e)
            throw e
        }
    }
}

suspend fun fetchDRSDetails(drsNo: String, username: String, depot: String): DRSResponse {
    return withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val url = "https://vtc3pl.com/fetch_detention_data_inoutstockerapp.php"
            val formBody = FormBody.Builder().add("drs_no", drsNo).add("username", username)
                .add("depot", depot).build()
            val request = Request.Builder().url(url).post(formBody).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("fetchDRSDetails", "Network error: ${response.code}")
                    return@withContext DRSResponse(emptyList(), emptyList())
                }
                val json = response.body?.string() ?: "{\"data\":[],\"options\":[]}"
                // Use a lenient JsonReader to handle any malformed JSON
                val reader = JsonReader(StringReader(json))
                reader.isLenient = true
                val gson = Gson()
                gson.fromJson(json, DRSResponse::class.java)
            }
        } catch (e: Exception) {
            Log.e("fetchDRSDetails", "Error fetching DRS details", e)
            DRSResponse(emptyList(), emptyList())
        }
    }
}

data class ReturnOption(
    @SerializedName("key") val key: String, @SerializedName("value") val value: String
)

data class DRSResponse(
    @SerializedName("data") val data: List<DRSDetail>,
    @SerializedName("options") val options: List<ReturnOption>
)

data class DRSDetail(
    @SerializedName("DRSNO") val drsno: String,
    @SerializedName("LRNO") val lrno: String?,
    @SerializedName("Cosigner") val cosigner: String?,
    @SerializedName("Path") val path: String?,
    @SerializedName("DRSdate") val drsdate: String,
    @SerializedName("Uploadtime") val uploadtime: String?,
    @SerializedName("Place") val place: String?,
    @SerializedName("Qty") val qty: String?,
    @SerializedName("Weight") val weight: String?,
    @SerializedName("Consignee") val consignee: String?,
    @SerializedName("BookingDate") val bookingDate: String?,
    @SerializedName("VehicleNo") val vehicleNo: String,
    @SerializedName("DriverName") val driverName: String,
    @SerializedName("DriverMobile") val driverMobile: String,
    @SerializedName("VendorName") val vendorName: String,
    @SerializedName("Delivered") val delivered: String?,
    @SerializedName("DeliveryDate") val deliveryDate: String?
)

data class DetentionResponse(
    val status: String, val message: String
)