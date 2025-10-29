// app/src/main/java/com/example/ntfynotipush/svc/NtfyPusherService.kt
package com.example.ntfynotipush.svc

import android.app.Notification
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.preference.PreferenceManager

class NtfyPusherService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        val baseUrl = prefs.getString("server_url", "")?.trim().orEmpty()
        val topic   = prefs.getString("topic", "")?.trim().orEmpty()
        if (baseUrl.isBlank() || topic.isBlank()) return

        val includeNtfy = prefs.getBoolean("include_ntfy_app", false)
        val includeSelf = prefs.getBoolean("include_self_app", false)

        val exclude = prefs.getString("exclude_packages", "")
            ?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()

        val pkg = sbn.packageName ?: return
        if (!includeSelf && pkg == packageName) return
        if (!includeNtfy && pkg == "io.heckel.ntfy") return
        if (exclude.contains(pkg)) return

        val n = sbn.notification ?: return

        // --- SAFE TITLE (used as ntfy Title header) ---
        // Some apps omit or insert line breaks/control chars; sanitize to avoid header errors.
        var notifTitle = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        if (notifTitle.isBlank()) notifTitle = "Notification"
        // Remove CR/LF to comply with HTTP header rules (RFC 7230)
        notifTitle = notifTitle.replace(Regex("[\\r\\n]"), " ")

        // Message text (body content)
        val text = (
            n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: n.extras.getCharSequence(Notification.EXTRA_TEXT)
                ?: ""
        ).toString()

        val lines = n.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.joinToString("\n") { it.toString() }.orEmpty()

        val appLabel = runCatching {
            val ai = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(ai).toString()
        }.getOrElse { pkg }

        val whenMs = if (n.`when` > 0) n.`when` else sbn.postTime

        // STATUS: store last event seen by the listener (for your debug panel)
        PreferenceManager.getDefaultSharedPreferences(this).edit()
            .putLong("last_event_ts", System.currentTimeMillis())
            .putString("last_event_pkg", pkg)
            .putString("last_event_app", appLabel)
            .putString("last_event_title", notifTitle)
            .apply()

        // Build body: keep original message, move app origin below
        val content = buildString {
            if (text.isNotBlank()) appendLine(text)
            if (lines.isNotBlank()) {
                appendLine("—")
                appendLine(lines)
            }
            appendLine()
            appendLine("App: $appLabel ($pkg)")
            appendLine("Posted: $whenMs")
            if (Build.VERSION.SDK_INT >= 23) {
                val sub = n.extras.getString(Notification.EXTRA_SUB_TEXT)
                if (!sub.isNullOrBlank()) appendLine("Sub: $sub")
            }
        }.trim()

        val bearer   = prefs.getString("auth_token", "")?.trim().orEmpty()
        val insecure = prefs.getBoolean("allow_insecure_ssl", false)

        // Enqueue send (WorkManager: expedited + retry/backoff)
        NtfyEnqueue.enqueue(
            context  = this,
            baseUrl  = baseUrl,
            topic    = topic,
            title    = notifTitle, // <- now the actual notification title, sanitized
            tags     = "bell",
            body     = content,
            bearer   = bearer,
            insecure = insecure
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (!prefs.getBoolean("send_removed", false)) return

        val baseUrl = prefs.getString("server_url", "")?.trim().orEmpty()
        val topic   = prefs.getString("topic", "")?.trim().orEmpty()
        if (baseUrl.isBlank() || topic.isBlank()) return

        val includeNtfy = prefs.getBoolean("include_ntfy_app", false)
        val includeSelf = prefs.getBoolean("include_self_app", false)
        val insecure    = prefs.getBoolean("allow_insecure_ssl", false)

        val pkg = sbn.packageName ?: return
        val exclude = prefs.getString("exclude_packages", "")
            ?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()

        if (!includeSelf && pkg == packageName) return
        if (!includeNtfy && pkg == "io.heckel.ntfy") return
        if (exclude.contains(pkg)) return

        val appLabel = runCatching {
            val ai = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(ai).toString()
        }.getOrElse { pkg }

        NtfyEnqueue.enqueue(
            context  = this,
            baseUrl  = baseUrl,
            topic    = topic,
            title    = "Notification removed",
            tags     = "",
            body     = "Notification removed from $appLabel ($pkg)",
            bearer   = "",
            insecure = insecure
        )
    }
}
