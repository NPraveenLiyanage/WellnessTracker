package com.example.wellnesstracker.ui

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.example.wellnesstracker.R
import com.google.android.material.transition.MaterialFadeThrough
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.appcompat.app.AlertDialog
import android.text.InputType

class SettingsFragment : PreferenceFragmentCompat() {

    private lateinit var prefs: SharedPreferences
    private var prefListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Subtle fade transitions between fragments
        enterTransition = MaterialFadeThrough()
        exitTransition = MaterialFadeThrough()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        // Inflate custom layout that contains a toolbar and a placeholder container
        val root = inflater.inflate(R.layout.fragment_settings_preferences, container, false)

        // Let PreferenceFragmentCompat create its internal preference list view
        val prefView = super.onCreateView(inflater, container, savedInstanceState)

        // If our custom layout contains a placeholder container, move the preference view there.
        // If not, leave the preference view attached to its default parent to avoid removing it.
        val holder = root.findViewById<ViewGroup?>(resources.getIdentifier("settings_container", "id", requireContext().packageName))
        if (holder != null && prefView != null) {
            // detach from default parent first, then attach into our holder
            (prefView.parent as? ViewGroup)?.removeView(prefView)
            holder.addView(prefView)
        }

        // Bind hydration controls to SharedPreferences
        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())

        // Resolve IDs at runtime to avoid potential generated R class timing issues
        val switchId = resources.getIdentifier("switch_hydration_reminder", "id", requireContext().packageName)
        val tvIntervalId = resources.getIdentifier("tv_hydration_interval_value", "id", requireContext().packageName)
        val btnEditId = resources.getIdentifier("btn_edit_hydration_interval", "id", requireContext().packageName)

        val switchHydration = if (switchId != 0) root.findViewById<androidx.appcompat.widget.SwitchCompat>(switchId) else null
        val tvInterval = if (tvIntervalId != 0) root.findViewById<TextView>(tvIntervalId) else null
        val btnEdit = if (btnEditId != 0) root.findViewById<Button>(btnEditId) else null

        // Initialize UI from preferences
        val enabled = prefs.getBoolean("hydration_enabled", true)
        switchHydration?.isChecked = enabled

        val interval = prefs.getString("hydration_interval", "60") ?: "60"
        tvInterval?.text = getString(R.string.pref_summary_hydration_interval, interval)

        // helper to enable/disable interval controls
        fun updateIntervalControls(enabledControls: Boolean) {
            tvInterval?.isEnabled = enabledControls
            btnEdit?.isEnabled = enabledControls
            val alpha = if (enabledControls) 1.0f else 0.5f
            tvInterval?.alpha = alpha
            btnEdit?.alpha = alpha
        }

        // initialize controls state
        updateIntervalControls(enabled)

        // Switch listener -> update preference
        switchHydration?.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("hydration_enabled", isChecked).apply()
            updateIntervalControls(isChecked)
        }

        // Edit interval button -> show custom dialog to set numeric interval
        btnEdit?.setOnClickListener {
            val dialogView = layoutInflater.inflate(R.layout.dialog_hydration_interval, null)
            val et = dialogView.findViewById<EditText>(R.id.et_interval_input)
            et.setText(prefs.getString("hydration_interval", "60"))

            val dialog = AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create()

            val btnCancel = dialogView.findViewById<Button>(R.id.btn_cancel_interval)
            val btnSave = dialogView.findViewById<Button>(R.id.btn_save_interval)

            btnCancel.setOnClickListener { dialog.dismiss() }
            btnSave.setOnClickListener {
                val newVal = et.text.toString().ifBlank { "60" }
                prefs.edit().putString("hydration_interval", newVal).apply()
                tvInterval?.text = getString(R.string.pref_summary_hydration_interval, newVal)

                // Also update the corresponding EditTextPreference if present
                val intervalPref = findPreference<EditTextPreference>("hydration_interval")
                intervalPref?.text = newVal
                dialog.dismiss()
            }

            dialog.show()
        }

        // Keep UI in sync if preferences change elsewhere
        prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "hydration_enabled" -> {
                    val v = prefs.getBoolean(key, true)
                    if (switchHydration?.isChecked != v) switchHydration?.isChecked = v
                }
                "hydration_interval" -> {
                    val v = prefs.getString(key, "60") ?: "60"
                    tvInterval?.text = getString(R.string.pref_summary_hydration_interval, v)
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(prefListener)

        return root
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        // Summary: Remind every %s minutes
        val intervalPref = findPreference<EditTextPreference>("hydration_interval")
        intervalPref?.summaryProvider = Preference.SummaryProvider<EditTextPreference> { pref ->
            val value = pref.text ?: "60"
            getString(R.string.pref_summary_hydration_interval, value)
        }

        // No programmatic widget injection here; CustomSwitchPreference handles binding the Switch
    }

    override fun onDestroyView() {
        super.onDestroyView()
        prefListener?.let { prefs.unregisterOnSharedPreferenceChangeListener(it) }
        prefListener = null
    }
}
