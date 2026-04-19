package com.ertmuirm.iosnotify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log

/**
 * Receiver to handle Tasker's "Condition/Event" queries.
 * Even for push events, Tasker sometimes probes the receiver to verify the plugin.
 */
class TaskerPluginReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("TaskerPlugin", "Received action: $action")
        
        if ("com.twofortyfouram.locale.intent.action.QUERY_CONDITION" == action) {
            // Tasker is asking if the condition is currently met.
            // For a one-time "event" (notification received), we usually return UNKNOWN
            // or let the event firing logic handle the actual trigger.
            // Tasker uses this to verify the plugin is alive.
            setResultCode(1) // Usually indicates "Satisfied" or "Alive" for discovery
        }
    }
}
