package com.example.inoutstocker

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.graphics.Color.GREEN
import android.graphics.Color.RED
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SourceLockedOrientationActivity")
@Composable
fun PreviewPrnScreen(
    scannedItems: List<Pair<String, Pair<Int, List<Int>>>>,
    username: String,
    depot: String,
    onBack: () -> Unit,
    onBackToHome: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    val previewData = remember { mutableStateListOf<Triple<String, Int, List<Int>>>() }

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
            }
        }
    }
}