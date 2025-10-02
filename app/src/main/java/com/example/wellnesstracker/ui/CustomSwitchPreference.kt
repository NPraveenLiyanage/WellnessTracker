package com.example.wellnesstracker.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.example.wellnesstracker.R
import com.google.android.material.switchmaterial.SwitchMaterial

/**
 * Custom Preference that binds a SwitchMaterial from our pref_switch_full layout and
 * persists the boolean value to SharedPreferences. It also triggers HydrationScheduler.
 */
class CustomSwitchPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle
) : Preference(context, attrs, defStyleAttr) {

    init {
        // Use our custom layout for this preference
        layoutResource = R.layout.pref_switch_full
        isPersistent = true
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        try {
            var switch = holder.itemView.findViewById<SwitchMaterial>(R.id.pref_switch)
            if (switch == null) {
                // Fallback: inflate our full layout into the holder if the switch wasn't present
                try {
                    val root = holder.itemView as? ViewGroup
                    val inflated = View.inflate(context, R.layout.pref_switch_full, null)
                    // Find the switch in the inflated layout
                    val newSwitch = inflated.findViewById<SwitchMaterial>(R.id.pref_switch)
                    if (root != null && newSwitch != null) {
                        // remove any existing inflated children that match id to avoid duplicates
                        // attach inflated's switch to the holder view
                        root.addView(inflated)
                        switch = newSwitch
                    }
                } catch (_: Exception) {
                }
            }

            if (switch != null) {
                val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
                val current = prefs.getBoolean(key, true)
                switch.isChecked = current

                // Avoid allowing the switch itself to handle persistence; we will
                switch.setOnCheckedChangeListener { _, isChecked ->
                    prefs.edit().putBoolean(key, isChecked).apply()
                    if (isChecked) com.example.wellnesstracker.util.HydrationScheduler.schedule(context) else com.example.wellnesstracker.util.HydrationScheduler.cancel(context)
                }

                // Make the row toggle the switch for convenience
                holder.itemView.setOnClickListener {
                    val newVal = !(prefs.getBoolean(key, true))
                    prefs.edit().putBoolean(key, newVal).apply()
                    switch.isChecked = newVal
                    if (newVal) com.example.wellnesstracker.util.HydrationScheduler.schedule(context) else com.example.wellnesstracker.util.HydrationScheduler.cancel(context)
                }
            }
        } catch (e: Exception) {
            // Best effort; ignore
        }
    }
}
