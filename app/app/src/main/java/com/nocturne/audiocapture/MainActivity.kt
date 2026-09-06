package com.nocturne.audiocapture

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.nocturne.audiocapture.api.ApiClient
import com.nocturne.audiocapture.databinding.ActivityMainBinding
import com.nocturne.audiocapture.service.RecordingService
import com.nocturne.audiocapture.ui.AskActivity
import com.nocturne.audiocapture.ui.SessionListActivity
import com.nocturne.audiocapture.ui.SettingsActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val handler = Handler(Looper.getMainLooper())
    private val pollInterval = 30_000L

    private val pollRunnable = object : Runnable {
        override fun run() { fetchStatus(); handler.postDelayed(this, pollInterval) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSessions.setOnClickListener {
            startActivity(Intent(this, SessionListActivity::class.java))
        }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnUploadNow.setOnClickListener {
            (application as App).uploadManager.enqueueNow()
            binding.btnUploadNow.isEnabled = false
            binding.btnUploadNow.text = "Uploading…"
        }

        val navigateToAsk = {
            val q = binding.etAsk.text.toString().trim()
            if (q.isNotEmpty()) {
                startActivity(Intent(this, AskActivity::class.java).putExtra("query", q))
                binding.etAsk.setText("")
            }
        }
        binding.btnAsk.setOnClickListener { navigateToAsk() }
        binding.etAsk.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { navigateToAsk(); true } else false
        }

        requestPermissionsIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        fetchStatus()
        handler.postDelayed(pollRunnable, pollInterval)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(pollRunnable)
    }

    private fun fetchStatus() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val status = ApiClient.getInstance(this@MainActivity).getTodayStatus()
                withContext(Dispatchers.Main) {
                    val recording = status.recording
                    binding.tvRecordingDot.setTextColor(
                        if (recording) ContextCompat.getColor(this@MainActivity, R.color.green_active)
                        else ContextCompat.getColor(this@MainActivity, R.color.grey_inactive)
                    )
                    binding.tvRecordingState.text = if (recording) "Recording" else "Standby"
                    binding.tvStats.text =
                        "${status.sessions_today} sessions · ${status.people_today} people today"
                    val onWifi = isOnWifi()
                    if (status.queue_depth > 0 && !onWifi) {
                        binding.btnUploadNow.visibility = View.VISIBLE
                        binding.btnUploadNow.isEnabled = true
                        binding.btnUploadNow.text = "Upload now (${status.queue_depth} pending)"
                    } else {
                        binding.btnUploadNow.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.tvRecordingState.text = "Server unreachable"
                    binding.tvStats.text = ""
                }
            }
        }
    }

    private fun isOnWifi(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun requestPermissionsIfNeeded() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 100)
        } else {
            startRecordingService()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty()
            && grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) startRecordingService()
    }

    private fun startRecordingService() {
        ContextCompat.startForegroundService(this, Intent(this, RecordingService::class.java))
    }
}
