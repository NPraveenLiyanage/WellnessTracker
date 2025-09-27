package com.example.wellnesstracker.ui

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.example.wellnesstracker.R
import com.google.android.material.transition.MaterialFadeThrough

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Subtle fade transitions between fragments
        enterTransition = MaterialFadeThrough()
        exitTransition = MaterialFadeThrough()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        // Summary: Remind every %s minutes
        val intervalPref = findPreference<EditTextPreference>("hydration_interval")
        intervalPref?.summaryProvider = Preference.SummaryProvider<EditTextPreference> { pref ->
            val value = pref.text ?: "60"
            getString(R.string.pref_summary_hydration_interval, value)
        }
    }
}
