package com.example.crashdetection.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.*

class BluetoothManager(private val context: Context) {
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    var onDeviceFound: ((BluetoothDevice) -> Unit)? = null
    var onConnectionStatusChanged: ((Boolean, String) -> Unit)? = null

    companion object {
        const val TAG = "BluetoothManager"
        private val ESP32_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    init {
        val bluetoothManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
        } else {
            null
        }
        bluetoothAdapter = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
    }

    fun isBluetoothSupported(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    fun enableBluetooth() {
        if (!isBluetoothEnabled()) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            context.startActivity(enableBtIntent)
        }
    }

    fun startScanning() {
        try {
            if (isBluetoothEnabled()) {
                bluetoothAdapter?.startDiscovery()
                Log.d(TAG, "Scanning for Bluetooth devices...")
            } else {
                Log.w(TAG, "Bluetooth is not enabled")
                onConnectionStatusChanged?.invoke(false, "Bluetooth is not enabled")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting scan: \[0m${e.message}")
        }
    }

    fun stopScanning() {
        try {
            bluetoothAdapter?.cancelDiscovery()
            Log.d(TAG, "Stopped scanning for devices")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping scan: \[0m${e.message}")
        }
    }

    suspend fun connectToDevice(device: BluetoothDevice): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            stopScanning()
            Log.d(TAG, "Connecting to device: \[0m${device.name} (${device.address})")
            
            bluetoothSocket = device.createRfcommSocketToServiceRecord(ESP32_UUID)
            bluetoothSocket?.connect()
            
            Log.d(TAG, "Connected successfully to \[0m${device.name}")
            onConnectionStatusChanged?.invoke(true, "Connected to \[0m${device.name}")
            true
        } catch (e: IOException) {
            Log.e(TAG, "Connection failed: \[0m${e.message}")
            onConnectionStatusChanged?.invoke(false, "Connection failed: \[0m${e.message}")
            false
        }
    }

    fun disconnectDevice() {
        try {
            bluetoothSocket?.close()
            Log.d(TAG, "Disconnected from device")
            onConnectionStatusChanged?.invoke(false, "Disconnected")
        } catch (e: IOException) {
            Log.e(TAG, "Error disconnecting: \[0m${e.message}")
        }
    }

    fun getPairedDevices(): List<BluetoothDevice> {
        return try {
            bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting paired devices: \[0m${e.message}")
            emptyList()
        }
    }

    fun isConnected(): Boolean {
        return bluetoothSocket?.isConnected == true
    }

    fun sendData(data: String) {
        try {
            bluetoothSocket?.outputStream?.write(data.toByteArray())
            Log.d(TAG, "Data sent: \[0m$data")
        } catch (e: IOException) {
            Log.e(TAG, "Error sending data: \[0m${e.message}")
        }
    }

    fun receiveData(): String? {
        return try {
            val buffer = ByteArray(1024)
            val bytes = bluetoothSocket?.inputStream?.read(buffer) ?: return null
            String(buffer, 0, bytes)
        } catch (e: IOException) {
            Log.e(TAG, "Error receiving data: \[0m${e.message}")
            null
        }
    }
}