package com.example.inoutstocker

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.inoutstocker.utils.parseScannedData
import androidx.navigation.NavHostController


@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SourceLockedOrientationActivity")
@Composable
fun AuditScreen(
    navController: NavHostController,
    username: String,
    depot: String,
    onPreview: () -> Unit,
    sharedViewModel: SharedViewModel
) {
    //Stopping user to change the orientation from vertical to portrait
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

    // State to track which view to display
    var scanMode by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "AUDIT SCREEN",
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                }, colors = TopAppBarDefaults.centerAlignedTopAppBarColors()
            )
        }) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Display buttons if no scan mode is selected
            if (scanMode == null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = { scanMode = "camera" },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text("Scan Using Mobile Camera")
                    }
                    Button(
                        onClick = { scanMode = "bluetooth" },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text("Scan Using Bluetooth Device")
                    }
                }
            } else {
                // Show content based on selected scan mode
                when (scanMode) {
                    "camera" -> {
                        CameraScanView(
                            sharedViewModel = sharedViewModel,
                            onPreview = onPreview,
                            callerContext = "AUDIT"
                        )
                    }

                    "bluetooth" -> {
                        ComingSoonView(
                            sharedViewModel = sharedViewModel,
                            onPreview = {
                                // exactly what you do for camera mode:
                                sharedViewModel.setFeatureType(SharedViewModel.FeatureType.AUDIT)
                                navController.navigate("previewAuditScreen/$username/$depot")
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComingSoonView(
    sharedViewModel: SharedViewModel, onPreview: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    var text by remember { mutableStateOf("") }
    var uniqueList by remember { mutableStateOf<List<Triple<String, Int, Int>>>(emptyList()) }
    var isButtonEnabled by remember { mutableStateOf(true) }

    // request focus as soon as the Composable enters composition
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        TextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Scan data here") },
            singleLine = true,
            modifier = Modifier
                .width(1.dp)
                .height(1.dp)
                .focusRequester(focusRequester)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            enabled = isButtonEnabled,
            onClick = {
                // 1. Split on each "LRNO=" drop blanks
                val parsed = text.split("LRNO=").filter { it.isNotBlank() }.map { "LRNO=$it" }
                    .mapNotNull { parseScannedData(it) }

                // 2. Remove duplicates by (LRNO, BoxNo)
                uniqueList = parsed.distinctBy { (lrno, _, box) ->
                    lrno to box
                }

                // 3. Log each unique entry
                uniqueList.forEach { (lrno, pkgs, box) ->
                    sharedViewModel.addScannedItem(lrno, pkgs, box)
                    Log.d("ComingSoonView", "LRNO=$lrno; PkgsNo=$pkgs; BoxNo=$box")
                }
                text = ""
                isButtonEnabled = false
            }, modifier = Modifier.fillMaxWidth()
        ) {
            Text("SHOW DATA")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (uniqueList.isNotEmpty()) {
            Text(
                "Scanned Items (${uniqueList.size}):", style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                items(uniqueList) { (lrno, pkgs, box) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(lrno, style = MaterialTheme.typography.bodyLarge)
                            Text("Pkgs: $pkgs", style = MaterialTheme.typography.bodyMedium)
                            Text("Box: $box", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onPreview,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text("Preview")
                    }
                }

            }
        }
    }
}