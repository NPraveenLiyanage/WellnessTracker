package com.example.wellnesstracker.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.wellnesstracker.R
import com.example.wellnesstracker.model.Habit
import com.google.android.material.checkbox.MaterialCheckBox

class AllHabitsAdapter(
    private val onToggle: (Habit, Boolean) -> Unit,
    private val onEdit: (Habit) -> Unit,
    private val onDelete: (Habit) -> Unit
) : ListAdapter<Habit, AllHabitsAdapter.HabitVH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Habit>() {
            override fun areItemsTheSame(oldItem: Habit, newItem: Habit): Boolean = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Habit, newItem: Habit): Boolean = oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HabitVH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_all_habit, parent, false)
        return HabitVH(v)
    }

    override fun onBindViewHolder(holder: HabitVH, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    inner class HabitVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val name: TextView = itemView.findViewById(R.id.tv_all_name)
        private val date: TextView = itemView.findViewById(R.id.tv_all_date)
        private val check: MaterialCheckBox = itemView.findViewById(R.id.cb_all_completed)
        private val btnEdit: ImageButton = itemView.findViewById(R.id.btn_all_edit)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btn_all_delete)

        fun bind(item: Habit) {
            name.text = item.name
            date.text = item.date
            check.setOnCheckedChangeListener(null)
            check.isChecked = item.completed
            check.setOnCheckedChangeListener { _, isChecked -> onToggle(item, isChecked) }
            btnEdit.setOnClickListener { onEdit(item) }
            btnDelete.setOnClickListener { onDelete(item) }
        }
    }
}

