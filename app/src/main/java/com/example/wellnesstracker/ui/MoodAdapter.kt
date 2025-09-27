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
        private val emoji: TextView = itemView.findViewById(R.id.textEmoji)
        private val dateTime: TextView = itemView.findViewById(R.id.textDateTime)
        private val note: TextView = itemView.findViewById(R.id.textNote)

        fun bind(item: Mood) {
            emoji.text = item.emoji
            dateTime.text = "${item.date} ${item.time}"
            if (item.note.isNullOrEmpty()) {
                note.visibility = View.GONE
            } else {
                note.visibility = View.VISIBLE
                note.text = item.note
            }
        }
    }
}

