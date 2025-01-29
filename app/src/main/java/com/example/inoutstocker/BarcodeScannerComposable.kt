package com.example.inoutstocker

import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@OptIn(ExperimentalGetImage::class)
@Composable
fun BarcodeScanner(
    modifier: Modifier = Modifier, onBarcodeScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalContext.current as LifecycleOwner
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    var cameraControl: CameraControl? by remember { mutableStateOf(null) }
    var isFlashlightOn by remember { mutableStateOf(false) }
    var isCameraOn by remember { mutableStateOf(true) }
    val previewView = remember { androidx.camera.view.PreviewView(context) }

    val cameraManager =
        remember { context.getSystemService(android.content.Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager }
    val backCameraId = remember {
        cameraManager.cameraIdList.firstOrNull { id ->
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val lensFacing =
                characteristics.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING)
            lensFacing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraProviderFuture.get().unbindAll()
            executor.shutdown()
        }
    }

    Box(modifier = modifier) {
        if (isCameraOn) {
            AndroidView(factory = { previewView }, modifier = Modifier.matchParentSize())

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                cameraProvider.unbindAll()

                val preview = androidx.camera.core.Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()

                imageAnalysis.setAnalyzer(executor) { imageProxy ->
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
                    }
                }

                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner, cameraSelector, preview, imageAnalysis
                )
                cameraControl = camera.cameraControl
            }, ContextCompat.getMainExecutor(context))
        } else {
            // Show a black screen when the camera is off
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                // Placeholder UI can be added here if needed
            }
        }

        // Controls for Flashlight and Camera On/Off
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd) // Align to the bottom-right corner
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Flashlight Toggle
            val flashlightIcon: Painter = if (isFlashlightOn) {
                painterResource(id = R.drawable.flashlight_off_24px)
            } else {
                painterResource(id = R.drawable.flashlight_on_24px)
            }
            Image(
                painter = flashlightIcon,
                contentDescription = "Flashlight Toggle",
                modifier = Modifier
                    .size(48.dp)
                    .clickable {
                        if (backCameraId != null) {
                            isFlashlightOn = !isFlashlightOn
                            try {
                                cameraManager.setTorchMode(backCameraId, isFlashlightOn)
                            } catch (e: Exception) {
                                Log.e("Flashlight", "Error toggling flashlight", e)
                            }
                        } else {
                            Log.i("Flashlight", "Back camera not found.")
                        }
                    },
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
            )

            // Camera On/Off Toggle
            val cameraIcon: Painter = if (isCameraOn) {
                painterResource(id = R.drawable.no_photography_24px)
            } else {
                painterResource(id = R.drawable.photo_camera_24px)
            }
            Image(
                painter = cameraIcon,
                contentDescription = if (isCameraOn) "Turn Camera Off" else "Turn Camera On",
                modifier = Modifier
                    .size(48.dp)
                    .clickable {
                        isCameraOn = !isCameraOn
                        if (!isCameraOn) {
                            cameraProviderFuture.get().unbindAll()
                        }
                    },
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
            )
        }
    }
}