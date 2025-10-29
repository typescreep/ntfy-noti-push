// app/src/main/java/com/example/ntfynotipush/svc/BootReceiver.kt
package com.example.ntfynotipush.svc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Nothing to start; but you could nudge user if access is off:
        // Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        // contains your component if granted. We keep it silent.
    }
}
