package com.example.crashdetection.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import com.example.crashdetection.R
import com.example.crashdetection.ui.CrashDetectedActivity
import java.util.UUID

class CrashDetectionService : Service() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "crash_detection_channel"
        const val NOTIFICATION_ID = 1
        private const val HELMET_NAME = "SmartHelmet-ESP32"
        private const val CRASH_COOLDOWN_MS = 15_000L
        private val SMART_HELMET_SERVICE_UUID =
            UUID.fromString("0000C0DE-0000-1000-8000-00805F9B34FB")
        private val CRASH_STATUS_CHAR_UUID =
            UUID.fromString("0000C0D1-0000-1000-8000-00805F9B34FB")
        private val CLIENT_CONFIG_DESCRIPTOR_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        @Volatile
        var isRunning: Boolean = false
            private set
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }
    private var scanner: BluetoothLeScanner? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var isScanning = false
    private var lastCrashTriggerAt = 0L

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val name = result.scanRecord?.deviceName ?: device.name
            if (name == HELMET_NAME) {
                stopHelmetScan()
                connectToDevice(device)
            }
        }

        override fun onScanFailed(errorCode: Int) = Unit
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (hasConnectPermission()) {
                    gatt.discoverServices()
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                closeGatt()
                startHelmetScan()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return
            val service = gatt.getService(SMART_HELMET_SERVICE_UUID) ?: return
            val crashStatusChar = service.getCharacteristic(CRASH_STATUS_CHAR_UUID) ?: return
            enableCrashNotifications(gatt, crashStatusChar)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid != CRASH_STATUS_CHAR_UUID) return
            val value = characteristic.value?.firstOrNull() ?: return
            if (value == 0x01.toByte() || value == 0x02.toByte()) {
                maybeLaunchCrashDetectedUi()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        startForeground(NOTIFICATION_ID, createNotification())
        startHelmetScan()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        stopHelmetScan()
        closeGatt()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun hasScanPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasConnectPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    @Suppress("MissingPermission")
    private fun startHelmetScan() {
        if (!hasScanPermission()) return
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) return
        if (isScanning) return
        scanner = adapter.bluetoothLeScanner
        scanner?.startScan(scanCallback)
        isScanning = true
    }

    @Suppress("MissingPermission")
    private fun stopHelmetScan() {
        if (!isScanning) return
        scanner?.stopScan(scanCallback)
        isScanning = false
    }

    @Suppress("MissingPermission")
    private fun connectToDevice(device: android.bluetooth.BluetoothDevice) {
        if (!hasConnectPermission()) return
        closeGatt()
        bluetoothGatt = device.connectGatt(this, false, gattCallback)
    }

    @Suppress("MissingPermission")
    private fun enableCrashNotifications(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {
        if (!hasConnectPermission()) return
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(CLIENT_CONFIG_DESCRIPTOR_UUID) ?: return
        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        gatt.writeDescriptor(descriptor)
    }

    private fun closeGatt() {
        bluetoothGatt?.close()
        bluetoothGatt = null
    }

    private fun maybeLaunchCrashDetectedUi() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastCrashTriggerAt < CRASH_COOLDOWN_MS) return
        lastCrashTriggerAt = now
        launchCrashDetectedUi()
    }

    private fun launchCrashDetectedUi() {
        val intent = Intent(this, CrashDetectedActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
    }

    private fun createNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Crash Detection",
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(channel)
        }

        val openIntent = Intent(this, CrashDetectedActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Helmet crash monitoring active")
            .setContentText("Listening for crash events from SmartHelmet-ESP32")
            .setSmallIcon(R.drawable.ic_stat_crash_detection)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}

