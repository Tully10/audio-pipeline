package com.nocturne.audiocapture.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.nocturne.audiocapture.R
import com.nocturne.audiocapture.api.models.WordEntry
import com.nocturne.audiocapture.databinding.ItemWordBinding

class WordAdapter(
    val words: List<WordEntry>,
    val onTap: (Int) -> Unit,
    val onLongPress: (Int, String) -> Unit
) : RecyclerView.Adapter<WordAdapter.ViewHolder>() {

    var highlightedIndex = -1
        set(value) {
            val old = field; field = value
            if (old >= 0) notifyItemChanged(old)
            if (value >= 0) notifyItemChanged(value)
        }

    inner class ViewHolder(val b: ItemWordBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemWordBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = words.size

    override fun onBindViewHolder(h: ViewHolder, pos: Int) {
        val word = words[pos]
        h.b.tvWord.text = word.word + " "
        h.b.tvWord.setTextColor(when {
            pos == highlightedIndex -> ContextCompat.getColor(h.itemView.context, R.color.highlight_active)
            word.low_confidence -> Color.parseColor("#FFA000")
            else -> ContextCompat.getColor(h.itemView.context, android.R.color.black)
        })
        h.b.tvWord.setBackgroundColor(
            if (pos == highlightedIndex) Color.parseColor("#E3F2FD") else Color.TRANSPARENT
        )
        h.b.tvWord.setOnClickListener { onTap(pos) }
        h.b.tvWord.setOnLongClickListener { onLongPress(pos, word.word); true }
    }
}
