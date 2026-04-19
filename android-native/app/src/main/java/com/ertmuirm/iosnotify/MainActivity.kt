package com.ertmuirm.iosnotify

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var pairButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusLabel)
        pairButton = findViewById(R.id.scanButton)

        requestPermissions()

        pairButton.setOnClickListener {
            startAncsBridge()
        }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.ACCESS_FINE_LOCATION
            ),
            1
        )
    }

    private fun startAncsBridge() {
        val intent = Intent(this, AncsBridgeService::class.java)
        startService(intent)
        statusText.text = "Bridge Service Running..."
    }
}
