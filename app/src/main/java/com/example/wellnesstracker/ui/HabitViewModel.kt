package com.example.wellnesstracker.ui

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.wellnesstracker.model.Habit
import com.example.wellnesstracker.util.SharedPrefsHelper
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

class HabitViewModel : ViewModel() {
    private val _habits = MutableLiveData<List<Habit>>(emptyList())
    val habits: LiveData<List<Habit>> = _habits

    private val _progress = MutableLiveData<Int>(0)
    val progress: LiveData<Int> = _progress

    // weekly: 7 ints for Mon..Sun (index 0 = Monday)
    private val _weekly = MutableLiveData<List<Int>>(emptyList())
    val weekly: LiveData<List<Int>> = _weekly

    // corresponding ISO dates for the weekly data (Mon..Sun)
    private val _weeklyDates = MutableLiveData<List<String>>(emptyList())
    val weeklyDates: LiveData<List<String>> = _weeklyDates

    // When non-null this represents the currently loaded single date (yyyy-MM-dd). When null, _habits may contain "all".
    private var currentDate: String? = null

    fun load(context: Context, date: String) {
        currentDate = date
        val list = SharedPrefsHelper.loadHabitsForDate(context, date)
        _habits.value = list
        recalcProgress(list)
        recalcWeekly(context)
    }

    fun save(context: Context) {
        val date = currentDate ?: return
        SharedPrefsHelper.saveHabitsForDate(context, date, _habits.value.orEmpty())
    }

    fun addHabit(context: Context, name: String, date: String? = null): Int {
        val effectiveDate = date ?: currentDate ?: LocalDate.now().toString()
        val id = SharedPrefsHelper.nextHabitId(context)
        val newHabit = Habit(id = id, name = name, completed = false, date = effectiveDate)

        // Persist under the selected/effective date
        val listForDate = SharedPrefsHelper.loadHabitsForDate(context, effectiveDate).toMutableList()
        listForDate.add(newHabit)
        SharedPrefsHelper.saveHabitsForDate(context, effectiveDate, listForDate)

        // Update in-memory only if we're currently viewing that date
        if (currentDate == effectiveDate) {
            _habits.value = listForDate
            recalcProgress(listForDate)
        } else {
            // keep current in-memory list as-is, just refresh progress
            recalcProgress(_habits.value.orEmpty())
        }

        // Weekly always needs refresh
        recalcWeekly(context)
        return id
    }

    // Update name in-memory only
    fun editHabit(id: Int, newName: String) {
        val updated = _habits.value.orEmpty().map { if (it.id == id) it.copy(name = newName) else it }
        _habits.value = updated
        recalcProgress(updated)
    }

    // Persisted edit: find the habit's date and update persisted storage and in-memory if applicable
    fun editHabit(context: Context, id: Int, newName: String): Boolean {
        var changed = false
        // Search all dates to find the habit and update that date list
        val all = SharedPrefsHelper.loadAllHabits(context)
        val found = all.firstOrNull { it.id == id }
        if (found != null) {
            val date = found.date
            val list = SharedPrefsHelper.loadHabitsForDate(context, date)
            val updated = list.map { if (it.id == id) { changed = it.name != newName; it.copy(name = newName) } else it }
            if (changed) SharedPrefsHelper.saveHabitsForDate(context, date, updated)
            // Refresh in-memory view if it affects currentDate or we're showing all
            if (currentDate == null) {
                _habits.value = SharedPrefsHelper.loadAllHabits(context)
            } else if (currentDate == date) {
                _habits.value = SharedPrefsHelper.loadHabitsForDate(context, date)
            }
            recalcProgress(_habits.value.orEmpty())
            recalcWeekly(context)
        }
        return changed
    }

    // New: update name and/or move habit to a different date
    fun updateHabit(context: Context, id: Int, newName: String, newDate: String): Boolean {
        val all = SharedPrefsHelper.loadAllHabits(context)
        val found = all.firstOrNull { it.id == id } ?: return false
        val oldDate = found.date
        var changed = false

        if (newDate == oldDate) {
            // Only name change
            changed = SharedPrefsHelper.setHabitName(context, oldDate, id, newName)
        } else {
            // Move across dates (and update name)
            val oldList = SharedPrefsHelper.loadHabitsForDate(context, oldDate).filterNot { it.id == id }
            SharedPrefsHelper.saveHabitsForDate(context, oldDate, oldList)

            val moved = found.copy(name = newName, date = newDate)
            val newList = SharedPrefsHelper.loadHabitsForDate(context, newDate).toMutableList().apply { add(moved) }
            SharedPrefsHelper.saveHabitsForDate(context, newDate, newList)
            changed = true
        }

        if (changed) {
            // Refresh in-memory depending on what we're showing
            when (val cur = currentDate) {
                null -> _habits.value = SharedPrefsHelper.loadAllHabits(context)
                oldDate, newDate -> _habits.value = SharedPrefsHelper.loadHabitsForDate(context, cur)
                else -> { /* leave current list as-is */ }
            }
            recalcProgress(_habits.value.orEmpty())
            recalcWeekly(context)
        }
        return changed
    }

