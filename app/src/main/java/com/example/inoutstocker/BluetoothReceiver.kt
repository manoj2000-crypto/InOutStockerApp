package com.example.inoutstocker

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import android.util.Log

class BluetoothReceiver(
    private val onBluetoothStateChange: (Boolean) -> Unit
) : BroadcastReceiver() {

    var isHidDevice: Boolean = false
        private set

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED) {
            val state =
                intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, BluetoothAdapter.ERROR)
            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)

            // Check if the device is an HID device
            isHidDevice = isHidDevice(context, device)

            val isConnected = state == BluetoothAdapter.STATE_CONNECTED && isHidDevice
            onBluetoothStateChange(isConnected)
        }
    }

    private fun isHidDevice(context: Context?, device: BluetoothDevice?): Boolean {
        device?.let {
            // Check if the app has Bluetooth permissions
            if (!hasBluetoothPermissions(context)) {
                Log.e("BluetoothReceiver", "No Bluetooth permissions")
                return false
            }

            try {
                // Check if the device class matches HID (keyboard)
                val deviceClass = it.bluetoothClass.deviceClass
                Log.i("BluetoothReceiver", "Device class: $deviceClass")
                if (deviceClass == BluetoothClass.Device.Major.PERIPHERAL) {
                    return true
                }

                // Check if the device name matches the specific device
                val deviceName = it.name
                Log.i("BluetoothReceiver", "Device name: $deviceName")
                if (deviceName != null && deviceName.equals("CX-CODE_6BE5D5", ignoreCase = true)) {
                    return true
                } else {
                    Log.i("BluetoothReceiver", "This device is not found")
                    return false
                }
            } catch (e: SecurityException) {
                Log.e("BluetoothReceiver", "SecurityException: ${e.message}")
            }
        }
        return false
    }


    // Function to check if Bluetooth permissions are granted
    private fun hasBluetoothPermissions(context: Context?): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(
                context!!, android.Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    fun register(context: Context) {
        val filter = IntentFilter(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
        context.registerReceiver(this, filter)
    }

    fun unregister(context: Context) {
        context.unregisterReceiver(this)
    }
}