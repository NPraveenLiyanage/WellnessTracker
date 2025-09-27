package com.example.wellnesstracker.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.wellnesstracker.R
import com.example.wellnesstracker.model.Habit

class HabitAdapter(
    private val onChecked: (habit: Habit, checked: Boolean) -> Unit,
    private val onLongPressed: (habit: Habit) -> Unit
) : ListAdapter<Habit, HabitAdapter.HabitVH>(DIFF) {

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
        holder.bind(item, onChecked, onLongPressed)
        if (animateId != null && item.id == animateId) {
            holder.itemView.clearAnimation()
            val anim = AlphaAnimation(0f, 1f)
            anim.duration = 250
            holder.itemView.startAnimation(anim)
            animateId = null
        }
    }

    class HabitVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val name: TextView = itemView.findViewById(R.id.textName)
        private val check: CheckBox = itemView.findViewById(R.id.checkCompleted)

        fun bind(item: Habit, onChecked: (Habit, Boolean) -> Unit, onLongPressed: (Habit) -> Unit) {
            name.text = item.name
            check.setOnCheckedChangeListener(null)
            check.isChecked = item.completed
            check.setOnCheckedChangeListener { _, isChecked -> onChecked(item, isChecked) }

            itemView.setOnLongClickListener {
                onLongPressed(item)
                true
            }
        }
    }
}

