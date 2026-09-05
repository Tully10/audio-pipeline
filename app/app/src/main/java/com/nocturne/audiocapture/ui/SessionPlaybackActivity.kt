package com.nocturne.audiocapture.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.EditText
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import com.nocturne.audiocapture.api.ApiClient
import com.nocturne.audiocapture.api.CorrectionRequest
import com.nocturne.audiocapture.api.models.Marker
import com.nocturne.audiocapture.api.models.TranscriptResponse
import com.nocturne.audiocapture.api.models.WordEntry
import com.nocturne.audiocapture.databinding.ActivitySessionPlaybackBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
class SessionPlaybackActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySessionPlaybackBinding
    private var player: ExoPlayer? = null
    private var wordAdapter: WordAdapter? = null
    private val handler = Handler(Looper.getMainLooper())
    private var words: List<WordEntry> = emptyList()
    private var markers: List<Marker> = emptyList()
    private lateinit var sessionId: String

    private val syncRunnable = object : Runnable {
        override fun run() { syncTranscript(); handler.postDelayed(this, 100) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySessionPlaybackBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        sessionId = intent.getStringExtra("session_id") ?: run { finish(); return }
        title = (intent.getStringExtra("session_summary") ?: "Session").take(40)
        binding.recyclerView.layoutManager = FlexboxLayoutManager(this).apply {
            flexDirection = FlexDirection.ROW; flexWrap = FlexWrap.WRAP
        }
        loadTranscript()
    }

    private fun loadTranscript() {
        binding.progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val t = ApiClient.getInstance(this@SessionPlaybackActivity).getTranscript(sessionId)
                withContext(Dispatchers.Main) { binding.progressBar.visibility = View.GONE; setupPlayback(t) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.tvError.text = "Failed: ${e.message}"; binding.tvError.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun setupPlayback(t: TranscriptResponse) {
        words = t.words; markers = t.markers
        wordAdapter = WordAdapter(words, onTap = { seekToWord(it) }, onLongPress = { idx, txt -> showCorrection(idx, txt) })
        binding.recyclerView.adapter = wordAdapter

        val totalMs = (words.lastOrNull()?.end_s?.times(1000) ?: 1.0).toLong().coerceAtLeast(1)
        binding.waveformBar.setMarkers(markers.map { (it.offset_s * 1000.0 / totalMs).toFloat() })
        binding.waveformBar.setOnMarkerTapped { idx -> player?.seekTo((markers[idx].offset_s * 1000).toLong()) }
        binding.waveformBar.setOnSeek { frac -> player?.duration?.let { d -> player?.seekTo((frac * d).toLong()) } }

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val serverUrl = (prefs.getString("server_url", "http://192.168.1.100:8080") ?: "").trimEnd('/')
        val apiKey = ApiClient.getApiKey(this)

        val dsFactory = DefaultHttpDataSource.Factory().setDefaultRequestProperties(mapOf("X-API-Key" to apiKey))
        val mediaSource = ProgressiveMediaSource.Factory(dsFactory)
            .createMediaSource(MediaItem.fromUri("$serverUrl/sessions/$sessionId/audio"))

        player = ExoPlayer.Builder(this).build().also { exo ->
            binding.playerControls.player = exo
            exo.setMediaSource(mediaSource); exo.prepare()
            exo.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) handler.post(syncRunnable) else handler.removeCallbacks(syncRunnable)
                }
            })
        }
    }

    private fun seekToWord(index: Int) {
        val word = words.getOrNull(index) ?: return
        if (word.low_confidence) {
            player?.seekTo(((word.start_s - 2.5) * 1000).toLong().coerceAtLeast(0))
            player?.play()
            handler.postDelayed({ player?.pause() }, 5000)
        } else {
            player?.seekTo((word.start_s * 1000).toLong())
            player?.play()
        }
    }

    private fun syncTranscript() {
        val posMs = player?.currentPosition ?: return
        val posS = posMs / 1000.0
        val idx = words.indexOfFirst { it.start_s <= posS && posS < it.end_s }.takeIf { it >= 0 } ?: return
        if (wordAdapter?.highlightedIndex != idx) {
            wordAdapter?.highlightedIndex = idx
            binding.recyclerView.scrollToPosition(idx)
        }
        val dur = player?.duration?.takeIf { it > 0 } ?: return
        binding.waveformBar.setProgress(posMs.toFloat() / dur.toFloat())
    }

    private fun showCorrection(index: Int, current: String) {
        val input = EditText(this).apply { setText(current) }
        AlertDialog.Builder(this).setTitle("Correct transcript").setView(input)
            .setPositiveButton("Save") { _, _ ->
                val corrected = input.text.toString().trim()
                if (corrected.isNotEmpty()) {
                    CoroutineScope(Dispatchers.IO).launch {
                        ApiClient.getInstance(this@SessionPlaybackActivity)
                            .correctWord(sessionId, index, CorrectionRequest(corrected))
                    }
                }
            }
            .setNegativeButton("Cancel", null).show()
    }

    override fun onStop() { super.onStop(); handler.removeCallbacks(syncRunnable); player?.pause() }
    override fun onDestroy() { handler.removeCallbacks(syncRunnable); player?.release(); player = null; super.onDestroy() }
    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
