package com.example.ntfynotipush.svc

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import javax.net.ssl.HostnameVerifier

class SendToNtfyWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)

        val baseUrl = inputData.getString(KEY_BASE_URL)?.trim().orEmpty()
        val topicRaw = inputData.getString(KEY_TOPIC)?.trim().orEmpty()
        val title    = inputData.getString(KEY_TITLE).orEmpty()
        val tagsStr  = inputData.getString(KEY_TAGS).orEmpty()
        val body     = inputData.getString(KEY_BODY).orEmpty()
        val bearer   = inputData.getString(KEY_BEARER).orEmpty()
        val insecure = inputData.getBoolean(KEY_INSECURE, false)

        // Record that we tried
        prefs.edit().putLong("last_send_ts", System.currentTimeMillis()).apply()

        if (baseUrl.isBlank() || topicRaw.isBlank() || body.isBlank()) {
            prefs.edit().putString("last_send_http", "skipped: missing params").apply()
            return@withContext Result.success()
        }

        // ntfy topics are single path segments
        val topic = topicRaw.replace("/", "-").replace(Regex("[^A-Za-z0-9._-]"), "-")

        // Per docs: JSON publish goes to ROOT, not /topic
        val url = baseUrl.removeSuffix("/")
        prefs.edit().putString("last_send_url", url).apply()

        val client = if (insecure) makeUnsafeClient() else defaultClient

        // Build JSON payload (UTF-8 safe)
        val tags = if (tagsStr.isBlank()) JSONArray()
                   else JSONArray(tagsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() })

        val json = JSONObject()
            .put("topic", topic)
            .put("message", body)
            .put("title", title.ifBlank { "Notification" })
            .put("tags", tags)

        val media = "application/json; charset=utf-8".toMediaType()
        val reqBody = json.toString().toRequestBody(media)

        val reqBuilder = Request.Builder()
            .url(url)
            .post(reqBody)
        if (bearer.isNotBlank()) {
            reqBuilder.addHeader("Authorization", "Bearer $bearer")
        }
        val req = reqBuilder.build()

        try {
            client.newCall(req).execute().use { resp ->
                val code = resp.code
                prefs.edit().putString("last_send_http", "HTTP $code").apply()
                return@withContext when {
                    resp.isSuccessful -> Result.success()
                    code in 500..599  -> Result.retry()
                    else              -> Result.failure()
                }
            }
        } catch (e: Exception) {
            prefs.edit().putString("last_send_http", "net err: ${e.javaClass.simpleName}").apply()
            return@withContext Result.retry()
        }
    }

    private val defaultClient by lazy {
        OkHttpClient.Builder()
            .callTimeout(15, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    // Only for self-signed testing (toggle in settings)
    private fun makeUnsafeClient(): OkHttpClient {
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        })
        val ssl = SSLContext.getInstance("SSL").apply { init(null, trustAll, SecureRandom()) }
        val hv = HostnameVerifier { _, _ -> true }
        return defaultClient.newBuilder()
            .sslSocketFactory(ssl.socketFactory, trustAll[0] as X509TrustManager)
            .hostnameVerifier(hv)
            .build()
    }

    companion object {
        const val KEY_BASE_URL = "baseUrl"
        const val KEY_TOPIC    = "topic"
        const val KEY_TITLE    = "title"
        const val KEY_TAGS     = "tags"
        const val KEY_BODY     = "body"
        const val KEY_BEARER   = "bearer"
        const val KEY_INSECURE = "insecure"
    }
}
