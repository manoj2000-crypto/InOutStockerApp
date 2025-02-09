package com.example.inoutstocker

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
    val lifecycleOwner = context as LifecycleOwner
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    var cameraControl: CameraControl? by remember { mutableStateOf(null) }
    var isFlashlightOn by remember { mutableStateOf(false) }
    var isCameraOn by remember { mutableStateOf(true) }
    val previewView = remember { androidx.camera.view.PreviewView(context) }

    val cameraManager = remember {
        context.getSystemService(android.content.Context.CAMERA_SERVICE) as CameraManager
    }
    val backCameraId = remember {
        cameraManager.cameraIdList.firstOrNull { id ->
            val characteristics = cameraManager.getCameraCharacteristics(id)
            characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
    }

    // Clean up when the composable leaves composition.
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

    // Bind (or unbind) the camera only when the isCameraOn flag changes.
    LaunchedEffect(isCameraOn) {
        val cameraProvider = cameraProviderFuture.get()
        if (isCameraOn) {
            try {
                cameraProvider.unbindAll()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
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
                // Reapply torch state if it was on before binding.
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

    Box(modifier = modifier.fillMaxSize()) {
        if (isCameraOn) {
            AndroidView(factory = { previewView }, modifier = Modifier.matchParentSize())
        } else {
            // When camera is off, show a black screen.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black)
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Flashlight Toggle
            Image(
                painter = painterResource(
                    id = if (isFlashlightOn) R.drawable.flashlight_off_24px
                    else R.drawable.flashlight_on_24px
                ), contentDescription = "Flashlight", modifier = Modifier
                    .size(48.dp)
                    .clickable {
                        val newState = !isFlashlightOn
                        try {
                            if (isCameraOn && cameraControl != null) {
                                // Simply call enableTorch and update state.
                                cameraControl?.enableTorch(newState)
                                isFlashlightOn = newState
                            } else {
                                // Fallback using Camera2 API when camera is off.
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

            // Camera On/Off Toggle
            Image(
                painter = painterResource(
                    id = if (isCameraOn) R.drawable.no_photography_24px
                    else R.drawable.photo_camera_24px
                ),
                contentDescription = if (isCameraOn) "Turn Camera Off" else "Turn Camera On",
                modifier = Modifier
                    .size(48.dp)
                    .clickable {
                        if (isCameraOn) {
                            try {
                                cameraProviderFuture.get().unbindAll()
                                cameraControl = null
                                // Also turn off the flashlight.
                                isFlashlightOn = false
                            } catch (e: Exception) {
                                Log.e("Camera", "Error turning off camera: ${e.message}", e)
                            }
                        }
                        isCameraOn = !isCameraOn
                    },
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
            )
        }
    }
}