package com.example.wellnesstracker.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import com.google.android.material.checkbox.MaterialCheckBox
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import android.widget.Toast
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.wellnesstracker.R
import com.example.wellnesstracker.model.Habit

class HabitAdapter(
    private val onChecked: (habit: Habit, checked: Boolean) -> Unit,
    private val onClick: (habit: Habit) -> Unit,
    private val onLongPressed: (habit: Habit) -> Unit
) : ListAdapter<Habit, HabitAdapter.HabitVH>(DIFF) {

    // track expanded item ids so description can expand/collapse
    private val expandedIds = mutableSetOf<Int>()

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Habit>() {
            override fun areItemsTheSame(oldItem: Habit, newItem: Habit): Boolean = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Habit, newItem: Habit): Boolean =
                oldItem.name == newItem.name && oldItem.completed == newItem.completed && oldItem.date == newItem.date
        }
    }

    private var animateId: Int? = null

    fun animateNextAdded(id: Int) {
        animateId = id
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HabitVH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_habit, parent, false)
        return HabitVH(v)
    }

    override fun onBindViewHolder(holder: HabitVH, position: Int) {
        val item = getItem(position)
        holder.bind(item, onChecked, onClick, onLongPressed)
        if (animateId != null && item.id == animateId) {
            holder.itemView.clearAnimation()
            val anim = AlphaAnimation(0f, 1f)
            anim.duration = 250
            holder.itemView.startAnimation(anim)
            animateId = null
        }
    }

    inner class HabitVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val name: TextView = itemView.findViewById(R.id.tv_habit_name)
        private val check: MaterialCheckBox = itemView.findViewById(R.id.cb_habit_completed)
        private val desc: TextView? = itemView.findViewById(R.id.tv_habit_description)

        fun bind(
            item: Habit,
            onChecked: (Habit, Boolean) -> Unit,
            onClick: (Habit) -> Unit,
            onLongPressed: (Habit) -> Unit
        ) {
            name.text = item.name
            // placeholder: description not persisted in model; if added later it will show here
            desc?.text = ""

            // set maxLines based on expanded state
            if (expandedIds.contains(item.id)) {
                desc?.maxLines = Int.MAX_VALUE
            } else {
                desc?.maxLines = 1
            }

            // toggle expanded state when description is clicked
            desc?.setOnClickListener {
                if (expandedIds.contains(item.id)) expandedIds.remove(item.id) else expandedIds.add(item.id)
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) notifyItemChanged(pos)
            }

            // Reset checkbox listener and state
            check.setOnCheckedChangeListener(null)
            check.isChecked = item.completed

            // Use the checked change listener so the checkbox behaves normally and still notifies the callback.
            check.setOnCheckedChangeListener { _, isChecked ->
                name.alpha = if (isChecked) 0.6f else 1.0f
                // brief toast for debugging so you can see the change in the UI
                Toast.makeText(itemView.context, if (isChecked) "Completed" else "Not completed", Toast.LENGTH_SHORT).show()
                onChecked(item, isChecked)
            }

            // Consume long clicks on the checkbox so they don't propagate to the item (prevent delete dialog)
            check.setOnLongClickListener { true }

            // Item clicks / long-clicks
            itemView.isClickable = true
            itemView.isLongClickable = true
            itemView.setOnClickListener { onClick(item) }
            itemView.setOnLongClickListener {
                onLongPressed(item)
                true
            }
        }
    }
}