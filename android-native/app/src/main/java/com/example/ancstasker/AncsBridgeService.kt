package com.example.ancstasker

import android.app.Service
import android.bluetooth.*
import android.content.Intent
import android.os.IBinder
import android.util.Log
import java.util.*

class AncsBridgeService : Service() {

    private var bluetoothGatt: BluetoothGatt? = null

    // ANCS UUIDs
    private val ANCS_SERVICE_UUID = UUID.fromString("7905F214-B5CE-4E99-A40F-4B1E122D00D0")
    private val NOTIFICATION_SOURCE_UUID = UUID.fromString("9FBF120D-6301-42D9-8C58-25E699A21DBD")
    private val CONTROL_POINT_UUID = UUID.fromString("69D1D8F3-45E1-49A8-9821-9BBDFDAAD9D9")
    private val DATA_SOURCE_UUID = UUID.fromString("22EAC6E9-24D6-4BB5-BE44-B36ACE7C7BFB")
    
    // Client Characteristic Configuration Descriptor
    private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    override fun onBind(intent: Intent?): IBinder? = null

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i("ANCS", "Connected to GATT server.")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i("ANCS", "Disconnected from GATT server.")
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
                    handleNotificationSource(characteristic.value)
                }
                DATA_SOURCE_UUID -> {
                    handleDataSource(characteristic.value)
                }
            }
        }
    }

    private fun enableNotification(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(CCCD_UUID)
        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        gatt.writeDescriptor(descriptor)
    }

    private fun handleNotificationSource(value: ByteArray) {
        // Parse basic notification info (EventID, Category, etc.)
        // For simplicity, we just log it and trigger Tasker
        val eventId = value[0].toInt() // 0: Added, 1: Modified, 2: Removed
        val category = value[2].toInt() // System, IncomingCall, Social, etc.
        
        Log.d("ANCS", "New Notification Source Event! ID: $eventId, Cat: $category")
        
        // Trigger Tasker
        triggerTaskerEvent("iOS_ANCS_Added", "Notification in category $category")
    }

    private fun handleDataSource(value: ByteArray) {
        // Detailed data about the notification
        // Parsing logic for ANCS Data Source goes here
    }

    private fun triggerTaskerEvent(eventName: String, message: String) {
        val intent = Intent("net.dinglisch.android.tasker.ACTION_TASK")
        intent.putExtra("task_name", "HandleiOSNotification")
        intent.putExtra("varName", "notification_data")
        intent.putExtra("varValue", "$eventName: $message")
        sendBroadcast(intent)
        Log.i("ANCS", "Intent sent to Tasker for event: $eventName")
    }
}
