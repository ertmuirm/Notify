package com.ertmuirm.iosnotify

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Empty activity used to satisfy Tasker's requirement for a configuration activity.
 * Tasker looks for an activity with action net.dinglisch.android.tasker.ACTION_EDIT_EVENT.
 */
class TaskerEventConfigActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // This is where a user would configure filters (e.g. only certain apps).
        // For now, we just auto-save and exit to make it easy to add.
        
        val resultIntent = Intent()
        val bundle = Bundle()
        
        // Tasker Event description shown in UI
        bundle.putString("net.dinglisch.android.tasker.extras.VARIABLE_REPLACE_KEYS", "")
        resultIntent.putExtra("com.twofortyfouram.locale.intent.extra.BUNDLE", bundle)
        resultIntent.putExtra("com.twofortyfouram.locale.intent.extra.BLURB", "Relays all iOS Notifications")
        
        setResult(RESULT_OK, resultIntent)
        finish()
    }
}
