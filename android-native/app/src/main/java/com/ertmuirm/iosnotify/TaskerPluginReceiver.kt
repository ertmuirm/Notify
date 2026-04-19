package com.ertmuirm.iosnotify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Dummy receiver to satisfy Tasker Plugin architectural requirements.
 * Some versions of Tasker check for a receiver to validate the plugin package.
 */
class TaskerPluginReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        // No action needed for Event-only plugin
    }
}
