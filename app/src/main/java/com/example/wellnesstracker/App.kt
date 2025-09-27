package com.example.wellnesstracker

import android.app.Application
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class App : Application() {
    private val TAG = "WellnessApp"
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null

    override fun onCreate() {
        super.onCreate()
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                e.printStackTrace(pw)
                pw.flush()
                val trace = sw.toString()
                Log.e(TAG, "Uncaught exception in thread ${t.name}: $trace")
                try {
                    val outFile = File(filesDir, "last_crash.txt")
                    outFile.writeText("Thread: ${t.name}\n\n$trace")
                } catch (_: Exception) {
                    Log.e(TAG, "Failed writing crash file", null)
                }
            } catch (_: Exception) {
                // nothing
            } finally {
                // Pass to default handler (will terminate the process)
                defaultHandler?.uncaughtException(t, e)
            }
        }

        // Ensure any existing widgets refresh when the app starts so new layout and step count appear
        try {
            WellnessWidget.updateAll(applicationContext)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to refresh widget on app start", e)
        }
    }
}
