package com.example.wellnesstracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.wellnesstracker.util.HydrationScheduler

/**
 * Receives BOOT_COMPLETED and re-schedules hydration reminders if enabled.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            // Re-schedule periodic hydration reminders after reboot
            HydrationScheduler.reschedule(context.applicationContext)
        }
    }
}
