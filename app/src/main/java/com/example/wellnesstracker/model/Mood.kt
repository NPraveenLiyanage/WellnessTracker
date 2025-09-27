package com.example.wellnesstracker.model

/**
 * Represents a single mood entry.
 *
 * @property date ISO-8601 date string (yyyy-MM-dd) when the mood was logged.
 * @property time Time string (e.g., HH:mm) when the mood was logged.
 * @property emoji Short string representing the mood (e.g., "🙂", or a name).
 * @property note Optional free-form note associated with the mood.
 */
data class Mood(
    val date: String,
    val time: String,
    val emoji: String,
    val note: String?
)

