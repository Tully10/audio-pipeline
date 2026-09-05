package com.nocturne.audiocapture.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.nocturne.audiocapture.api.models.Session
import com.nocturne.audiocapture.databinding.ItemSessionBinding
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class SessionAdapter(
    private val sessions: List<Session>,
    private val onTap: (Session) -> Unit
) : RecyclerView.Adapter<SessionAdapter.ViewHolder>() {

    inner class ViewHolder(val b: ItemSessionBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemSessionBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = sessions.size

    override fun onBindViewHolder(h: ViewHolder, pos: Int) {
        val s = sessions[pos]
        h.b.tvDateTime.text = formatDate(s.started_at)
        h.b.tvDuration.text = formatDuration(s.duration_seconds)
        h.b.tvPeople.text = "${s.people_count} people"
        h.b.tvSummary.text = s.summary ?: "Processing…"
        h.b.root.setOnClickListener { onTap(s) }
    }

    private fun formatDate(iso: String) = try {
        ZonedDateTime.parse(iso).format(DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm"))
    } catch (e: Exception) { iso }

    private fun formatDuration(s: Int) = if (s >= 60) "${s / 60}m ${s % 60}s" else "${s}s"
}
