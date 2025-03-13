package com.example.inoutstocker

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.stream.JsonReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.StringReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinalDRSInward(
    prnOrThc: String,
    drs: String,
    username: String,
    depot: String,
    scannedItems: List<Pair<String, Pair<Int, List<Int>>>>,
    onBack: () -> Unit
) {

    val (drsDetails, setDrsDetails) = remember { mutableStateOf<List<DRSDetail>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(key1 = drs) {
        try {
            val response = fetchDRSDetails(drs, username, depot)
            setDrsDetails(response.data)
            Log.d("FinalDRSInward", "Return options: ${response.options}")
        } catch (e: Exception) {
            Log.e("FinalDRSInward", "Error fetching DRS details", e)
        } finally {
            isLoading = false
        }
    }

    // Wrap the content in a Scaffold with a black background
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
                }
            }
        }
    }

    Log.d(
        "FinalDRSInward",
        "prnOrThc: $prnOrThc, prn: $drs, username: $username, depot: $depot, scannedItems: $scannedItems"
    )
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