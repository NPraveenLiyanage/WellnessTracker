package com.example.wellnesstracker.util

import android.content.Context
import android.content.SharedPreferences
import com.example.wellnesstracker.model.Habit
import com.example.wellnesstracker.model.Mood
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Simple helper around SharedPreferences to persist habits per date and a monotonic habit ID.
 * Also persists a flat list of moods across dates.
 */
object SharedPrefsHelper {
    private const val PREFS_NAME = "wellness_prefs"
    private const val KEY_HABITS_PREFIX = "habits_" // key is habits_yyyy-MM-dd
    private const val KEY_ID_COUNTER = "habit_id_counter"

    // New: single key storing the entire moods history list
    private const val KEY_MOODS_ALL = "moods_all"

    // Onboarding completion flag
    private const val KEY_ONBOARDING_DONE = "onboarding_done"

    // New: per-date steps key prefix
    private const val KEY_STEPS_PREFIX = "steps_" // key is steps_yyyy-MM-dd

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Loads the habits list for the given ISO date (yyyy-MM-dd). */
    fun loadHabitsForDate(context: Context, date: String): MutableList<Habit> {
        val key = KEY_HABITS_PREFIX + date
        val json = prefs(context).getString(key, null) ?: return mutableListOf()
        return try {
            val type = object : TypeToken<MutableList<Habit>>() {}.type
            Gson().fromJson<MutableList<Habit>>(json, type) ?: mutableListOf()
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    /** Persists the given list of habits for the given ISO date (yyyy-MM-dd). */
    fun saveHabitsForDate(context: Context, date: String, habits: List<Habit>) {
        val key = KEY_HABITS_PREFIX + date
        val json = Gson().toJson(habits)
        prefs(context).edit().putString(key, json).apply()
    }

    /** Returns a new unique habit id and increments the counter. */
    fun nextHabitId(context: Context): Int {
        val p = prefs(context)
        val current = p.getInt(KEY_ID_COUNTER, 0)
        val next = current + 1
        p.edit().putInt(KEY_ID_COUNTER, next).apply()
        return next
    }

    // --- Mood persistence ---

    /** Loads all moods ever saved. Returns an empty mutable list if missing or malformed. */
    fun loadAllMoods(context: Context): MutableList<Mood> {
        val json = prefs(context).getString(KEY_MOODS_ALL, null) ?: return mutableListOf()
        return try {
            val type = object : TypeToken<MutableList<Mood>>() {}.type
            Gson().fromJson<MutableList<Mood>>(json, type) ?: mutableListOf()
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    /** Persists the entire moods history list. Overwrites the previous value. */
    fun saveAllMoods(context: Context, moods: List<Mood>) {
        val json = Gson().toJson(moods)
        prefs(context).edit().putString(KEY_MOODS_ALL, json).apply()
    }

    // --- Onboarding flag ---

    fun isOnboardingDone(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ONBOARDING_DONE, false)

    fun setOnboardingDone(context: Context, done: Boolean = true) {
        prefs(context).edit().putBoolean(KEY_ONBOARDING_DONE, done).apply()
    }

    // --- Steps persistence (per date) ---

    /** Gets the stored step count for the given ISO date (yyyy-MM-dd). Returns 0 if missing. */
    fun getStepsForDate(context: Context, date: String): Int {
        return prefs(context).getInt(KEY_STEPS_PREFIX + date, 0)
    }

    /** Sets the step count for the given date. */
    fun setStepsForDate(context: Context, date: String, steps: Int) {
        prefs(context).edit().putInt(KEY_STEPS_PREFIX + date, steps.coerceAtLeast(0)).apply()
    }

    /** Adds delta steps (can be positive) to the given date and returns the new total. */
    fun addStepsForDate(context: Context, date: String, delta: Int): Int {
        if (delta == 0) return getStepsForDate(context, date)
        val p = prefs(context)
        val key = KEY_STEPS_PREFIX + date
        val current = p.getInt(key, 0)
        val next = (current + delta).coerceAtLeast(0)
        p.edit().putInt(key, next).apply()
        return next
    }
}
