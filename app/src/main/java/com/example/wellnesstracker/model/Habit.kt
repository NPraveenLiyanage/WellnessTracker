package com.example.wellnesstracker.model

/**
 * Represents a single habit the user is tracking.
 *
 * @property id Stable identifier for the habit (unique within the app).
 * @property name Human-readable habit name (e.g., "Drink Water").
 * @property completed Whether the habit is marked as completed for the given date.
 * @property date ISO-8601 date string (yyyy-MM-dd) the completion status applies to.
 */
data class Habit(
    val id: Int,
    val name: String,
    var completed: Boolean,
    val date: String
)

