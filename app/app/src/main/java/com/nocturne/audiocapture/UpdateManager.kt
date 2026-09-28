package com.nocturne.audiocapture

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import androidx.preference.PreferenceManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class UpdateManager(private val context: Context) {

    private fun serverUrl(): String =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getString("server_url", "") ?: ""

    private fun apiKey(): String? = try {
        val masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "secure_prefs", masterKey, context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        ).getString("api_key", null)
    } catch (e: Exception) {
        Log.e(TAG, "Key load failed", e)
        null
    }

    fun canInstallUnknownApps(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                context.packageManager.canRequestPackageInstalls()

    fun openInstallUnknownAppsSettings() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
    }

    suspend fun downloadAndInstall(onProgress: (String) -> Unit) = withContext(Dispatchers.IO) {
        val base = serverUrl().trimEnd('/')
        val key = apiKey()
        if (base.isEmpty() || key == null) {
            onProgress("Server URL / API key not configured")
            return@withContext
        }

        val apkFile = File(context.cacheDir, "update.apk")

        try {
            onProgress("Downloading…")
            val conn = URL("$base/update/apk").openConnection() as HttpURLConnection
            conn.setRequestProperty("X-Api-Key", key)
            conn.connectTimeout = 10_000
            conn.readTimeout = 120_000
            conn.connect()

            if (conn.responseCode != 200) {
                onProgress("Download failed (${conn.responseCode})")
                return@withContext
            }

            conn.inputStream.use { input ->
                apkFile.outputStream().use { output -> input.copyTo(output) }
            }

            withContext(Dispatchers.Main) {
                onProgress("Installing…")
                val uri = FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", apkFile
                )
                context.startActivity(
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Update failed", e)
            onProgress("Update failed")
        }
    }

    companion object {
        private const val TAG = "UpdateManager"
    }
}
