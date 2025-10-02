package com.example.wellnesstracker

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
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
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> updateAll(context)
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

            // Load latest mood (if any)
            val moods = SharedPrefsHelper.loadAllMoods(context)
            val latestMood = moods.lastOrNull()

            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_wellness)

                // Update UI
                views.setTextViewText(R.id.textTitle, context.getString(R.string.app_name))
                views.setTextViewText(R.id.tv_widget_habits_progress, context.getString(R.string.widget_progress_format, percent))
                views.setProgressBar(R.id.progressBar, 100, percent, false)
                // Ensure logo drawable is applied (RemoteViews may require explicit set)
                try {
                    views.setImageViewResource(R.id.img_logo, R.drawable.trans_logo)
                    views.setViewVisibility(R.id.img_logo, View.VISIBLE)
                } catch (_: Throwable) { /* ignore if resource not available at runtime */ }

                // Top summary: latest mood + steps (layout now contains top summary only)
                if (latestMood != null) {
                    views.setTextViewText(R.id.tv_widget_mood_emoji_top, latestMood.emoji)
                    // Show a short meaning for the emoji (e.g. "Happy", "Sad") instead of date
                    val meaning = when (latestMood.emoji) {
                        "😊" -> context.getString(R.string.mood_meaning_happy)
                        "🙂" -> context.getString(R.string.mood_meaning_smile)
                        "😐" -> context.getString(R.string.mood_meaning_neutral)
                        "😢" -> context.getString(R.string.mood_meaning_sad)
                        "😡" -> context.getString(R.string.mood_meaning_angry)
                        "😠" -> context.getString(R.string.mood_meaning_mad)
                        "😴" -> context.getString(R.string.mood_meaning_sleepy)
                        "😰" -> context.getString(R.string.mood_meaning_anxious)
                        else -> context.getString(R.string.widget_no_recent_mood)
                    }
                    views.setTextViewText(R.id.tv_widget_mood_text_top, meaning)
                    views.setViewVisibility(R.id.tv_widget_mood_text_top, View.VISIBLE)
                } else {
                    views.setTextViewText(R.id.tv_widget_mood_emoji_top, "—")
                    views.setTextViewText(R.id.tv_widget_mood_text_top, "")
                    views.setViewVisibility(R.id.tv_widget_mood_text_top, View.GONE)
                }
                // Show only the numeric steps count (no "steps" label)
                views.setTextViewText(R.id.tv_widget_steps_top, stepsToday.toString())

                // Main click: open app and navigate to habits
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    action = MainActivity.ACTION_SHOW_HABITS
                    putExtra(MainActivity.EXTRA_NAVIGATE_TO, "habits")
                }
                val mainPending = PendingIntent.getActivity(context, 0, launchIntent, flags)
                views.setOnClickPendingIntent(R.id.widgetRoot, mainPending)

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
