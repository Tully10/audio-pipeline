package com.nocturne.audiocapture.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.nocturne.audiocapture.api.ApiClient
import com.nocturne.audiocapture.databinding.ActivitySessionListBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SessionListActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySessionListBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySessionListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Sessions"
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        loadSessions()
    }

    private fun loadSessions() {
        binding.progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val sessions = ApiClient.getInstance(this@SessionListActivity).getSessions()
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.recyclerView.adapter = SessionAdapter(sessions) { s ->
                        startActivity(Intent(this@SessionListActivity, SessionPlaybackActivity::class.java)
                            .putExtra("session_id", s.id)
                            .putExtra("session_summary", s.summary ?: ""))
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.tvError.text = "Failed: ${e.message}"
                    binding.tvError.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
