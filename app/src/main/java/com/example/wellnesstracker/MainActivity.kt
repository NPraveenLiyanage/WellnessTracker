package com.example.wellnesstracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.NavController
import androidx.navigation.navOptions
import androidx.navigation.ui.setupWithNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import com.example.wellnesstracker.WellnessWidget
import com.example.wellnesstracker.databinding.ActivityMainBinding
import com.example.wellnesstracker.util.HydrationScheduler

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    // Explicit intent actions for direct navigation
    companion object {
        const val ACTION_SHOW_HABITS = "com.example.wellnesstracker.SHOW_HABITS"
        const val ACTION_SHOW_MOOD = "com.example.wellnesstracker.SHOW_MOOD"
        const val ACTION_SHOW_SETTINGS = "com.example.wellnesstracker.SHOW_SETTINGS"
        const val EXTRA_NAVIGATE_TO = "navigate_to" // values: habits|mood|settings
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Obtain NavController from the NavHostFragment directly to avoid relying on view tags
        val hostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
            ?: throw IllegalStateException("NavHostFragment not found in activity_main layout")
        navController = hostFragment.navController
        binding.bottomNav.setupWithNavController(navController)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize default values for preferences (only once)
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)

        // Request notification permission on API 33+
        requestNotificationPermissionIfNeeded()

        // Schedules periodic hydration reminders
        HydrationScheduler.schedule(this)

        // Ensure home-screen widget shows latest progress when app is opened
        WellnessWidget.updateAll(this)

        // Handle any explicit navigation intent that launched the Activity
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle explicit navigation intents while activity is in foreground
        handleIntent(intent)
    }

    // Handles explicit intents for fragment navigation via NavController
    private fun handleIntent(intent: Intent?) {
        val dest = when {
            intent == null -> null
            // Accept either explicit actions or a simple string extra
            ACTION_SHOW_HABITS == intent.action || intent.getStringExtra(EXTRA_NAVIGATE_TO) == "habits" -> R.id.habitFragment
            ACTION_SHOW_MOOD == intent.action || intent.getStringExtra(EXTRA_NAVIGATE_TO) == "mood" -> R.id.moodFragment
            ACTION_SHOW_SETTINGS == intent.action || intent.getStringExtra(EXTRA_NAVIGATE_TO) == "settings" -> R.id.settingsFragment
            else -> null
        }
        dest?.let { destinationId ->
            val current = navController.currentDestination?.id
            if (current != destinationId) {
                val options = navOptions {
                    // Simple fade-like behavior by avoiding default slide animations
                    anim {
                        enter = android.R.anim.fade_in
                        exit = android.R.anim.fade_out
                        popEnter = android.R.anim.fade_in
                        popExit = android.R.anim.fade_out
                    }
                    // Pop up to the start to avoid deep stacks if launched repeatedly
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