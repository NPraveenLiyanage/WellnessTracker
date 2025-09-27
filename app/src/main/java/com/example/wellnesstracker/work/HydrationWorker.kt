package com.example.wellnesstracker.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.wellnesstracker.util.HydrationScheduler

/**
 * Worker that posts a hydration reminder notification.
 */
class HydrationWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): androidx.work.ListenableWorker.Result {
        // Build and show the hydration notification
        HydrationScheduler.showHydrationNotification(applicationContext)
        return androidx.work.ListenableWorker.Result.success()
    }
}
