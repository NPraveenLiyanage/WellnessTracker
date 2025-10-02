package com.example.wellnesstracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceManager
import com.example.wellnesstracker.databinding.ActivityMainBinding
import com.example.wellnesstracker.util.HydrationScheduler
import com.example.wellnesstracker.util.PackageUtils
import com.example.wellnesstracker.util.SharedPrefsHelper
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private val TAG = "MainActivity"

    companion object {
        const val ACTION_SHOW_HABITS = "com.example.wellnesstracker.SHOW_HABITS"
        const val ACTION_SHOW_MOOD = "com.example.wellnesstracker.SHOW_MOOD"
        const val ACTION_SHOW_SETTINGS = "com.example.wellnesstracker.SHOW_SETTINGS"
        const val EXTRA_NAVIGATE_TO = "navigate_to" // values: habits|mood|settings
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Removed installSplashScreen() to avoid runtime dependency issues
        super.onCreate(savedInstanceState)

        try {
            File(filesDir, "startup_trace.txt").appendText("Main.onCreate\n")
            Log.i(TAG, "onCreate start")

            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            enableEdgeToEdge()

            // If onboarding hasn't been completed, redirect there first
            val onboardingDone = try {
                SharedPrefsHelper.isOnboardingDone(this)
            } catch (e: Throwable) {
                Log.e(TAG, "Error reading onboarding flag, proceeding to app", e)
                true
            }
            if (!onboardingDone) {
                Log.i(TAG, "Onboarding not done -> launching OnboardingActivity and finishing Main")
                startActivity(Intent(this, OnboardingActivity::class.java))
                finish()
                return
            }

            // Inflate and set content view
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root) // Hide the default ActionBar to keep only the custom header with logo
            supportActionBar?.hide()
            Log.i(TAG, "setContentView complete")

            // Subtle content animation
            binding.root.apply {
                alpha = 0f
                scaleX = 0.98f
                scaleY = 0.98f
                animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(280).start()
            }

            // Setup NavController
            val hostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
                ?: throw IllegalStateException("NavHostFragment not found in activity_main layout")
            navController = hostFragment.navController
            binding.bottomNav.setupWithNavController(navController)
            Log.i(TAG, "NavController setup complete")

            // Apply window inset padding
            ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
                val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                // Keep root view free of top inset so header sits at top edge
                v.setPadding(sys.left, 0, sys.right, sys.bottom)

                // Apply the top inset to the header so its contents are positioned below the status bar
                try {
                    val headerExtra = resources.getDimensionPixelSize(R.dimen.header_extra_top)
                    val left = binding.headerBar?.paddingLeft ?: 0
                    val right = binding.headerBar?.paddingRight ?: 0
                    val bottom = binding.headerBar?.paddingBottom ?: 0
                    binding.headerBar?.setPadding(left, sys.top + headerExtra, right, bottom)
                } catch (_: Exception) {
                }

                // Return the WindowInsetsCompat to indicate we've handled insets
                insets
            }

            // Initialize default preference values
            PreferenceManager.setDefaultValues(this, R.xml.preferences, false)

            // Request notification permission on API 33+
            requestNotificationPermissionIfNeeded()

            // Schedule hydration reminders
            HydrationScheduler.schedule(this)

            // Refresh the widget
            try {
                WellnessWidget.updateAll(this)
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to update widget", t)
            }

            // Check common third-party packages in a safe, well-handled way to avoid spamming logs
            try {
                PackageUtils.logInstalledPackages(this)
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to perform package visibility checks", t)
            }

            // Handle any navigation intent
            handleIntent(intent)

            Log.i(TAG, "MainActivity ready")
            File(filesDir, "startup_trace.txt").appendText("Main.ready\n")

        } catch (t: Throwable) {
            Log.e(TAG, "Unhandled throwable in onCreate", t)
            // write crash file for diagnosis
            try {
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                t.printStackTrace(pw)
                pw.flush()
                val trace = sw.toString()
                val outFile = File(filesDir, "last_crash.txt")
                outFile.writeText("Unhandled throwable in MainActivity.onCreate:\n\n$trace")
            } catch (io: Throwable) {
                Log.e(TAG, "Failed to write crash file", io)
            }
            // Safe Mode removed: do not attempt to start a separate SafeModeActivity; instead finish.
            finish()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val dest = when {
            intent == null -> null
            ACTION_SHOW_HABITS == intent.action || intent.getStringExtra(EXTRA_NAVIGATE_TO) == "habits" -> R.id.habitFragment
            ACTION_SHOW_MOOD == intent.action || intent.getStringExtra(EXTRA_NAVIGATE_TO) == "mood" -> R.id.moodFragment
            ACTION_SHOW_SETTINGS == intent.action || intent.getStringExtra(EXTRA_NAVIGATE_TO) == "settings" -> R.id.settingsFragment
            else -> null
        }
        dest?.let { destinationId ->
            val current = navController.currentDestination?.id
            if (current != destinationId) {
                val options = navOptions {
                    anim {
                        enter = android.R.anim.fade_in
                        exit = android.R.anim.fade_out
                        popEnter = android.R.anim.fade_in
                        popExit = android.R.anim.fade_out
                    }
                    popUpTo(R.id.habitFragment) { inclusive = false }
                }
                navController.navigate(destinationId, null, options)
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(permission), 100)
            }
        }
    }
}