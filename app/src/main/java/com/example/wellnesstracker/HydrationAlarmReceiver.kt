package com.example.wellnesstracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.wellnesstracker.util.HydrationScheduler

/**
 * AlarmManager fallback receiver that shows the hydration notification when fired.
 */
class HydrationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        // Show hydration notification when alarm triggers
        HydrationScheduler.showHydrationNotification(context.applicationContext)
    }
}

