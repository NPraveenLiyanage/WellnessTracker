package com.example.wellnesstracker.ui

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.wellnesstracker.model.Mood
import com.example.wellnesstracker.util.SharedPrefsHelper
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MoodViewModel : ViewModel() {
    private val _moods = MutableLiveData<List<Mood>>(emptyList())
    val moods: LiveData<List<Mood>> = _moods

    /** Loads all moods from SharedPreferences. */
    fun load(context: Context) {
        _moods.value = SharedPrefsHelper.loadAllMoods(context)
    }

    /** Adds a new mood and persists the full list. */
    fun addMood(context: Context, emoji: String, note: String?) {
        val now = LocalDateTime.now()
        val date = now.toLocalDate().toString() // yyyy-MM-dd
        val time = now.format(DateTimeFormatter.ofPattern("HH:mm"))
        val newMood = Mood(date = date, time = time, emoji = emoji, note = note?.trim().takeIf { !it.isNullOrEmpty() })
        val updated = _moods.value.orEmpty().toMutableList().apply { add(0, newMood) }
        _moods.value = updated
        SharedPrefsHelper.saveAllMoods(context, updated)
    }

    /** Optionally clears all moods (not used but handy for tests/debug). */
    fun clearAll(context: Context) {
        _moods.value = emptyList()
        SharedPrefsHelper.saveAllMoods(context, emptyList())
    }

    /** Returns moods filtered to the given ISO date. */
    fun moodsForDate(date: LocalDate): List<Mood> = _moods.value.orEmpty().filter { it.date == date.toString() }

    /** Updates a mood at the given list position with new emoji/note, preserving date/time. */
    fun updateMood(context: Context, position: Int, newEmoji: String, newNote: String?) {
        val current = _moods.value.orEmpty().toMutableList()
        if (position < 0 || position >= current.size) return
        val old = current[position]
        val updatedItem = Mood(date = old.date, time = old.time, emoji = newEmoji, note = newNote?.trim().takeIf { !it.isNullOrEmpty() })
        current[position] = updatedItem
        _moods.value = current.toList()
        SharedPrefsHelper.saveAllMoods(context, current)
    }

    /** Deletes a mood at the given list position. */
    fun deleteMood(context: Context, position: Int) {
        val current = _moods.value.orEmpty().toMutableList()
        if (position < 0 || position >= current.size) return
        current.removeAt(position)
        _moods.value = current.toList()
        SharedPrefsHelper.saveAllMoods(context, current)
    }
}
