package com.example.ntfynotipush.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.example.ntfynotipush.R
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import javax.net.ssl.HostnameVerifier

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, SettingsFragment())
            .commit()

        findViewById<Button>(R.id.btnGrantAccess).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        // Battery optimization prompt (best-effort)
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:$packageName")
                    )
                )
            }
        } catch (_: Exception) {}

        // TEST SEND (JSON to ROOT)
        findViewById<Button>(R.id.btnTestSend).setOnClickListener {
            Thread {
                val prefs = PreferenceManager.getDefaultSharedPreferences(this)
                val base    = prefs.getString("server_url", "")?.trim().orEmpty()
                val topic   = prefs.getString("topic", "")?.trim().orEmpty()
                val bearer  = prefs.getString("auth_token", "")?.trim().orEmpty()
                val insecure= prefs.getBoolean("allow_insecure_ssl", false)

                if (base.isBlank() || topic.isBlank()) {
                    runOnUiThread { toast("Set server URL and topic first") }
                    return@Thread
                }

                val client = if (insecure) makeUnsafeClient() else OkHttpClient()

                val url = base.removeSuffix("/")  // JSON goes to root
                val json = JSONObject()
                    .put("topic", topic.replace("/", "-"))
                    .put("message", "hello from test button")
                    .put("title", "ntfy bridge test — Паша ✓")
                    .put("tags", JSONArray(listOf("test")))

                val req = Request.Builder()
                    .url(url)
                    .apply { if (bearer.isNotBlank()) addHeader("Authorization", "Bearer $bearer") }
                    .post(json.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                try {
                    client.newCall(req).execute().use { resp ->
                        runOnUiThread { toast("Test send -> HTTP ${resp.code}") }
                    }
                } catch (e: Exception) {
                    runOnUiThread { toast("Test failed: ${e.message}") }
                }
            }.start()
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    // UNSAFE client: for self-signed certs only (lab/testing)!
    private fun makeUnsafeClient(): OkHttpClient {
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        })
        val ssl = SSLContext.getInstance("SSL").apply { init(null, trustAll, SecureRandom()) }
        val hv = HostnameVerifier { _, _ -> true }
        return OkHttpClient.Builder()
            .sslSocketFactory(ssl.socketFactory, trustAll[0] as X509TrustManager)
            .hostnameVerifier(hv)
            .build()
    }
}