    // Update in-memory only
    fun setCompleted(id: Int, completed: Boolean) {
        val updated = _habits.value.orEmpty().map { if (it.id == id) it.copy(completed = completed) else it }
        _habits.value = updated
        recalcProgress(updated)
    }

    // Persist completion change for the habit's date (uses currentDate when available)
    fun setCompleted(context: Context, id: Int, completed: Boolean): Boolean {
        // If we have a current date, update that date list
        if (currentDate != null) {
            val changed = SharedPrefsHelper.setHabitCompleted(context, currentDate!!, id, completed)
            if (changed) {
                _habits.value = SharedPrefsHelper.loadHabitsForDate(context, currentDate!!)
                recalcProgress(_habits.value.orEmpty())
                recalcWeekly(context)
            }
            return changed
        }
        // Otherwise search across all dates
        val all = SharedPrefsHelper.loadAllHabits(context)
        val found = all.firstOrNull { it.id == id } ?: return false
        val changed = SharedPrefsHelper.setHabitCompleted(context, found.date, id, completed)
        if (changed) {
            // Refresh in-memory if needed
            if (currentDate == null) _habits.value = SharedPrefsHelper.loadAllHabits(context)
            recalcProgress(_habits.value.orEmpty())
            recalcWeekly(context)
        }
        return changed
    }

    // Remove in-memory only
    fun deleteHabit(id: Int) {
        val updated = _habits.value.orEmpty().filterNot { it.id == id }
        _habits.value = updated
        recalcProgress(updated)
    }

    // Persisted delete (searches correct date and deletes)
    fun deleteHabit(context: Context, id: Int): Boolean {
        // If currentDate set, try deleting from that date first
        if (currentDate != null) {
            val deleted = SharedPrefsHelper.deleteHabit(context, currentDate!!, id)
            if (deleted) {
                _habits.value = SharedPrefsHelper.loadHabitsForDate(context, currentDate!!)
                recalcProgress(_habits.value.orEmpty())
                recalcWeekly(context)
                return true
            }
        }
        // Otherwise search all dates
        val all = SharedPrefsHelper.loadAllHabits(context)
        val found = all.firstOrNull { it.id == id } ?: return false
        val deleted = SharedPrefsHelper.deleteHabit(context, found.date, id)
        if (deleted) {
            if (currentDate == null) _habits.value = SharedPrefsHelper.loadAllHabits(context) else if (currentDate == found.date) _habits.value = SharedPrefsHelper.loadHabitsForDate(context, currentDate!!)
            recalcProgress(_habits.value.orEmpty())
            recalcWeekly(context)
        }
        return deleted
    }

    private fun recalcProgress(list: List<Habit>) {
        val total = list.size
        val done = list.count { it.completed }
        val pct = if (total == 0) 0 else ((done * 100f) / total).toInt()
        _progress.value = pct
    }

    /**
     * Recalculates weekly percentages for a fixed Mon..Sun window.
     * If currentDate is set, the week containing that date is used; otherwise the current week is used.
     * _weekly will contain 7 integers (0..100) for Monday..Sunday and _weeklyDates the corresponding ISO date strings.
     */
    fun recalcWeekly(context: Context) {
        val base = try { LocalDate.parse(currentDate) } catch (_: Exception) { LocalDate.now() }
        val monday = base.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val values = mutableListOf<Int>()
        val dates = mutableListOf<String>()
        for (i in 0..6) {
            val day = monday.plusDays(i.toLong())
            dates.add(day.toString())
            val list = SharedPrefsHelper.loadHabitsForDate(context, day.toString())
            val total = list.size
            val done = list.count { it.completed }
            val pct = if (total == 0) 0 else ((done * 100f) / total).toInt()
            values.add(pct.coerceIn(0, 100))
        }
        _weekly.value = values
        _weeklyDates.value = dates
    }
}
