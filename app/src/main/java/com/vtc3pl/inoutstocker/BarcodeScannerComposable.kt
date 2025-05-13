package com.vtc3pl.inoutstocker

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@OptIn(ExperimentalGetImage::class)
@Composable
fun BarcodeScanner(
    modifier: Modifier = Modifier,
    onBarcodeScanned: (String) -> Unit,
    callerContext: String,
    sharedViewModel: SharedViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = context as LifecycleOwner
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    var cameraControl: CameraControl? by remember { mutableStateOf(null) }
    var isFlashlightOn by remember { mutableStateOf(false) }
    var isCameraOn by remember { mutableStateOf(true) }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    val cameraManager = remember {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }
    val backCameraId = remember {
        cameraManager.cameraIdList.firstOrNull { id ->
            val characteristics = cameraManager.getCameraCharacteristics(id)
            characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
    }

    // State for showing the delete confirmation dialog
    var showDeleteDialog by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                cameraProviderFuture.get().unbindAll()
            } catch (e: Exception) {
                Log.e("Camera", "Error unbinding camera during dispose", e)
            }
            executor.shutdown()
            // Ensure that the torch is turned off when disposed.
            try {
                if (backCameraId != null && isFlashlightOn) {
                    cameraManager.setTorchMode(backCameraId, false)
                }
            } catch (e: Exception) {
                Log.e("Flashlight", "Error cleaning up flashlight", e)
            }
        }
    }

    LaunchedEffect(isCameraOn) {
        val cameraProvider = cameraProviderFuture.get()
        if (isCameraOn) {
            try {
                cameraProvider.unbindAll()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()

                imageAnalysis.setAnalyzer(executor) { imageProxy ->
                    try {
                        val mediaImage = imageProxy.image
                        if (mediaImage != null) {
                            val inputImage = InputImage.fromMediaImage(
                                mediaImage, imageProxy.imageInfo.rotationDegrees
                            )
                            val scanner = BarcodeScanning.getClient()
                            scanner.process(inputImage).addOnSuccessListener { barcodes ->
                                for (barcode in barcodes) {
                                    barcode.rawValue?.let { onBarcodeScanned(it) }
                                }
                            }.addOnCompleteListener {
                                imageProxy.close()
                            }
                        } else {
                            imageProxy.close()
                        }
                    } catch (e: Exception) {
                        Log.e("ImageAnalysis", "Error analyzing image", e)
                        imageProxy.close()
                    }
                }

                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner, cameraSelector, preview, imageAnalysis
                )
                cameraControl = camera.cameraControl
                if (isFlashlightOn) {
                    cameraControl?.enableTorch(true)
                }
            } catch (e: Exception) {
                Log.e("Camera", "Error binding camera to lifecycle", e)
            }
        } else {
            try {
                cameraProvider.unbindAll()
                cameraControl = null
            } catch (e: Exception) {
                Log.e("Camera", "Error unbinding camera", e)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .requiredHeight(220.dp)
            .clipToBounds()
            .background(Color.Black)

    ) {
        if (isCameraOn) {
            AndroidView(
                factory = { previewView }, modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .zIndex(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Image(
                painter = painterResource(
                    id = if (isFlashlightOn) R.drawable.flashlight_off_24px
                    else R.drawable.flashlight_on_24px
                ), contentDescription = "Flashlight", modifier = Modifier
                    .size(24.dp)
                    .clickable {
                        val newState = !isFlashlightOn
                        try {
                            if (isCameraOn && cameraControl != null) {
                                cameraControl?.enableTorch(newState)
                                isFlashlightOn = newState
                            } else {
                                if (backCameraId != null) {
                                    cameraManager.setTorchMode(backCameraId, newState)
                                    isFlashlightOn = newState
                                } else {
                                    Log.e(
                                        "Flashlight",
                                        "Back camera not found for Camera2 torch control"
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("Flashlight", "Error toggling flashlight: ${e.message}", e)
                        }
                    }, colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
            )

            Image(
                painter = painterResource(
                    id = if (isCameraOn) R.drawable.no_photography_24px
                    else R.drawable.photo_camera_24px
                ),
                contentDescription = if (isCameraOn) "Turn Camera Off" else "Turn Camera On",
                modifier = Modifier
                    .size(24.dp)
                    .clickable {
                        if (isCameraOn) {
                            try {
                                cameraProviderFuture.get().unbindAll()
                                cameraControl = null
                                isFlashlightOn = false
                            } catch (e: Exception) {
                                Log.e("Camera", "Error turning off camera: ${e.message}", e)
                            }
                        }
                        isCameraOn = !isCameraOn
                    },
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
            )

            Image(
                painter = painterResource(
                    id = R.drawable.delete_icon
                ), contentDescription = "Delete Data", modifier = Modifier
                    .size(24.dp)
                    .clickable {
                        showDeleteDialog = true
                    }, colorFilter = ColorFilter.tint(Color.Red)
            )
        }

        // Show confirmation AlertDialog when requested
        if (showDeleteDialog) {
            AlertDialog(onDismissRequest = { showDeleteDialog = false }, title = {
                // You can customize the title (for example, with an icon)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.warning_icon),
                        contentDescription = "Warning",
                        modifier = Modifier.size(24.dp),
                        colorFilter = ColorFilter.tint(Color.Red)
                    )
                    Spacer(
                        modifier = Modifier.padding(
                            horizontal = 8.dp
                        )
                    )
                    Text("Confirmation")
                }
            }, text = {
                Text("Are you sure? Do you really want to delete all scanned data?")
            }, confirmButton = {
                Button(onClick = {
                    // Clear only the data for the current feature based on callerContext
                    when (callerContext.uppercase()) {
                        "INWARD" -> sharedViewModel.clearInwardScannedItems()
                        "OUTWARD" -> sharedViewModel.clearOutwardScannedItems()
                        "AUDIT" -> sharedViewModel.clearAuditScannedItems()
                        "PRN_OUTWARD" -> sharedViewModel.clearPrnOutwardScannedItems()
                        else -> {
                            // Fallback in case the callerContext is not recognized
                            sharedViewModel.clearScannedItems()
                        }
                    }
                    Log.i("DeleteButton", "Deleted scanned data from $callerContext")
                    showDeleteDialog = false
                }) {
                    Text("Yes")
                }
            }, dismissButton = {
                Button(onClick = { showDeleteDialog = false }) {
                    Text("No")
                }
            })
        }

    }
}