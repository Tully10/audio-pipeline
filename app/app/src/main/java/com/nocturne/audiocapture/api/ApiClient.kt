package com.nocturne.audiocapture.api

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private var instance: ApiService? = null
    private var lastBaseUrl: String? = null

    fun getInstance(context: Context): ApiService {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val baseUrl = (prefs.getString("server_url", "http://192.168.1.100:8080") ?: "http://192.168.1.100:8080").trimEnd('/') + "/"
        if (instance == null || baseUrl != lastBaseUrl) {
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    chain.proceed(chain.request().newBuilder().header("X-API-Key", getApiKey(context)).build())
                }
                .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
                .build()
            instance = Retrofit.Builder().baseUrl(baseUrl).client(client)
                .addConverterFactory(GsonConverterFactory.create()).build().create(ApiService::class.java)
            lastBaseUrl = baseUrl
        }
        return instance!!
    }

    fun invalidate() { instance = null; lastBaseUrl = null }

    fun getApiKey(context: Context): String = try {
        val key = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create("secure_prefs", key, context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ).getString("api_key", "") ?: ""
    } catch (e: Exception) { Log.e("ApiClient", "Failed to get API key", e); "" }
}
