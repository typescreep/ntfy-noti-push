// app/src/main/java/com/example/ntfynotipush/ui/SettingsFragment.kt
package com.example.ntfynotipush.ui

import android.os.Bundle
import android.text.format.DateFormat
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.example.ntfynotipush.R
import java.util.Date

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.prefs, rootKey)

        // Friendly defaults for server/topic
        findPreference<EditTextPreference>("server_url")
            ?.setOnBindEditTextListener { if (it.text.isNullOrBlank()) it.setText("https://ntfy.yourdomain.com") }
        findPreference<EditTextPreference>("topic")
            ?.setOnBindEditTextListener { if (it.text.isNullOrBlank()) it.setText("android-bridge") }
    }

    override fun onResume() {
        super.onResume()
        refreshAccess()
        refreshStatus()

        // Tap any status row to refresh quickly
        listOf(
            "status_access",
            "status_last_event",
            "status_details",
            "status_last_enqueued",
            "status_last_send",
            "status_last_http"
        ).forEach { key ->
            findPreference<Preference>(key)?.setOnPreferenceClickListener {
                refreshAccess(); refreshStatus(); true
            }
        }
    }

    private fun refreshAccess() {
        val enabled = try {
            val s = android.provider.Settings.Secure.getString(
                requireContext().contentResolver,
                "enabled_notification_listeners"
            )
            s?.contains("${requireContext().packageName}/") == true
        } catch (_: Exception) { false }

        findPreference<Preference>("status_access")?.summary =
            if (enabled) "Enabled" else "DISABLED — tap “Grant Notification Access”."
    }

    private fun refreshStatus() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())

        val lastEventTs    = prefs.getLong("last_event_ts", 0L)
        val lastEventPkg   = prefs.getString("last_event_pkg", null)
        val lastEventApp   = prefs.getString("last_event_app", null)
        val lastEventTitle = prefs.getString("last_event_title", null)

        val lastEnqTs      = prefs.getLong("last_enqueued_ts", 0L)
        val lastSendTs     = prefs.getLong("last_send_ts", 0L)
        val lastSendUrl    = prefs.getString("last_send_url", null)
        val lastHttp       = prefs.getString("last_send_http", null)

        findPreference<Preference>("status_last_event")?.summary =
            if (lastEventTs > 0) fmt(lastEventTs) else "(none yet)"

        findPreference<Preference>("status_details")?.summary =
            when {
                lastEventApp != null && lastEventTitle != null ->
                    "$lastEventApp [${lastEventPkg ?: "?"}]\nTitle: ${lastEventTitle.ifBlank { "(no title)" }}"
                lastEventPkg != null -> "$lastEventPkg (no title)"
                else -> "—"
            }

        findPreference<Preference>("status_last_enqueued")?.summary =
            if (lastEnqTs > 0) fmt(lastEnqTs) else "(none yet)"

        findPreference<Preference>("status_last_send")?.summary =
            if (lastSendTs > 0) "${fmt(lastSendTs)}\nURL: ${lastSendUrl ?: "(n/a)"}"
            else "(none yet)"

        findPreference<Preference>("status_last_http")?.summary =
            lastHttp ?: "(none)"
    }

    private fun fmt(ts: Long): String {
        val d = Date(ts)
        val df = DateFormat.getMediumDateFormat(requireContext())
        val tf = DateFormat.getTimeFormat(requireContext())
        return "${df.format(d)} ${tf.format(d)}"
    }
}
