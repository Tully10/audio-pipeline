package com.nocturne.audiocapture.ui

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.nocturne.audiocapture.R
import com.nocturne.audiocapture.api.ApiClient

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Settings"
        if (savedInstanceState == null)
            supportFragmentManager.beginTransaction().replace(R.id.settings_container, SettingsFragment()).commit()
    }
    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)
            findPreference<EditTextPreference>("server_url")?.setOnPreferenceChangeListener { _, _ ->
                ApiClient.invalidate(); true
            }
            findPreference<EditTextPreference>("api_key")?.setOnPreferenceChangeListener { _, newValue ->
                saveApiKey(newValue as String); ApiClient.invalidate(); true
            }
        }

        private fun saveApiKey(key: String) {
            try {
                val masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
                EncryptedSharedPreferences.create("secure_prefs", masterKey, requireContext(),
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                ).edit().putString("api_key", key).apply()
            } catch (e: Exception) { Log.e("Settings", "Key save failed", e) }
        }
    }
}
