package com.example.wellnesstracker

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.example.wellnesstracker.util.SharedPrefsHelper
import java.time.LocalDate

class WellnessWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        val today = LocalDate.now().toString()
        val habits = SharedPrefsHelper.loadHabitsForDate(context, today)
        val total = habits.size
        val done = habits.count { it.completed }
        val percent = if (total == 0) 0 else ((done * 100f) / total).toInt()

        appWidgetIds.forEach { appWidgetId ->
            val views = RemoteViews(context.packageName, R.layout.widget_wellness).apply {
                setTextViewText(R.id.textTitle, context.getString(R.string.app_name))
                setTextViewText(R.id.textProgress, context.getString(R.string.widget_progress_format, percent))
                setProgressBar(R.id.progressBar, 100, percent, false)

                val intent = Intent(context, MainActivity::class.java).apply {
                    action = MainActivity.ACTION_SHOW_HABITS
                    putExtra(MainActivity.EXTRA_NAVIGATE_TO, "habits")
                }
                val flags = if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_UPDATE_CURRENT
                val pendingIntent = PendingIntent.getActivity(context, 0, intent, flags)
                setOnClickPendingIntent(R.id.widgetRoot, pendingIntent)
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WellnessWidget::class.java))
            if (ids.isNotEmpty()) {
                // Delegate to onUpdate by sending the broadcast
                val intent = Intent(context, WellnessWidget::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }
    }
}

