package com.example.wellnesstracker

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.example.wellnesstracker.util.SharedPrefsHelper
import java.io.File
import java.time.LocalDate

class WellnessWidget : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED -> updateAll(context)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        try {
            val today = LocalDate.now().toString()
            val habits = SharedPrefsHelper.loadHabitsForDate(context, today)
            val total = habits.size
            val done = habits.count { it.completed }
            val percent = if (total == 0) 0 else ((done * 100f) / total).toInt()
            val stepsToday = SharedPrefsHelper.getStepsForDate(context, today)

            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_wellness)

                // Update UI
                views.setTextViewText(R.id.textTitle, context.getString(R.string.app_name))
                views.setTextViewText(R.id.tv_widget_habits_progress, context.getString(R.string.widget_progress_format, percent))
                views.setProgressBar(R.id.progressBar, 100, percent, false)
                views.setTextViewText(R.id.textSteps, context.getString(R.string.steps_today, stepsToday))

                // Main click: open app and navigate to habits
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    action = MainActivity.ACTION_SHOW_HABITS
                    putExtra(MainActivity.EXTRA_NAVIGATE_TO, "habits")
                }
                val mainPending = PendingIntent.getActivity(context, 0, launchIntent, flags)
                views.setOnClickPendingIntent(R.id.widgetRoot, mainPending)

                // Quick action: view habits
                val viewIntent = Intent(context, MainActivity::class.java).apply {
                    action = MainActivity.ACTION_SHOW_HABITS
                    putExtra(MainActivity.EXTRA_NAVIGATE_TO, "habits")
                }
                val viewPending = PendingIntent.getActivity(context, 1, viewIntent, flags)
                views.setOnClickPendingIntent(R.id.btn_widget_view_habits, viewPending)

                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        } catch (t: Throwable) {
            try {
                Log.e("WellnessWidget", "onUpdate failed", t)
                File(context.filesDir, "last_crash.txt").writeText("Widget onUpdate failed:\n\n${t.stackTraceToString()}")
            } catch (_: Throwable) {
                // ignore
            }
        }
    }

    companion object {
        fun updateAll(context: Context) {
            try {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, WellnessWidget::class.java))
                if (ids.isNotEmpty()) {
                    val intent = Intent(context, WellnessWidget::class.java).apply {
                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                    }
                    context.sendBroadcast(intent)
                }
            } catch (t: Throwable) {
                try {
                    Log.e("WellnessWidget", "updateAll failed", t)
                    File(context.filesDir, "last_crash.txt").writeText("Widget updateAll failed:\n\n${t.stackTraceToString()}")
                } catch (_: Throwable) { }
            }
        }
    }
}
