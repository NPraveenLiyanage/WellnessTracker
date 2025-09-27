package com.example.wellnesstracker.util

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.wellnesstracker.R
import com.example.wellnesstracker.work.HydrationWorker
import java.time.Duration
import java.util.concurrent.TimeUnit

object HydrationScheduler {
    private const val PREF_KEY_ENABLED = "hydration_enabled"
    private const val PREF_KEY_INTERVAL = "hydration_interval"
    private const val DEFAULT_INTERVAL_MIN = 60
    private const val MIN_INTERVAL_MIN = 15

    private const val CHANNEL_ID = "hydration_channel"
    private const val NOTIFICATION_ID = 1001

    private const val UNIQUE_WORK_NAME = "hydration"

    private fun prefs(context: Context): SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(PREF_KEY_ENABLED, true)

    fun getIntervalMinutes(context: Context): Int {
        val raw = prefs(context).getString(PREF_KEY_INTERVAL, DEFAULT_INTERVAL_MIN.toString())
        val parsed = raw?.toIntOrNull() ?: DEFAULT_INTERVAL_MIN
        return parsed.coerceAtLeast(MIN_INTERVAL_MIN)
    }

    // Schedules periodic hydration reminders
    fun schedule(context: Context) {
        if (!isEnabled(context)) return
        val minutes = getIntervalMinutes(context)
        try {
            val request = PeriodicWorkRequestBuilder<HydrationWorker>(
                Duration.ofMinutes(minutes.toLong())
            )
                .addTag(UNIQUE_WORK_NAME)
                .build()

            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request
                )
        } catch (_: Exception) {
            // Fallback to AlarmManager if WorkManager fails
            scheduleAlarmFallback(context, minutes)
        }
    }

    // Cancels any scheduled hydration reminders
    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork(UNIQUE_WORK_NAME)
        cancelAlarmFallback(context)
    }

    // Reschedules periodic hydration reminders
    fun reschedule(context: Context) {
        cancel(context)
        schedule(context)
    }

    // Build and show hydration notification
    fun showHydrationNotification(context: Context) {
        ensureChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_water)
            .setContentTitle(context.getString(R.string.hydration_title))
            .setContentText(context.getString(R.string.hydration_content))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val name = context.getString(R.string.hydration_channel_name)
            val desc = context.getString(R.string.hydration_channel_desc)
            val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = desc
            }
            manager.createNotificationChannel(channel)
        }
    }

    // --- AlarmManager fallback ---

    private fun scheduleAlarmFallback(context: Context, minutes: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = alarmPendingIntent(context)
        val intervalMs = TimeUnit.MINUTES.toMillis(minutes.toLong())
        val triggerAtMs = System.currentTimeMillis() + intervalMs
        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            triggerAtMs,
            intervalMs,
            pi
        )
    }

    private fun cancelAlarmFallback(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(alarmPendingIntent(context))
    }

    private fun alarmPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, com.example.wellnesstracker.HydrationAlarmReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }
}

