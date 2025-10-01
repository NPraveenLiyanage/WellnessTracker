package com.example.wellnesstracker

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.example.wellnesstracker.util.SharedPrefsHelper
import java.io.File

class SplashActivity : AppCompatActivity() {
    private val TAG = "SplashActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Record startup
        try {
            File(filesDir, "startup_trace.txt").appendText("Splash.onCreate\n")
        } catch (_: Throwable) {}

        // Removed SafeMode redirect: we no longer route to a separate SafeModeActivity on crash.

        // Small delay so the theme windowBackground splash is visible on fast devices
        Handler(Looper.getMainLooper()).postDelayed({
            routeNext()
        }, 800)
    }

    private fun routeNext() {
        val forceOnboarding = intent.getBooleanExtra("force_onboarding", false)

        // Test shortcut: if a file named 'force_onboarding' exists in filesDir, show onboarding.
        val debugForce = try {
            filesDir.resolve("force_onboarding").exists()
        } catch (_: Throwable) {
            false
        }

        val onboardingDone = try {
            SharedPrefsHelper.isOnboardingDone(this)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed reading onboarding flag, defaulting to done", t)
            true
        }

        val goToOnboarding = forceOnboarding || debugForce || !onboardingDone
        Log.d(TAG, "onboardingDone=$onboardingDone forceOnboarding=$forceOnboarding debugForce=$debugForce -> launching ${if (goToOnboarding) "OnboardingActivity" else "MainActivity"}")

        try {
            File(filesDir, "startup_trace.txt").appendText("Splash.routeNext -> ${if (goToOnboarding) "Onboarding" else "Main"}\n")
        } catch (_: Throwable) {}

        val target = if (goToOnboarding) OnboardingActivity::class.java else MainActivity::class.java
        startActivity(Intent(this, target))
        finish()
    }
}
