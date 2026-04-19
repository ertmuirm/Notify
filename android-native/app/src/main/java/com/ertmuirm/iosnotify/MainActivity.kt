package com.ertmuirm.iosnotify

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.*
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var statusView: View
    private lateinit var logsView: View
    private lateinit var pairView: View
    
    private lateinit var deviceNameText: TextView
    private lateinit var deviceAddressText: TextView
    private lateinit var statusLabel: TextView
    private lateinit var notificationCountText: TextView
    private lateinit var scanStatusText: TextView
    
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var isScanning = false
    private val handler = Handler(Looper.getMainLooper())
    
    private val discoveredDevices = mutableListOf<BluetoothDevice>()
    private val deviceAdapter = DeviceAdapter { device -> connectToDevice(device) }
    
    private val logs = mutableListOf<String>()
    private val logsAdapter = LogsAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize Views
        statusView = findViewById(R.id.view_status)
        logsView = findViewById(R.id.view_logs)
        pairView = findViewById(R.id.view_pair)
        
        deviceNameText = findViewById(R.id.deviceName)
        deviceAddressText = findViewById(R.id.deviceAddress)
        statusLabel = findViewById(R.id.statusLabel)
        notificationCountText = findViewById(R.id.notificationCount)
        scanStatusText = findViewById(R.id.scanStatusText)
        
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_status -> showTab(statusView)
                R.id.nav_logs -> showTab(logsView)
                R.id.nav_pair -> showTab(pairView)
            }
            true
        }

        // Setup RecyclerViews
        findViewById<RecyclerView>(R.id.devicesRecyclerView).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = deviceAdapter
        }
        
        findViewById<RecyclerView>(R.id.logsRecyclerView).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = logsAdapter
        }

        findViewById<View>(R.id.scanButton).setOnClickListener { startScan() }
        findViewById<View>(R.id.maskButton).setOnClickListener { 
            val intent = Intent(this, AncsBridgeService::class.java)
            intent.action = "START_ADVERTISING"
            startService(intent)
            Toast.makeText(this, "Advertising... find this device on iPhone BT settings", Toast.LENGTH_LONG).show()
        }
        findViewById<View>(R.id.clearLogsButton).setOnClickListener { 
            logs.clear()
            logsAdapter.notifyDataSetChanged()
        }
        
        findViewById<View>(R.id.openSettingsButton).setOnClickListener {
            val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
            startActivity(intent)
        }

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        requestPermissions()
        
        // Register for bridge updates
        val filter = IntentFilter("com.ertmuirm.iosnotify.BRIDGE_UPDATE")
        ContextCompat.registerReceiver(this, bridgeReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun showTab(view: View) {
        statusView.visibility = View.GONE
        logsView.visibility = View.GONE
        pairView.visibility = View.GONE
        view.visibility = View.VISIBLE
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 1)
    }

    @SuppressLint("MissingPermission")
    private fun startScan() {
        if (isScanning) return
        
        discoveredDevices.clear()
        deviceAdapter.notifyDataSetChanged()
        
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            Toast.makeText(this, "BLE not supported", Toast.LENGTH_SHORT).show()
            return
        }

        isScanning = true
        scanStatusText.text = "Scanning..."
        
        scanner.startScan(scanCallback)
        handler.postDelayed({
            stopScan()
        }, 10000)
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        if (!isScanning) return
        isScanning = false
        scanStatusText.text = "Scan complete. Found ${discoveredDevices.size} devices."
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            if (device.name != null && !discoveredDevices.contains(device)) {
                discoveredDevices.add(device)
                deviceAdapter.notifyItemInserted(discoveredDevices.size - 1)
            }
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        stopScan()
        val intent = Intent(this, AncsBridgeService::class.java)
        intent.putExtra("device_address", device.address)
        intent.putExtra("device_name", device.name ?: "Unknown")
        startService(intent)
        
        deviceNameText.text = device.name ?: "Unknown"
        deviceAddressText.text = device.address
        statusLabel.text = "CONNECTING..."
        statusLabel.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_light))
        
        Toast.makeText(this, "Starting Bridge Service...", Toast.LENGTH_SHORT).show()
        showTab(statusView)
    }

    private val bridgeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val status = intent.getStringExtra("status") ?: ""
            val notificationCount = intent.getIntExtra("count", -1)
            val log = intent.getStringExtra("log")
            
            Log.d("MainActivity", "Bridge Update Received: status=$status, log=$log")
            
            if (status.isNotEmpty()) {
                statusLabel.text = status.uppercase()
                if (status == "connected") {
                    statusLabel.setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_green_light))
                } else {
                    statusLabel.setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_red_light))
                }
            }
            
            if (notificationCount >= 0) {
                notificationCountText.text = notificationCount.toString()
            }
            
            if (log != null) {
                logs.add(0, log)
                logsAdapter.notifyItemInserted(0)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(bridgeReceiver)
    }

    // --- Adapters ---

    inner class DeviceAdapter(private val onClick: (BluetoothDevice) -> Unit) : RecyclerView.Adapter<DeviceAdapter.ViewHolder>() {
        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(android.R.id.text1)
            val address: TextView = v.findViewById(android.R.id.text2)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false)
            return ViewHolder(v)
        }

        @SuppressLint("MissingPermission")
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val d = discoveredDevices[position]
            holder.name.text = d.name
            holder.name.setTextColor(0xFFFFFFFF.toInt())
            holder.address.text = d.address
            holder.address.setTextColor(0x80FFFFFF.toInt())
            holder.itemView.setOnClickListener { onClick(d) }
        }

        override fun getItemCount() = discoveredDevices.size
    }

    inner class LogsAdapter : RecyclerView.Adapter<LogsAdapter.ViewHolder>() {
        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val text: TextView = v.findViewById(android.R.id.text1)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.text.text = logs[position]
            holder.text.setTextColor(0xFFFFFFFF.toInt())
            holder.text.textSize = 12f
        }

        override fun getItemCount() = logs.size
    }
}
