package com.example.inoutstocker

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SourceLockedOrientationActivity")
@Composable
fun PrnOutwardScreen(
    navController: NavController,
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
                        text = "PRN OUTWARD SCREEN",
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
                            callerContext = "PRN_OUTWARD"
                        )
                    }

                    "bluetooth" -> {
                        ComingSoonView(
                            sharedViewModel = sharedViewModel, onPreview = {
                                sharedViewModel.setFeatureType(SharedViewModel.FeatureType.PRN_OUTWARD)
                                navController.navigate("previewPrnScreen/$username/$depot")
                            })
                    }

                }
            }
        }
    }
}