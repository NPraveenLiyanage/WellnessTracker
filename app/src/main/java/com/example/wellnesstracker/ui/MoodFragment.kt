package com.example.wellnesstracker.ui

import android.app.Dialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.core.view.isEmpty
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.wellnesstracker.R
import com.example.wellnesstracker.databinding.FragmentMoodBinding
import com.example.wellnesstracker.model.Mood
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
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
            recyclerState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                it.getParcelable(KEY_RECYCLER_STATE, Parcelable::class.java)
            } else {
                @Suppress("DEPRECATION")
                it.getParcelable(KEY_RECYCLER_STATE)
            }
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

        try {
            // RecyclerView setup
            adapter = MoodAdapter(
                { mood, position ->
                    // Open edit form when item clicked
                    selectedMood = mood
                    selectedPosition = position
                    adapter.setSelected(position)
                    binding.buttonShare?.isEnabled = true
                    showEditMoodDialog(mood, position)
                },
                { mood, position ->
                    // Long-press -> confirm delete
                    confirmDeleteMood(mood, position)
                }
            )

            // Use whichever RecyclerView exists in this layout (land: recyclerMoods, port: rv_mood_history)
            val recycler: RecyclerView? = binding.recyclerMoods ?: binding.rvMoodHistory
            val layoutManager = LinearLayoutManager(requireContext())
            recycler?.layoutManager = layoutManager
            recycler?.adapter = adapter

            // Restore scroll position if available
            recyclerState?.let { state ->
                recycler?.post { layoutManager.onRestoreInstanceState(state) }
            }

            // Add mood FAB (handle both IDs across layout variants)
            binding.root.findViewById<FloatingActionButton>(R.id.fab_add_mood)?.setOnClickListener { showAddMoodDialog() }
            binding.root.findViewById<FloatingActionButton>(R.id.fabAddMood)?.setOnClickListener { showAddMoodDialog() }

            // Handles sharing mood summary via implicit intent (landscape only)
            binding.root.findViewById<MaterialButton>(R.id.buttonShare)?.setOnClickListener { shareSelectedMood() }

            // Configure chart base styling once
            setupChart()

            // Observe moods
            viewModel.moods.observe(viewLifecycleOwner) { list ->
                adapter.submitList(list)
                // Disable share when selection becomes invalid (e.g., list changed)
                if (selectedPosition !in list.indices || (selectedMood != null && list.getOrNull(selectedPosition) != selectedMood)) {
                    selectedMood = null
                    selectedPosition = -1
                    binding.buttonShare?.isEnabled = false
                }
                // If we restored a pending selection, apply it once data arrives
                pendingSelectPosition?.let { p ->
                    if (p in list.indices) {
                        selectedPosition = p
                        selectedMood = list[p]
                        adapter.setSelected(p)
                        binding.buttonShare?.isEnabled = true
                    }
                    pendingSelectPosition = null
                }
                // Update chart data whenever the list changes
                updateChart(list)
                // Empty state handling for list
                recycler?.isVisible = list.isNotEmpty()
            }

            // Load persisted data
            viewModel.load(requireContext())
        } catch (e: Exception) {
            Log.e(TAG, "Error in onViewCreated", e)
            // Show a simple dialog to notify the user instead of crashing
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.app_name)
                .setMessage(e.message ?: "Unexpected error during UI setup")
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    /** Shows dialog to pick an emoji and optionally enter a note. */
    private fun showAddMoodDialog() {
        // Inflate dialog view (attachToRoot = false)
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_mood, binding.root as ViewGroup, false)
        val editNote = dialogView.findViewById<EditText>(R.id.et_mood_note)
        val emojiRowsContainer = dialogView.findViewById<android.widget.LinearLayout>(R.id.emoji_rows_container)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btn_cancel)
        val btnSave = dialogView.findViewById<MaterialButton>(R.id.btn_save)

        val emojis = listOf("😊", "🙂", "😐", "😢", "😡", "😠", "😴", "😰")

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()

        dialog.show()

        // Dialog width and measurements
        val screenWidth = resources.displayMetrics.widthPixels
        val dialogWidth = (screenWidth * 0.95).toInt()
        dialog.window?.setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT)

        val marginPx = (2 * resources.displayMetrics.density).toInt()
        val defaultSize = resources.getDimensionPixelSize(R.dimen.mood_item_size)
        val emojiTextSizePx = resources.getDimension(R.dimen.mood_emoji_size)

        // Compute available width using padding from layout
        val innerPadding = resources.getDimensionPixelSize(R.dimen.padding_lg)
        val availableWidth = (dialogWidth - innerPadding * 2).coerceAtLeast(0)

        val n = emojis.size
        val desiredTwoRowCols = (n + 1) / 2
        val approxCols = (availableWidth / (defaultSize + marginPx * 2)).coerceAtLeast(1)
        val columns = if (approxCols >= desiredTwoRowCols) desiredTwoRowCols.coerceAtMost(n) else approxCols.coerceAtMost(n)

        val totalMarginSpace = columns * marginPx * 2
        var sizePx = ((availableWidth - totalMarginSpace) / columns).coerceAtLeast((defaultSize / 3)).coerceAtMost(defaultSize)
        val minFromText = (emojiTextSizePx * 1.8f).toInt().coerceAtLeast((defaultSize * 0.35f).toInt())
        if (sizePx < minFromText) sizePx = minFromText

        emojiRowsContainer?.removeAllViews()
        val row1 = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val row2 = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val firstRowCount = columns.coerceAtMost(n)
        val secondRowStart = firstRowCount

        var chosenEmoji: String? = null
        var selectedView: View? = null

        fun makeEmojiView(emoji: String): TextView {
            val tv = TextView(requireContext()).apply {
                text = emoji
                contentDescription = "Mood: $emoji"
                gravity = android.view.Gravity.CENTER
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                includeFontPadding = true
                val horiz = (2 * resources.displayMetrics.density).toInt()
                val vert = (6 * resources.displayMetrics.density).toInt()
                setPadding(horiz, vert, horiz, vert)
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, emojiTextSizePx)
                setBackgroundResource(R.drawable.mood_item_background)
                isClickable = true
                isFocusable = true
            }
            val lp = android.widget.LinearLayout.LayoutParams(sizePx, sizePx).apply {
                setMargins(marginPx, marginPx, marginPx, marginPx)
            }
            tv.layoutParams = lp
            tv.minWidth = sizePx
            tv.minHeight = sizePx

            tv.setOnClickListener { v ->
                selectedView?.scaleX = 1f
                selectedView?.scaleY = 1f
                v.scaleX = 1.12f
                v.scaleY = 1.12f
                selectedView = v
                chosenEmoji = emoji
            }
            return tv
        }

        for (i in 0 until firstRowCount) {
            val tv = makeEmojiView(emojis[i])
            row1.addView(tv)
        }
        if (secondRowStart < n) {
            for (i in secondRowStart until n) {
                val tv = makeEmojiView(emojis[i])
                row2.addView(tv)
            }
        }

        if (row2.isEmpty()) {
            emojiRowsContainer?.addView(row1)
        } else {
            emojiRowsContainer?.addView(row1)
            emojiRowsContainer?.addView(row2)
        }

        Log.d(TAG, "emoji rows: row1=${row1.childCount} row2=${row2.childCount} tileSize=$sizePx")

        btnCancel?.setOnClickListener { dialog.dismiss() }
        btnSave?.setOnClickListener {
            val emoji = chosenEmoji
            if (emoji.isNullOrEmpty()) return@setOnClickListener
            viewModel.addMood(requireContext(), emoji, editNote?.text?.toString())
            dialog.dismiss()
        }
    }

    /** Shows an edit dialog pre-filled for the given mood at position. */
    private fun showEditMoodDialog(mood: Mood, position: Int) {
        // Inflate edit dialog view (attachToRoot = false)
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_mood, binding.root as ViewGroup, false)
        val editNote = dialogView.findViewById<EditText>(R.id.et_mood_note)
        val emojiRowsContainer = dialogView.findViewById<android.widget.LinearLayout>(R.id.emoji_rows_container)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btn_cancel)
        val btnSave = dialogView.findViewById<MaterialButton>(R.id.btn_save)

        val emojis = listOf("😊", "🙂", "😐", "😢", "😡", "😠", "😴", "😰")

        // Prefill note
        editNote?.setText(mood.note)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()

        dialog.show()

        val screenWidth = resources.displayMetrics.widthPixels
        val dialogWidth = (screenWidth * 0.95).toInt()
        dialog.window?.setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT)

        val marginPx = (2 * resources.displayMetrics.density).toInt()
        val defaultSize = resources.getDimensionPixelSize(R.dimen.mood_item_size)
        val emojiTextSizePx = resources.getDimension(R.dimen.mood_emoji_size)
        val innerPadding = resources.getDimensionPixelSize(R.dimen.padding_lg)
        val availableWidth = (dialogWidth - innerPadding * 2).coerceAtLeast(0)

        val n = emojis.size
        val desiredTwoRowCols = (n + 1) / 2
        val approxCols = (availableWidth / (defaultSize + marginPx * 2)).coerceAtLeast(1)
        val columns = if (approxCols >= desiredTwoRowCols) desiredTwoRowCols.coerceAtMost(n) else approxCols.coerceAtMost(n)

        val totalMarginSpace = columns * marginPx * 2
        var sizePx = ((availableWidth - totalMarginSpace) / columns).coerceAtLeast((defaultSize / 3)).coerceAtMost(defaultSize)
        val minFromText = (emojiTextSizePx * 1.8f).toInt().coerceAtLeast((defaultSize * 0.35f).toInt())
        if (sizePx < minFromText) sizePx = minFromText

        emojiRowsContainer?.removeAllViews()
        val row1 = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val row2 = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val firstRowCount = columns.coerceAtMost(n)
        val secondRowStart = firstRowCount

        var chosenEmoji: String? = mood.emoji
        var selectedView: View? = null

        fun makeEmojiView(emoji: String): TextView {
            val tv = TextView(requireContext()).apply {
                text = emoji
                contentDescription = "Mood: $emoji"
                gravity = android.view.Gravity.CENTER
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                includeFontPadding = true
                val horiz = (2 * resources.displayMetrics.density).toInt()
                val vert = (6 * resources.displayMetrics.density).toInt()
                setPadding(horiz, vert, horiz, vert)
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, emojiTextSizePx)
                setBackgroundResource(R.drawable.mood_item_background)
                isClickable = true
                isFocusable = true
            }
            val lp = android.widget.LinearLayout.LayoutParams(sizePx, sizePx).apply {
                setMargins(marginPx, marginPx, marginPx, marginPx)
            }
            tv.layoutParams = lp
            tv.minWidth = sizePx
            tv.minHeight = sizePx

            // If this is the mood's current emoji, mark it selected
            if (emoji == mood.emoji) {
                tv.scaleX = 1.12f
                tv.scaleY = 1.12f
                selectedView = tv
            }

            tv.setOnClickListener { v ->
                selectedView?.scaleX = 1f
                selectedView?.scaleY = 1f
                v.scaleX = 1.12f
                v.scaleY = 1.12f
                selectedView = v
                chosenEmoji = emoji
            }
            return tv
        }

        for (i in 0 until firstRowCount) {
            val tv = makeEmojiView(emojis[i])
            row1.addView(tv)
        }
        if (secondRowStart < n) {
            for (i in secondRowStart until n) {
                val tv = makeEmojiView(emojis[i])
                row2.addView(tv)
            }
        }

        if (row2.isEmpty()) {
            emojiRowsContainer?.addView(row1)
        } else {
            emojiRowsContainer?.addView(row1)
            emojiRowsContainer?.addView(row2)
        }

        btnCancel?.setOnClickListener { dialog.dismiss() }
        btnSave?.setOnClickListener {
            val emoji = chosenEmoji
            if (emoji.isNullOrEmpty()) return@setOnClickListener
            viewModel.updateMood(requireContext(), position, emoji, editNote?.text?.toString())
            dialog.dismiss()
        }
    }

    private fun confirmDeleteMood(mood: Mood, position: Int) {
        // Inflate custom delete dialog layout
        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_habit, binding.root as ViewGroup, false)

        // Use a plain Dialog and set the custom view as its content. Use transparent background so
        // only the card is visible (no AlertDialog chrome).
        val dialog = Dialog(requireContext())
        dialog.setContentView(dialogView)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()

        // Populate message with mood info
        val msgView = dialogView.findViewById<TextView>(R.id.dialogDeleteMessage)
        msgView?.text = getString(R.string.delete_mood_confirm_with_info, mood.emoji, mood.date, mood.time)

        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btn_dialog_cancel)
        val btnDelete = dialogView.findViewById<MaterialButton>(R.id.btn_dialog_delete)

        btnCancel?.setOnClickListener { dialog.dismiss() }
        btnDelete?.setOnClickListener {
            // perform deletion and persist
            viewModel.deleteMood(requireContext(), position)
            // clear selection if it was the deleted one
            if (position == selectedPosition) {
                selectedPosition = -1
                selectedMood = null
                binding.buttonShare?.isEnabled = false
            }
            dialog.dismiss()
        }
    }

    private fun shareSelectedMood() {
        val mood = selectedMood ?: return
        val notePart = mood.note?.let { "\n" + it } ?: ""
        val text = "${mood.emoji} ${mood.date} ${mood.time}${notePart}"
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(sendIntent, getString(R.string.share_mood))
        startActivity(chooser)
    }

    private fun setupChart() {
        binding.lineChart?.apply {
             description.isEnabled = false
             setNoDataText(getString(R.string.no_mood_data))
             axisRight.isEnabled = false
             axisLeft.apply {
                 axisMinimum = 0f
                 axisMaximum = 5.5f
                 granularity = 1f
             }
             xAxis.apply {
                 position = XAxis.XAxisPosition.BOTTOM
                 granularity = 1f
                 setDrawGridLines(false)
                 valueFormatter = object : ValueFormatter() {
                     override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                         val idx = value.toInt()
                         val dates = last7Days()
                         return dates.getOrNull(idx)?.toString()?.substring(5) ?: ""
                     }
                 }
             }
             legend.isEnabled = false
         }
     }

     private fun updateChart(allMoods: List<Mood>) {
        binding.lineChart?.let { chart ->
             val days = last7Days()

             fun score(emoji: String): Int = when (emoji) {
                 "😊" -> 5
                 "🙂" -> 4
                 "😐" -> 3
                 "😢" -> 2
                 "😡", "😠" -> 1
                 "😴" -> 2
                 "😰" -> 2
                 else -> 3
             }

             val entries = mutableListOf<Entry>()
             var anyData = false
             days.forEachIndexed { index, date ->
                 val dayMoods = allMoods.filter { it.date == date.toString() }
                 if (dayMoods.isNotEmpty()) {
                     anyData = true
                     val avg = dayMoods.map { score(it.emoji) }.average().toFloat()
                     entries.add(Entry(index.toFloat(), avg))
                 } else {
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
                 color = requireContext().getColor(R.color.primary_500)
                 setCircleColor(color)
                 lineWidth = 2f
             }
             chart.data = LineData(dataSet)
             chart.invalidate()
         }
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
        val recycler: RecyclerView? = binding.recyclerMoods ?: binding.rvMoodHistory
        val state = recycler?.layoutManager?.onSaveInstanceState()
        outState.putParcelable(KEY_RECYCLER_STATE, state)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "MoodFragment"
        private const val KEY_SELECTED_POS = "mood_selected_pos"
        private const val KEY_RECYCLER_STATE = "mood_recycler_state"
    }
}
