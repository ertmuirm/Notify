package com.ertmuirm.iosnotify

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.*
import android.content.Context
import android.content.Intent
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

    @SuppressLint("MissingPermission")
    private fun startAdvertising() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
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
        val device = bluetoothManager.adapter.getRemoteDevice(address)
        
        Log.i("ANCS", "Connecting to $address")
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        
        bluetoothGatt = device.connectGatt(this, false, gattCallback)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i("ANCS", "Connected to GATT server.")
                updateUiStatus("connected")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i("ANCS", "Disconnected from GATT server.")
                updateUiStatus("disconnected")
                bluetoothGatt = null
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(ANCS_SERVICE_UUID)
                if (service != null) {
                    val notificationSource = service.getCharacteristic(NOTIFICATION_SOURCE_UUID)
                    val dataSource = service.getCharacteristic(DATA_SOURCE_UUID)

                    // Enable notifications for both Source and Data
                    enableNotification(gatt, notificationSource)
                    enableNotification(gatt, dataSource)
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            when (characteristic.uuid) {
                NOTIFICATION_SOURCE_UUID -> {
                    handleNotificationSource(gatt, characteristic.value)
                }
                DATA_SOURCE_UUID -> {
                    handleDataSource(characteristic.value)
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
        val descriptor = characteristic.getDescriptor(CCCD_UUID)
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
        val service = gatt.getService(ANCS_SERVICE_UUID)
        val controlPoint = service?.getCharacteristic(CONTROL_POINT_UUID) ?: return

        val request = byteArrayOf(
            0x00, 
            uid[0], uid[1], uid[2], uid[3],
            0x00, // AppIdentifier
            0x01, 0xff.toByte(), 0xff.toByte(), // Title
            0x02, 0xff.toByte(), 0xff.toByte(), // Subtitle
            0x03, 0xff.toByte(), 0xff.toByte()  // Message
        )

        controlPoint.value = request
        gatt.writeCharacteristic(controlPoint)
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
        // Broadcast for Tasker Event Plugin
        val broadcastIntent = Intent("com.ertmuirm.iosnotify.NOTIFICATION_RECEIVED")
        broadcastIntent.putExtra("bundle_id", appId)
        broadcastIntent.putExtra("sender", title)
        broadcastIntent.putExtra("content", message)
        
        // Generic tasker trigger for backward compatibility
        val taskIntent = Intent("net.dinglisch.android.tasker.ACTION_TASK")
        taskIntent.putExtra("task_name", "HandleiOSNotification")
        taskIntent.putExtra("varName", "notification_raw")
        taskIntent.putExtra("varValue", "App: $appId | From: $title | Msg: $message")
        
        sendBroadcast(broadcastIntent)
        sendBroadcast(taskIntent)
        Log.i("ANCS", "Comprehensive notification sent to Tasker: $title")
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
    }
}
