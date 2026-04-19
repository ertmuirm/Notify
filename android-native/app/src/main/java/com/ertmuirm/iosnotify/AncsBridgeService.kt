package com.ertmuirm.iosnotify

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.pm.ServiceInfo
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import java.util.*

class AncsBridgeService : Service() {

    private var bluetoothGatt: BluetoothGatt? = null
    private var responseBuffer = mutableListOf<Byte>()
    private var notificationCount = 0
    private var targetDeviceAddress: String? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var gattServer: BluetoothGattServer? = null

    // ANCS UUIDs
    private val ANCS_SERVICE_UUID = UUID.fromString("7905F214-B5CE-4E99-A40F-4B1E122D00D0")
    private val NOTIFICATION_SOURCE_UUID = UUID.fromString("9FBF120D-6301-42D9-8C58-25E699A21DBD")
    private val CONTROL_POINT_UUID = UUID.fromString("69D1D8F3-45E1-49A8-9821-9BBDFDAAD9D9")
    private val DATA_SOURCE_UUID = UUID.fromString("22EAC6E9-24D6-4BB5-BE44-B36ACE7C7BFB")
    
    // Client Characteristic Configuration Descriptor
    private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(1, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(1, createNotification())
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "START_ADVERTISING") {
            startAdvertising()
        }
        
        val address = intent?.getStringExtra("device_address")
        if (address != null && address != targetDeviceAddress) {
            targetDeviceAddress = address
            connectToDevice(address)
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "bridge_service",
                "iOS Bridge Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            Notification.Builder(this, "bridge_service")
                .setContentTitle("iOS Bridge Active")
                .setContentText("Listening for iPhone notifications...")
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentIntent(pendingIntent)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("iOS Bridge Active")
                .setContentText("Listening for iPhone notifications...")
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentIntent(pendingIntent)
                .build()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startAdvertising() {
        Log.d("ANCS", "startAdvertising called")
        addUiLog("Requesting Advertising...")
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter == null) {
            addUiLog("Error: Bluetooth Adapter not found")
            return
        }
        advertiser = bluetoothAdapter.bluetoothLeAdvertiser
        
        if (advertiser == null) {
            addUiLog("Advertising not supported on this device")
            return
        }

        // Setup a dummy GATT server to look like a real accessory
        setupGattServer(bluetoothManager)

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .setIncludeTxPowerLevel(true)
            .build()

        val scanResponse = AdvertiseData.Builder()
            .addServiceSolicitationUuid(ParcelUuid(ANCS_SERVICE_UUID))
            .build()

        advertiser?.startAdvertising(settings, data, scanResponse, advertiseCallback)
        addUiLog("Broadcasting ANCS Solicitation...")
        addUiLog("IMPORTANT: Forget device on iPhone and re-pair now!")
        updateUiStatus("advertising")
    }

    @SuppressLint("MissingPermission")
    private fun setupGattServer(manager: BluetoothManager) {
        gattServer?.close()
        gattServer = manager.openGattServer(this, object : BluetoothGattServerCallback() {
            override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
                Log.d("ANCS", "GATT Server connection state: $newState")
            }
        })

