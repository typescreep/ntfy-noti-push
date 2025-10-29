// app/src/main/java/com/example/ntfynotipush/svc/NtfyEnqueue.kt
package com.example.ntfynotipush.svc

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object NtfyEnqueue {
    fun enqueue(
        context: Context,
        baseUrl: String,
        topic: String,
        title: String,
        tags: String,
        body: String,
        bearer: String,
        insecure: Boolean
    ) {
        val data = Data.Builder()
            .putString(SendToNtfyWorker.KEY_BASE_URL, baseUrl)
            .putString(SendToNtfyWorker.KEY_TOPIC, topic)
            .putString(SendToNtfyWorker.KEY_TITLE, title)
            .putString(SendToNtfyWorker.KEY_TAGS, tags)
            .putString(SendToNtfyWorker.KEY_BODY, body)
            .putString(SendToNtfyWorker.KEY_BEARER, bearer)
            .putBoolean(SendToNtfyWorker.KEY_INSECURE, insecure)
            .build()

        val req = OneTimeWorkRequestBuilder<SendToNtfyWorker>()
            .setInputData(data)
            .setConstraints(
                androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag("send-ntfy")
            .build()

        // record enqueue time for UI
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putLong("last_enqueued_ts", System.currentTimeMillis())
            .apply()

        WorkManager.getInstance(context).enqueue(req)
    }
}
