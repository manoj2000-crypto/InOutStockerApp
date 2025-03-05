package com.example.inoutstocker

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.example.inoutstocker.ui.theme.InOutStockerTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            InOutStockerTheme {
                val navController = rememberNavController()
                PermissionHandler {
                    AppNavigation(navController = navController)
                }
            }
        }
    }
}

@SuppressLint("InlinedApi")
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionHandler(content: @Composable () -> Unit) {
    val context = LocalContext.current

    // Remember permission states for Camera, Internet, and Bluetooth
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)
    val internetPermissionState = rememberPermissionState(android.Manifest.permission.INTERNET)
    val bluetoothPermissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        rememberPermissionState(android.Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        null  // For older versions, consider the permission as granted
    }

    LaunchedEffect(Unit) {
        // Request permissions if not already granted
        if (!cameraPermissionState.status.isGranted || !internetPermissionState.status.isGranted || (bluetoothPermissionState != null && !bluetoothPermissionState.status.isGranted)) {
            cameraPermissionState.launchPermissionRequest()
            internetPermissionState.launchPermissionRequest()
            bluetoothPermissionState?.launchPermissionRequest()
        }
    }

    when {
        !cameraPermissionState.status.isGranted -> {
            AlertDialog(onDismissRequest = {},
                title = { Text("Camera Permission Required") },
                text = { Text("This app requires access to your camera. Please grant the permission.") },
                confirmButton = {
                    TextButton(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                        Text("Grant Permission")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { openAppSettings(context) }) {
                        Text("Open Settings")
                    }
                })
        }

        !internetPermissionState.status.isGranted -> {
            AlertDialog(onDismissRequest = {},
                title = { Text("Internet Permission Required") },
                text = { Text("This app requires internet access to function properly. Please grant the permission.") },
                confirmButton = {
                    TextButton(onClick = { internetPermissionState.launchPermissionRequest() }) {
                        Text("Grant Permission")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { openAppSettings(context) }) {
                        Text("Open Settings")
                    }
                })
        }

        // Only show the Bluetooth dialog if we're on Android 12+ and the permission isn’t granted
        bluetoothPermissionState != null && !bluetoothPermissionState.status.isGranted -> {
            AlertDialog(onDismissRequest = {},
                title = { Text("Bluetooth Permission Required") },
                text = { Text("This app requires access to Bluetooth for scanning device. Please grant the permission.") },
                confirmButton = {
                    TextButton(onClick = { bluetoothPermissionState.launchPermissionRequest() }) {
                        Text("Grant Permission")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { openAppSettings(context) }) {
                        Text("Open Settings")
                    }
                })
        }

        else -> {
            content()
        }
    }
}

fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }
    context.startActivity(intent)
}

fun saveCredentials(context: Context, username: String, password: String) {
    val sharedPreferences = context.getSharedPreferences("InOutStockerPrefs", Context.MODE_PRIVATE)
    val editor = sharedPreferences.edit()
    editor.putString("username", username)
    editor.putString("password", password)
    editor.apply()
}

fun getSavedCredentials(context: Context): Pair<String, String> {
    val sharedPreferences = context.getSharedPreferences("InOutStockerPrefs", Context.MODE_PRIVATE)
    val username = sharedPreferences.getString("username", "") ?: ""
    val password = sharedPreferences.getString("password", "") ?: ""
    return Pair(username, password)
}

@Composable
fun LoginPage(navController: NavController) {
    val context = LocalContext.current
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var loginMessage by remember { mutableStateOf("") }
    var messageColor by remember { mutableStateOf(androidx.compose.ui.graphics.Color.Gray) }
    val loading = remember { mutableStateOf(false) }

    // Load saved credentials when the screen loads
    LaunchedEffect(Unit) {
        val savedCredentials = getSavedCredentials(context)
        username = savedCredentials.first
        password = savedCredentials.second
    }

    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.simple_loading_animation))
    val progress by animateLottieCompositionAsState(
        composition = composition, iterations = LottieConstants.IterateForever
    )

    Scaffold(modifier = Modifier.fillMaxSize(), content = { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.vtc_logo),
                    contentDescription = stringResource(id = R.string.logo_description),
                    modifier = Modifier
                        .size(120.dp)
                        .padding(bottom = 32.dp)
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    isError = username.isBlank(),
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    isError = password.isBlank(),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                painter = painterResource(
                                    id = if (passwordVisible) R.drawable.open_eye else R.drawable.closed_eye
                                ),
                                contentDescription = if (passwordVisible) "Hide password" else "Show password"
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (username.isNotBlank() && password.isNotBlank()) {
                            loginUser(username, password, { status, message, depot ->
                                loginMessage = message
                                messageColor =
                                    if (status == "success") androidx.compose.ui.graphics.Color.Green else androidx.compose.ui.graphics.Color.Red
                                if (status == "success") {
                                    // Save credentials on successful login
                                    saveCredentials(context, username, password)

                                    // Navigate to HomeScreen and pass username and depot
                                    navController.navigate("homeScreen/${username}/${depot}")
                                }
                            }, loading)
                        } else {
                            loginMessage = "Please fill in all fields."
                            messageColor = androidx.compose.ui.graphics.Color.Red
                        }
                    }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (loading.value) {
                        LottieAnimation(
                            composition = composition,
                            progress = { progress },
                            modifier = Modifier.size(100.dp)
                        )
                    } else {
                        // Display the button when loading is false
                        Button(
                            onClick = {
                                if (username.isNotBlank() && password.isNotBlank()) {
                                    loginUser(username, password, { status, message, depot ->
                                        loginMessage = message
                                        messageColor =
                                            if (status == "success") androidx.compose.ui.graphics.Color.Green else androidx.compose.ui.graphics.Color.Red
                                        if (status == "success") {
                                            // Save credentials on successful login
                                            saveCredentials(context, username, password)

                                            // Navigate to HomeScreen and pass username and depot
                                            navController.navigate("homeScreen/${username}/${depot}")
                                        }
                                    }, loading)
                                } else {
                                    loginMessage = "Please fill in all fields."
                                    messageColor = androidx.compose.ui.graphics.Color.Red
                                }
                            }, modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text(text = "Login")
                        }
                    }
                }

                Text(
                    text = loginMessage,
                    color = messageColor,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    })
}

fun loginUser(
    username: String,
    password: String,
    onLoginResponse: (status: String, message: String, depot: String) -> Unit,
    loading: MutableState<Boolean>
) {
    loading.value = true

    CoroutineScope(Dispatchers.IO).launch {
        val client = OkHttpClient()
        val requestBody = FormBody.Builder().add("user_name", username).add("password", password)
            .add("appVersion", "versionTwo").build()

        val request = Request.Builder().url("https://vtc3pl.com/in_out_stocker_app_login.php")
            .post(requestBody).build()

        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val jsonResponse = JSONObject(response.body?.string() ?: "{}")
                val status = jsonResponse.optString("status", "error")
                val message = jsonResponse.optString("message", "Unknown error occurred.")
                val depot = jsonResponse.optString("depot", "")
                withContext(Dispatchers.Main) {
                    loading.value = false
                    onLoginResponse(status, message, depot)
                }
            } else {
                withContext(Dispatchers.Main) {
                    loading.value = false
                    onLoginResponse("error", "Network error, please try again.", "")
                }
            }
        } catch (e: IOException) {
            withContext(Dispatchers.Main) {
                loading.value = false
                onLoginResponse("error", "Network error, please try again.", "")
            }
        }
    }
}