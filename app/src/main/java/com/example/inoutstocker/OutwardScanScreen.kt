package com.example.inoutstocker

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@SuppressLint("SourceLockedOrientationActivity")
@Composable
fun OutwardScanScreen(
    navController: NavController,
    username: String,
    depot: String,
    loadingSheetNo: String,
    onPreview: () -> Unit,
    sharedViewModel: SharedViewModel
) {
    //Stopping user to change the orientation from vertical to portrait
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

    // State to track which view to display
    var scanMode by remember { mutableStateOf<String?>(null) }

    // Use loadingSheetNo as needed
    Log.d("OutwardScanScreen", "Received LoadingSheetNo: $loadingSheetNo")

    Scaffold { paddingValues ->
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
                            sharedViewModel = sharedViewModel, onPreview = onPreview
                        )
                    }

                    "bluetooth" -> {
                        ComingSoonView()
                    }
                }
            }
        }
    }
}