        // Add a generic service to be a "valid" BLE peripheral
        val service = BluetoothGattService(
            UUID.randomUUID(),
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )
        gattServer?.addService(service)
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.i("ANCS", "LE Advertise started successfully")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e("ANCS", "LE Advertise failed: $errorCode")
            addUiLog("Advertising failed: $errorCode")
        }
    }

    private fun connectToDevice(address: String) {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter == null) {
            addUiLog("Error: BT Adapter unavailable")
            return
        }
        val device = bluetoothAdapter.getRemoteDevice(address)
        
        Log.i("ANCS", "Connecting to $address via LE")
        addUiLog("Attempting GATT connection...")
        
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        
        // Use TRANSPORT_LE for better compatibility with modern devices
        bluetoothGatt = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(this, false, gattCallback)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d("ANCS", "ConnectionStateChange: status=$status, newState=$newState")
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i("ANCS", "Connected to GATT server.")
                addUiLog("GATT Connected. Discovering services...")
                updateUiStatus("connected")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i("ANCS", "Disconnected from GATT server.")
                addUiLog("GATT Disconnected (status: $status)")
                updateUiStatus("disconnected")
                bluetoothGatt = null
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.d("ANCS", "Services discovered: status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(ANCS_SERVICE_UUID)
                if (service != null) {
                    Log.i("ANCS", "ANCS Service found")
                    addUiLog("ANCS Service found!")
                    val notificationSource = service.getCharacteristic(NOTIFICATION_SOURCE_UUID)
                    val dataSource = service.getCharacteristic(DATA_SOURCE_UUID)

                    // Enable notifications for both Source and Data
                    if (notificationSource != null) enableNotification(gatt, notificationSource)
                    if (dataSource != null) enableNotification(gatt, dataSource)
                } else {
                    Log.e("ANCS", "ANCS Service NOT found on this device")
                    addUiLog("Error: ANCS Service not found. Is notification sharing ON?")
                }
            } else {
                addUiLog("Service discovery failed: $status")
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            val value = characteristic.value
            if (value == null || value.isEmpty()) return
            
            when (characteristic.uuid) {
                NOTIFICATION_SOURCE_UUID -> {
                    handleNotificationSource(gatt, value)
                }
                DATA_SOURCE_UUID -> {
                    handleDataSource(value)
                }
            }
        }
    }

    private fun updateUiStatus(status: String) {
        val intent = Intent("com.ertmuirm.iosnotify.BRIDGE_UPDATE")
        intent.putExtra("status", status)
        sendBroadcast(intent)
    }

    private fun updateUiCount() {
        val intent = Intent("com.ertmuirm.iosnotify.BRIDGE_UPDATE")
        intent.putExtra("count", notificationCount)
        sendBroadcast(intent)
    }

    private fun addUiLog(log: String) {
        val intent = Intent("com.ertmuirm.iosnotify.BRIDGE_UPDATE")
        intent.putExtra("log", log)
        sendBroadcast(intent)
    }

    @SuppressLint("MissingPermission")
    private fun enableNotification(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(CCCD_UUID) ?: return
        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        gatt.writeDescriptor(descriptor)
    }

    private fun handleNotificationSource(gatt: BluetoothGatt, value: ByteArray) {
        if (value.size < 8) return

        val eventId = value[0].toInt() // 0: Added, 1: Modified, 2: Removed
        val uid = value.sliceArray(4..7)

        if (eventId == 0 || eventId == 1) { // Added or Modified
            Log.d("ANCS", "Requesting attributes for UID: ${uid.contentToString()}")
            requestNotificationAttributes(gatt, uid)
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestNotificationAttributes(gatt: BluetoothGatt, uid: ByteArray) {
        val service = gatt.getService(ANCS_SERVICE_UUID) ?: return
        val controlPoint = service.getCharacteristic(CONTROL_POINT_UUID) ?: return

        val request = ByteArray(15)
        request[0] = 0x00
        request[1] = uid[0]
        request[2] = uid[1]
        request[3] = uid[2]
        request[4] = uid[3]
        request[5] = 0x00 // AppIdentifier
        request[6] = 0x01
        request[7] = 0xff.toByte()
        request[8] = 0xff.toByte()
        request[9] = 0x02
        request[10] = 0xff.toByte()
        request[11] = 0xff.toByte()
        request[12] = 0x03
        request[13] = 0xff.toByte()
        request[14] = 0xff.toByte()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(controlPoint, request, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            @Suppress("DEPRECATION")
            controlPoint.value = request
            gatt.writeCharacteristic(controlPoint)
        }
        responseBuffer.clear()
    }

    private fun handleDataSource(value: ByteArray) {
        responseBuffer.addAll(value.toList())
        if (responseBuffer.size > 8) {
            parseAncsAttributes(responseBuffer.toByteArray())
        }
    }

    private fun parseAncsAttributes(data: ByteArray) {
        try {
            var i = 5
            val attributes = mutableMapOf<Int, String>()

            while (i < data.size) {
                val attrId = data[i].toInt()
                if (i + 2 >= data.size) break
                val len = ((data[i + 1].toInt() and 0xFF) or (data[i + 2].toInt() and 0xFF shl 8))
                i += 3
                
                if (i + len > data.size) break
                val attrValue = String(data.sliceArray(i until i + len))
                attributes[attrId] = attrValue
                i += len
            }

            if (attributes.containsKey(1) && attributes.containsKey(3)) {
                notificationCount++
                updateUiCount()
                
                val appId = attributes[0] ?: "unknown"
                val title = attributes[1] ?: ""
                val message = attributes[3] ?: ""
                
                addUiLog("[$appId] $title: $message")
                sendDetailedTaskerIntent(appId, title, message)
                
                responseBuffer.clear()
            }
        } catch (e: Exception) {
            Log.e("ANCS", "Parse error: ${e.message}")
        }
    }

    private fun sendDetailedTaskerIntent(appId: String, title: String, message: String) {
        Log.i("ANCS", "Firing Tasker Event: $title")
        
        // 1. Generic broadcast for Tasker "Intent Received" listeners
        val broadcastIntent = Intent("com.ertmuirm.iosnotify.NOTIFICATION_RECEIVED")
        broadcastIntent.putExtra("bundle_id", appId)
        broadcastIntent.putExtra("sender", title)
        broadcastIntent.putExtra("content", message)
        
        // 2. Properly formatted Tasker Plugin Event broadcast
        // Tasker identifies plugins by their package/receiver
        val taskerEventIntent = Intent("net.dinglisch.android.tasker.ACTION_FIRE_EVENT")
        taskerEventIntent.setPackage("net.dinglisch.android.tasker")
        
        val bundle = Bundle()
        bundle.putString("bundle_id", appId)
        bundle.putString("sender", title)
        bundle.putString("content", message)
        
        // Metadata required for Tasker to map it back to the event
        taskerEventIntent.putExtra("com.twofortyfouram.locale.intent.extra.BUNDLE", bundle)
        
        // Also send variables directly
        val vars = Bundle()
        vars.putString("%bundle_id", appId)
        vars.putString("%sender", title)
        vars.putString("%content", message)
        taskerEventIntent.putExtra("net.dinglisch.android.tasker.extras.VARIABLES", vars)
        
        sendBroadcast(broadcastIntent)
        try {
            sendBroadcast(taskerEventIntent)
        } catch (e: Exception) {
            Log.e("ANCS", "Failed to send tasker event: ${e.message}")
        }
        
        Log.i("ANCS", "Log entry sent: [$appId] $title")
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
    }
}
