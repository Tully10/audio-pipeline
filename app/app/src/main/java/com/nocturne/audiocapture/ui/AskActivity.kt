package com.nocturne.audiocapture.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nocturne.audiocapture.api.ApiClient
import com.nocturne.audiocapture.api.models.AskRequest
import com.nocturne.audiocapture.api.models.AskSource
import com.nocturne.audiocapture.databinding.ActivityAskBinding
import com.nocturne.audiocapture.databinding.ItemAskSourceBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AskActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAskBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAskBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Ask"

        val initialQuery = intent.getStringExtra("query") ?: ""
        if (initialQuery.isNotEmpty()) {
            binding.etQuery.setText(initialQuery)
            doAsk(initialQuery)
        }

        binding.btnAsk.setOnClickListener { doAsk(binding.etQuery.text.toString().trim()) }
        binding.etQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                doAsk(binding.etQuery.text.toString().trim()); true
            } else false
        }
    }

    private fun doAsk(query: String) {
        if (query.isEmpty()) return
        binding.progressBar.visibility = View.VISIBLE
        binding.scrollAnswer.visibility = View.GONE
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val resp = ApiClient.getInstance(this@AskActivity).ask(AskRequest(query))
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.tvAnswer.text = resp.answer
                    binding.rvSources.layoutManager = LinearLayoutManager(this@AskActivity)
                    binding.rvSources.adapter = SourceAdapter(resp.sources) { source ->
                        startActivity(
                            Intent(this@AskActivity, SessionPlaybackActivity::class.java)
                                .putExtra("session_id", source.session_id)
                                .putExtra("session_summary", source.quote.take(60))
                        )
                    }
                    binding.scrollAnswer.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.tvAnswer.text = "Error: ${e.message}"
                    binding.scrollAnswer.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}

private class SourceAdapter(
    private val sources: List<AskSource>,
    private val onClick: (AskSource) -> Unit
) : RecyclerView.Adapter<SourceAdapter.VH>() {

    inner class VH(val b: ItemAskSourceBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemAskSourceBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = sources.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val s = sources[pos]
        h.b.tvSourceHeader.text = "Session ${s.session_id.take(8)} @ ${"%,.1f".format(s.timestamp_s)}s"
        h.b.tvSourceQuote.text = "“${s.quote}”"
        h.b.root.setOnClickListener { onClick(s) }
    }
}
