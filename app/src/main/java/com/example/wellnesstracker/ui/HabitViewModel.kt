package com.example.wellnesstracker.ui

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.wellnesstracker.model.Habit
import com.example.wellnesstracker.util.SharedPrefsHelper

class HabitViewModel : ViewModel() {
    private val _habits = MutableLiveData<List<Habit>>(emptyList())
    val habits: LiveData<List<Habit>> = _habits

    private val _progress = MutableLiveData<Int>(0)
    val progress: LiveData<Int> = _progress

    private var currentDate: String? = null

    fun load(context: Context, date: String) {
        currentDate = date
        val list = SharedPrefsHelper.loadHabitsForDate(context, date)
        _habits.value = list
        recalcProgress(list)
    }

    fun save(context: Context) {
        val date = currentDate ?: return
        SharedPrefsHelper.saveHabitsForDate(context, date, _habits.value.orEmpty())
    }

    fun addHabit(context: Context, name: String): Int {
        val date = currentDate ?: return -1
        val id = SharedPrefsHelper.nextHabitId(context)
        val newHabit = Habit(id = id, name = name, completed = false, date = date)
        val updated = _habits.value.orEmpty().toMutableList().apply { add(newHabit) }
        _habits.value = updated
        recalcProgress(updated)
        return id
    }

    fun editHabit(id: Int, newName: String) {
        val updated = _habits.value.orEmpty().map { h -> if (h.id == id) h.copy(name = newName) else h }
        _habits.value = updated
        recalcProgress(updated)
    }

    fun deleteHabit(id: Int) {
        val updated = _habits.value.orEmpty().filterNot { it.id == id }
        _habits.value = updated
        recalcProgress(updated)
    }

    fun setCompleted(id: Int, completed: Boolean) {
        val updated = _habits.value.orEmpty().map { h -> if (h.id == id) h.copy(completed = completed) else h }
        _habits.value = updated
        recalcProgress(updated)
    }

    private fun recalcProgress(list: List<Habit>) {
        val total = list.size
        val done = list.count { it.completed }
        val pct = if (total == 0) 0 else ((done * 100f) / total).toInt()
        _progress.value = pct
    }
}
