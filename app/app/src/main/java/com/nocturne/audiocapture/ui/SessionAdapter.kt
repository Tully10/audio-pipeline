package com.nocturne.audiocapture.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.nocturne.audiocapture.api.models.Session
import com.nocturne.audiocapture.databinding.ItemSessionBinding
import org.json.JSONObject
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class SessionAdapter(
    private val sessions: List<Session>,
    private val onTap: (Session) -> Unit
) : RecyclerView.Adapter<SessionAdapter.ViewHolder>() {

    private var filtered: List<Session> = sessions

    inner class ViewHolder(val b: ItemSessionBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemSessionBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = filtered.size

    override fun onBindViewHolder(h: ViewHolder, pos: Int) {
        val s = filtered[pos]
        h.b.tvDateTime.text = formatDate(s.started_at)
        h.b.tvDuration.text = formatDuration(s.duration_s)
        h.b.tvPeople.text = when {
            s.people.isEmpty() -> ""
            s.people.size == 1 -> s.people[0]
            else -> "${s.people[0]} +${s.people.size - 1} more"
        }
        h.b.tvSummary.text = s.summary
            ?: if (s.status == "complete") "No content captured" else "Processing…"

        if (!s.sentiment.isNullOrEmpty()) {
            try {
                val jo = JSONObject(s.sentiment)
                val sent = jo.optString("sentiment", "")
                val tone = jo.optString("tone", "")
                if (sent.isNotEmpty()) {
                    h.b.tvSentiment.text = "$sent · $tone"
                    h.b.tvSentiment.visibility = View.VISIBLE
                } else {
                    h.b.tvSentiment.visibility = View.GONE
                }
            } catch (e: Exception) {
                h.b.tvSentiment.visibility = View.GONE
            }
        } else {
            h.b.tvSentiment.visibility = View.GONE
        }

        h.b.root.setOnClickListener { onTap(s) }
    }

    fun filter(query: String) {
        filtered = if (query.isBlank()) sessions
        else sessions.filter { s ->
            s.summary?.contains(query, ignoreCase = true) == true ||
                s.people.any { it.contains(query, ignoreCase = true) } ||
                s.started_at.contains(query)
        }
        notifyDataSetChanged()
    }

    private fun formatDate(iso: String) = try {
        ZonedDateTime.parse(iso).format(DateTimeFormatter.ofPattern("d MMM · HH:mm"))
    } catch (e: Exception) {
        try { iso.substring(0, 16).replace("T", " ") } catch (ex: Exception) { iso }
    }

    private fun formatDuration(s: Int) = when {
        s >= 3600 -> "${s / 3600}h ${(s % 3600) / 60}m"
        s >= 60 -> "${s / 60}m ${s % 60}s"
        else -> "${s}s"
    }
}
