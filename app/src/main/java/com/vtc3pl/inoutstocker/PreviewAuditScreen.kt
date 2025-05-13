package com.vtc3pl.inoutstocker

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.graphics.Color.GREEN
import android.graphics.Color.RED
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SourceLockedOrientationActivity")
@Composable
fun PreviewAuditScreen(
    scannedItems: List<Pair<String, Pair<Int, List<Int>>>>,
    username: String,
    depot: String,
    onBack: () -> Unit,
    onBackToHome: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

    val coroutineScope = rememberCoroutineScope()
    val previewData = remember { mutableStateListOf<Triple<String, Int, List<Int>>>() }
    val remarks = remember { mutableStateOf("") }
    val errorMessage = remember { mutableStateOf<String?>(null) }
    val successMessage = remember { mutableStateOf<String?>(null) }
    val isLoading = remember { mutableStateOf(false) }

    LaunchedEffect(scannedItems) {
        scannedItems.forEach { (lrno, pair) ->
            val (totalPkgs, boxes) = pair
            val missingBoxes = (1..totalPkgs).filter { it !in boxes }
            previewData.add(Triple(lrno, totalPkgs, missingBoxes))
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Preview Screen") }, navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        })
    }) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                items(previewData) { (lrno, totalPkgs, missingBoxes) ->
                    val cardColor = if (missingBoxes.isEmpty()) Color(GREEN) else Color(RED)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = cardColor),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("LRNO: $lrno", color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                "Total Packages: $totalPkgs",
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (missingBoxes.isEmpty()) "No missing boxes" else "Missing Boxes: ${
                                    missingBoxes.joinToString(
                                        ", "
                                    )
                                }", color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }


                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }

                item {
                    // Remark Field
                    TextField(
                        value = remarks.value,
                        onValueChange = { remarks.value = it },
                        label = { Text("Remarks") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    )
                }

                item {
                    errorMessage.value?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (isLoading.value) {
                            LottieAnimationView()
                        } else {
                            Button(
                                onClick = {
                                    if (remarks.value.isBlank()) {
                                        errorMessage.value = "Remark cannot be empty."
                                        return@Button
                                    }
                                    coroutineScope.launch {
                                        isLoading.value = true
                                        try {
                                            delay(500)
                                            val response = sendDataToServer(
                                                previewData, remarks.value, username, depot
                                            )
                                            isLoading.value = false
                                            if (response.first) {
                                                Log.i(
                                                    "PreviewAuditScreen",
                                                    "Success: ${response.second}"
                                                )
                                                successMessage.value = response.second
                                                errorMessage.value = null
                                            } else {
                                                Log.i(
                                                    "PreviewAuditScreen",
                                                    "Error: ${response.second}"
                                                )
                                                errorMessage.value = response.second
                                                successMessage.value = null
                                            }
                                        } catch (e: Exception) {
                                            isLoading.value = false
                                            Log.i("PreviewAuditScreen", "Exception: ${e.message}")
                                            errorMessage.value = "An error occurred: ${e.message}"
                                            successMessage.value = null
                                        }
                                    }
                                }, modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Text("Save")
                            }
                        }
                    }
                }

            }

            if (successMessage.value != null) {
                AlertDialog(onDismissRequest = { onBackToHome() }, confirmButton = {
                    TextButton(onClick = { onBackToHome() }) {
                        Text("OK")
                    }
                }, title = { Text("Success") }, text = { Text(successMessage.value ?: "") })
            }

        }
    }
}

@Composable
fun LottieAnimationView() {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.simple_loading_animation))
    val progress by animateLottieCompositionAsState(composition)

    LottieAnimation(
        composition = composition, progress = { progress }, modifier = Modifier.size(100.dp)
    )
}

suspend fun sendDataToServer(
    previewData: List<Triple<String, Int, List<Int>>>,
    remarks: String,
    username: String,
    depot: String
): Pair<Boolean, String> {
    val client = OkHttpClient()
    val url = "https://vtc3pl.com/save_stock_audit_inoutstocker_app.php"

    val jsonArray = JSONArray()
    previewData.forEach { (lrno, totalPkgs, missingBoxes) ->
        val scannedBox = totalPkgs - missingBoxes.size
        val jsonObject = JSONObject()
        jsonObject.put("lrno", lrno)
        jsonObject.put("missingBoxes", JSONArray(missingBoxes))
        jsonObject.put("totalDiff", missingBoxes.size)
        jsonObject.put("scannedBox", scannedBox)
        jsonObject.put("boxDiff", missingBoxes.size)
        jsonObject.put("remarks", remarks)
        jsonObject.put("username", username)
        jsonObject.put("depot", depot)
        jsonArray.put(jsonObject)
    }

    val jsonString = jsonArray.toString()
    Log.i("sendDataToServer", "Request JSON: $jsonString")
    val requestBody: RequestBody = jsonString.toRequestBody("application/json".toMediaTypeOrNull())

    val request =
        Request.Builder().url(url).post(requestBody).addHeader("Content-Type", "application/json")
            .build()

    return withContext(Dispatchers.IO) {
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Pair(true, "Data saved successfully")
                } else {
                    Pair(false, "Failed to save data: ${response.code} - ${response.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("sendDataToServer", "Exception: ${e.message}", e)
            Pair(false, "Network error: ${e.message}")
        }
    }
}