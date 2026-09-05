package com.nocturne.audiocapture.upload

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nocturne.audiocapture.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        if (prefs.getBoolean("wifi_only", true) && !isOnWifi()) return@withContext Result.retry()

        val db = AppDatabase.getInstance(applicationContext)
        val serverUrl = (prefs.getString("server_url", "http://192.168.1.100:8080") ?: "").trimEnd('/')
        val apiKey = getApiKey()
        val deviceId = Settings.Secure.getString(applicationContext.contentResolver, Settings.Secure.ANDROID_ID)
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

        for (chunk in db.chunkDao().getPending()) {
            val file = File(chunk.filePath)
            if (!file.exists()) { db.chunkDao().deleteById(chunk.id); continue }
            db.chunkDao().updateStatus(chunk.id, "UPLOADING")
            try {
                val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("audio_file", file.name, file.asRequestBody("audio/wav".toMediaTypeOrNull()))
                    .addFormDataPart("device_id", deviceId)
                    .addFormDataPart("chunk_seq", chunk.chunkSeq.toString())
                    .addFormDataPart("recorded_at", chunk.recordedAt)
                    .addFormDataPart("markers", chunk.markers)
                    .build()
                val response = client.newCall(
                    Request.Builder().url("$serverUrl/audio/chunks").header("X-API-Key", apiKey).post(body).build()
                ).execute()
                if (response.isSuccessful) {
                    db.chunkDao().updateStatus(chunk.id, "UPLOADED"); file.delete()
                } else {
                    db.chunkDao().updateStatus(chunk.id, "PENDING")
                    db.chunkDao().incrementRetry(chunk.id)
                    if (chunk.retryCount >= 5) db.chunkDao().updateStatus(chunk.id, "FAILED")
                }
                response.close()
            } catch (e: Exception) {
                Log.e("UploadWorker", "Upload failed for chunk ${chunk.id}", e)
                db.chunkDao().updateStatus(chunk.id, "PENDING")
                db.chunkDao().incrementRetry(chunk.id)
                if (chunk.retryCount >= 5) db.chunkDao().updateStatus(chunk.id, "FAILED")
            }
        }
        Result.success()
    }

    private fun isOnWifi(): Boolean {
        val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.getNetworkCapabilities(cm.activeNetwork ?: return false)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    fun getApiKey(): String = try {
        val key = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create("secure_prefs", key, applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ).getString("api_key", "") ?: ""
    } catch (e: Exception) { "" }
}
