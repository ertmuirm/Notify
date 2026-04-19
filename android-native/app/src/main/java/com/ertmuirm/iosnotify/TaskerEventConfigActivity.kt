package com.ertmuirm.iosnotify

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

/**
 * Activity used to satisfy Tasker's requirement for a configuration activity.
 * Displays documentation on available variables.
 */
class TaskerEventConfigActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tasker_config)

        findViewById<Button>(R.id.save_button).setOnClickListener {
            val resultIntent = Intent()
            val bundle = Bundle()
            
            // Tasker Event description shown in UI
            bundle.putString("net.dinglisch.android.tasker.extras.VARIABLE_REPLACE_KEYS", "")
            
            // Provide variable hints for Tasker UI
            val vars = arrayOf("%bundle_id", "%sender", "%content")
            resultIntent.putExtra("net.dinglisch.android.tasker.extras.VARIABLES", vars)
            
            resultIntent.putExtra("com.twofortyfouram.locale.intent.extra.BUNDLE", bundle)
            resultIntent.putExtra("com.twofortyfouram.locale.intent.extra.BLURB", "Relays all iOS Notifications")
            
            setResult(RESULT_OK, resultIntent)
            finish()
        }
    }
}
