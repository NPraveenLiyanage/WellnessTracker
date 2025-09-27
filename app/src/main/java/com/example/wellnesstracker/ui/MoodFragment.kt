package com.example.wellnesstracker.ui

import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.wellnesstracker.R
import com.example.wellnesstracker.databinding.FragmentMoodBinding
import com.example.wellnesstracker.model.Mood
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.transition.MaterialFadeThrough
import java.time.LocalDate

class MoodFragment : Fragment() {
    private var _binding: FragmentMoodBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MoodViewModel by viewModels()
    private lateinit var adapter: MoodAdapter

    private var selectedMood: Mood? = null
    private var selectedPosition: Int = -1

    // Transient UI state for rotation: recycler scroll and selection
    private var pendingSelectPosition: Int? = null
    private var recyclerState: Parcelable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Smooth, subtle transition between fragments
        enterTransition = MaterialFadeThrough()
        exitTransition = MaterialFadeThrough()
        // Restore transient state markers
        savedInstanceState?.let {
            pendingSelectPosition = it.getInt(KEY_SELECTED_POS, -1).takeIf { p -> p >= 0 }
            recyclerState = it.getParcelable(KEY_RECYCLER_STATE)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMoodBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // RecyclerView setup
        adapter = MoodAdapter { mood, position ->
            // Persists selection visually and enables the Share button
            selectedMood = mood
            selectedPosition = position
            adapter.setSelected(position)
            binding.buttonShare.isEnabled = true
        }
        val layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerMoods.layoutManager = layoutManager
        binding.recyclerMoods.adapter = adapter

        // Restore scroll position if available
        recyclerState?.let { state ->
            binding.recyclerMoods.post { layoutManager.onRestoreInstanceState(state) }
        }

        // Add mood FAB
        binding.fabAddMood.setOnClickListener { showAddMoodDialog() }

        // Handles sharing mood summary via implicit intent
        binding.buttonShare.setOnClickListener { shareSelectedMood() }

        // Configure chart base styling once
        setupChart()

        // Observe moods
        viewModel.moods.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            // Disable share when selection becomes invalid (e.g., list changed)
            if (selectedPosition !in list.indices || (selectedMood != null && list.getOrNull(selectedPosition) != selectedMood)) {
                selectedMood = null
                selectedPosition = -1
                binding.buttonShare.isEnabled = false
            }
            // If we restored a pending selection, apply it once data arrives
            pendingSelectPosition?.let { p ->
                if (p in list.indices) {
                    selectedPosition = p
                    selectedMood = list[p]
                    adapter.setSelected(p)
                    binding.buttonShare.isEnabled = true
                }
                pendingSelectPosition = null
            }
            // Update chart data whenever the list changes
            updateChart(list)
            // Empty state handling for list
            binding.recyclerMoods.isVisible = list.isNotEmpty()
        }

        // Load persisted data
        viewModel.load(requireContext())
    }

    /** Shows dialog to pick an emoji and optionally enter a note. */
    private fun showAddMoodDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_mood, null)
        val editNote = dialogView.findViewById<EditText>(R.id.editNote)

        var chosenEmoji: String? = null
        // List of button ids mapped to emoji strings
        val emojiButtons = listOf(
            R.id.emojiHappy to "😊",
            R.id.emojiSmile to "🙂",
            R.id.emojiNeutral to "😐",
            R.id.emojiSad to "😢",
            R.id.emojiAngry to "😡",
            // Optional sleepy emoji if present in layout
            R.id.emojiSleep to "😴"
        )

        // Attach listeners to emoji buttons; if a button id isn't present, it's ignored
        emojiButtons.forEach { (id, emoji) ->
            dialogView.findViewById<Button?>(id)?.setOnClickListener {
                chosenEmoji = emoji
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.add_mood)
            .setView(dialogView)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val emoji = chosenEmoji
                if (emoji.isNullOrEmpty()) {
                    // If no emoji picked, we don't save; in a polished UX we'd keep dialog open
                    return@setPositiveButton
                }
                viewModel.addMood(requireContext(), emoji, editNote.text?.toString())
            }
            .show()
    }

    /** Shares the currently selected mood via ACTION_SEND as a plain text summary. */
    private fun shareSelectedMood() {
        val mood = selectedMood ?: return
        val notePart = mood.note?.let { "\n" + it } ?: ""
        val text = "${mood.emoji} ${mood.date} ${mood.time}${notePart}"
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        // Create chooser with a friendly title
        val chooser = Intent.createChooser(sendIntent, getString(R.string.share_mood))
        startActivity(chooser)
    }

    /** One-time chart config. */
    private fun setupChart() {
        val chart = binding.lineChart
        chart.description.isEnabled = false
        chart.setNoDataText(getString(R.string.no_mood_data))
        chart.axisRight.isEnabled = false
        chart.axisLeft.apply {
            axisMinimum = 0f
            axisMaximum = 5.5f
            granularity = 1f
        }
        chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            granularity = 1f
            setDrawGridLines(false)
            valueFormatter = object : ValueFormatter() {
                // Formats x values (0..6) as yyyy-MM-dd labels for the last 7 days
                override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                    val idx = value.toInt()
                    val dates = last7Days()
                    return dates.getOrNull(idx)?.toString()?.substring(5) ?: ""
                }
            }
        }
        chart.legend.isEnabled = false
    }

    /** Updates the chart data to show last 7 days' average mood values. */
    private fun updateChart(allMoods: List<Mood>) {
        val chart = binding.lineChart
        val days = last7Days() // oldest..newest

        // Map emoji to numeric values for the chart
        fun score(emoji: String): Int = when (emoji) {
            "😊" -> 5
            "🙂" -> 4
            "😐" -> 3
            "😢" -> 2
            "😡" -> 1
            "😴" -> 2
            else -> 3 // default neutral
        }

        // Calculates mood averages for chart data
        val entries = mutableListOf<Entry>()
        var anyData = false
        days.forEachIndexed { index, date ->
            val dayMoods = allMoods.filter { it.date == date.toString() }
            if (dayMoods.isNotEmpty()) {
                anyData = true
                val avg = dayMoods.map { score(it.emoji) }.average().toFloat()
                entries.add(Entry(index.toFloat(), avg))
            } else {
                // Still add an entry for the day so the line spans the full week
                entries.add(Entry(index.toFloat(), Float.NaN))
            }
        }

        if (!anyData) {
            chart.clear()
            chart.invalidate()
            return
        }

        val dataSet = LineDataSet(entries, "").apply {
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawCircles(true)
            circleRadius = 3f
            setDrawValues(false)
            color = requireContext().getColor(R.color.purple_500)
            setCircleColor(color)
            lineWidth = 2f
        }
        chart.data = LineData(dataSet)
        chart.invalidate()
    }

    /** Returns a list of the last 7 LocalDate values (oldest first, includes today). */
    private fun last7Days(): List<LocalDate> {
        val today = LocalDate.now()
        return (6 downTo 0).map { today.minusDays(it.toLong()) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Save transient UI state for rotation
        outState.putInt(KEY_SELECTED_POS, selectedPosition)
        outState.putParcelable(KEY_RECYCLER_STATE, binding.recyclerMoods.layoutManager?.onSaveInstanceState())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val KEY_SELECTED_POS = "mood_selected_pos"
        private const val KEY_RECYCLER_STATE = "mood_recycler_state"
    }
}
