package com.example.wellnesstracker.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.wellnesstracker.R
import com.example.wellnesstracker.model.Mood
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MoodAdapter(
    private val onClick: (Mood, Int) -> Unit
) : ListAdapter<Mood, MoodAdapter.MoodVH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Mood>() {
            override fun areItemsTheSame(oldItem: Mood, newItem: Mood): Boolean =
                oldItem === newItem || (oldItem.date == newItem.date && oldItem.time == newItem.time && oldItem.emoji == newItem.emoji && oldItem.note == newItem.note)

            override fun areContentsTheSame(oldItem: Mood, newItem: Mood): Boolean =
                oldItem == newItem
        }
    }

    private var selectedPosition: Int = RecyclerView.NO_POSITION

    fun setSelected(position: Int) {
        val prev = selectedPosition
        selectedPosition = position
        if (prev != RecyclerView.NO_POSITION) notifyItemChanged(prev)
        if (selectedPosition != RecyclerView.NO_POSITION) notifyItemChanged(selectedPosition)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MoodVH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_mood, parent, false)
        return MoodVH(v)
    }

    override fun onBindViewHolder(holder: MoodVH, position: Int) {
        val item = getItem(position)
        holder.bind(item)
        holder.itemView.isSelected = position == selectedPosition
        holder.itemView.setOnClickListener {
            onClick(item, position)
        }
    }

    class MoodVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // IDs in item_mood.xml
        private val emoji: TextView = itemView.findViewById(R.id.tv_mood_emoji)
        private val name: TextView = itemView.findViewById(R.id.tv_mood_name)
        private val timestamp: TextView = itemView.findViewById(R.id.tv_mood_timestamp)
        private val timestampRaw: TextView = itemView.findViewById(R.id.tv_mood_timestamp_raw)
        private val note: TextView = itemView.findViewById(R.id.tv_mood_note)
        private val rating: TextView = itemView.findViewById(R.id.tv_mood_rating)

        private val friendlyTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
        private val friendlyDateFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

        private fun scoreForEmoji(e: String): Int = when (e) {
            "😊" -> 5
            "🙂" -> 4
            "😐" -> 3
            "😢" -> 2
            "😡", "😠" -> 1
            "😴" -> 2
            "😰" -> 2
            else -> 3
        }

        private fun labelForEmoji(e: String): String = when (e) {
            "😊" -> "Happy"
            "🙂" -> "Content"
            "😐" -> "Neutral"
            "😢" -> "Sad"
            "😡", "😠" -> "Angry"
            "😴" -> "Tired"
            "😰" -> "Anxious"
            else -> e
        }

        fun bind(item: Mood) {
            // Emoji and label
            emoji.text = item.emoji
            name.text = labelForEmoji(item.emoji)

            // Combine date (yyyy-MM-dd) and time (HH:mm) into LocalDateTime, then build friendly and raw ISO
            try {
                val localDate = LocalDate.parse(item.date)
                val localTime = LocalTime.parse(item.time)
                val ldt = LocalDateTime.of(localDate, localTime)

                // Friendly display: Today • 2:30 PM or MMM d • 2:30 PM
                val today = LocalDate.now()
                val friendlyDate = if (localDate == today) "Today" else localDate.format(friendlyDateFormatter)
                val friendlyTime = ldt.format(friendlyTimeFormatter)
                timestamp.text = "$friendlyDate • $friendlyTime"

                // Raw ISO instant in UTC (use system zone to convert)
                val instant = ldt.atZone(ZoneId.systemDefault()).toInstant()
                timestampRaw.text = DateTimeFormatter.ISO_INSTANT.format(instant)

            } catch (t: Throwable) {
                // Fallback to whatever strings were provided
                timestamp.text = "${item.date} ${item.time}"
                timestampRaw.text = "${item.date}T${item.time}:00Z"
            }

            // Note visibility
            if (item.note.isNullOrEmpty()) {
                note.visibility = View.GONE
            } else {
                note.visibility = View.VISIBLE
                note.text = item.note
            }

            // Compute rating as decimal between 0.0 - 1.0 based on 5-point scale and display with one decimal (e.g., 0.8)
            val score = scoreForEmoji(item.emoji)
            val decimal = score / 5.0
            rating.text = String.format(Locale.getDefault(), "%.1f", decimal)
        }
    }
}
