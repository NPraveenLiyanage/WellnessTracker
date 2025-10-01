package com.example.wellnesstracker.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Parcelable
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.wellnesstracker.R
import com.example.wellnesstracker.WellnessWidget
import com.example.wellnesstracker.databinding.FragmentHabitBinding
import com.example.wellnesstracker.model.Mood
import com.example.wellnesstracker.util.SharedPrefsHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.transition.MaterialFadeThrough
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import android.util.Log

class HabitFragment : Fragment() {
    private var _binding: FragmentHabitBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HabitViewModel by viewModels()
    private lateinit var adapter: HabitAdapter

    // Save transient scroll state across rotation
    private var recyclerState: Parcelable? = null

    // Optional: accelerometer-based shake detection to auto-add a mood
    private var sensorManager: SensorManager? = null
    private var lastAccel = 0f
    private var accel = 0f
    private var accelCurrent = SensorManager.GRAVITY_EARTH
    private var lastShakeTime = 0L

    // Step counter state (TYPE_STEP_DETECTOR fallback). Transient while the fragment is active.
    private var stepCount: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Smooth fade transitions between tabs
        enterTransition = MaterialFadeThrough()
        exitTransition = MaterialFadeThrough()
        recyclerState = savedInstanceState?.getParcelable(KEY_RECYCLER_STATE)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHabitBinding.inflate(inflater, container, false)
        return binding.root
    }

    /** Loads habits for today and updates RecyclerView. */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // RecyclerView setup
        adapter = HabitAdapter(
            onChecked = { habit, checked ->
                viewModel.setCompleted(habit.id, checked)
                persistAndRefreshWidget()
            },
            onClick = { habit ->
                // Single tap: edit
                showEditHabitDialog(habit.id, habit.name)
            },
            onLongPressed = { habit ->
                // Long press: confirm deletion
                showDeleteHabitDialog(habit.id, habit.name)
            }
        )
        val layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerHabits?.layoutManager = layoutManager
        binding.recyclerHabits?.adapter = adapter

        // Restore recycler scroll position after rotation
        recyclerState?.let { state ->
            binding.recyclerHabits?.post { layoutManager.onRestoreInstanceState(state) }
        }

        // Swipe-to-delete
        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.bindingAdapterPosition
                val item = adapter.currentList.getOrNull(pos)
                if (item != null) {
                    viewModel.deleteHabit(item.id)
                    persistAndRefreshWidget()
                }
            }
        })
        // Attach only if RecyclerView exists in this layout variant
        binding.recyclerHabits?.let { rv ->
            touchHelper.attachToRecyclerView(rv)
        }

        // FAB add habit
        binding.fabAdd?.setOnClickListener { showAddHabitDialog() }

        // Observe data
        viewModel.habits.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            // Empty state handling — handle layouts that may not include the empty view
            val emptyId = resources.getIdentifier("textEmpty", "id", requireContext().packageName)
            if (emptyId != 0) binding.root.findViewById<View>(emptyId).isVisible = list.isEmpty()
        }
        viewModel.progress.observe(viewLifecycleOwner) { pct ->
            // Try multiple APIs via reflection to set progress across different Material versions
            binding.progressBar?.let { pb ->
                try {
                    val m = pb.javaClass.getMethod("setProgress", Int::class.javaPrimitiveType)
                    m.invoke(pb, pct)
                } catch (nsme: NoSuchMethodException) {
                    try {
                        val m2 = pb.javaClass.getMethod("setProgressCompat", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
                        m2.invoke(pb, pct, false)
                    } catch (e: Exception) {
                        // Last resort: try setProgress via kotlin property if exposed, else log and ignore
                        Log.w("HabitFragment", "Unable to set progress on progressBar view: ${e.message}")
                    }
                } catch (e: Exception) {
                    Log.w("HabitFragment", "Failed to set progress on progressBar: ${e.message}")
                }
            }
            val progressId = resources.getIdentifier("textProgress", "id", requireContext().packageName)
            if (progressId != 0) binding.root.findViewById<TextView>(progressId)?.text = getString(R.string.progress_percent, pct)
        }

        // Initialize step UI from stored value for today
        val todayDate = LocalDate.now().toString()
        stepCount = SharedPrefsHelper.getStepsForDate(requireContext(), todayDate)
        val stepsId = resources.getIdentifier("textSteps", "id", requireContext().packageName)
        if (stepsId != 0) binding.root.findViewById<TextView>(stepsId)?.text = getString(R.string.steps_today, stepCount)

        // Load data for today's date
        val today = LocalDate.now().toString()
        viewModel.load(requireContext(), today)
    }

    /** Shows dialog to add a new habit. */
    private fun showAddHabitDialog() {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            hint = getString(R.string.habit_name)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.add_habit)
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) {
                    val nameNotNull = name
                    val id = viewModel.addHabit(requireContext(), nameNotNull)
                    adapter.animateNextAdded(id)
                    persistAndRefreshWidget()
                } else {
                    input.error = getString(R.string.habit_name_required)
                }
            }
            .show()
    }

    /** Shows dialog to edit an existing habit. */
    private fun showEditHabitDialog(id: Int, currentName: String) {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setText(currentName)
            setSelection(currentName.length)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.edit_habit)
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val newName = input.text?.toString()?.trim().orEmpty()
                if (newName.isNotEmpty()) {
                    viewModel.editHabit(id, newName)
                    persistAndRefreshWidget()
                } else {
                    input.error = getString(R.string.habit_name_required)
                }
            }
            .show()
    }

    /** Shows dialog to confirm deletion of a habit. */
    private fun showDeleteHabitDialog(id: Int, name: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(name)
            .setMessage(getString(R.string.confirm_delete_habit))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deleteHabit(id)
                persistAndRefreshWidget()
            }
            .show()
    }

    /** Persists changes when leaving the screen. */
    override fun onPause() {
        super.onPause()
        viewModel.save(requireContext())
        unregisterShake()
    }

    override fun onResume() {
        super.onResume()
        registerShake()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Save recycler state only when present in the current layout
        binding.recyclerHabits?.layoutManager?.onSaveInstanceState()?.let { outState.putParcelable(KEY_RECYCLER_STATE, it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun persistAndRefreshWidget() {
        // Save immediately so the widget can reflect the latest state
        viewModel.save(requireContext())
        // Update widget to show today's progress
        WellnessWidget.updateAll(requireContext())
    }

    // --- Optional: Shake detection to auto-add a neutral mood entry ---

    private fun registerShake() {
        val ctx = context ?: return
        sensorManager = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        accel = 0.0f
        accelCurrent = SensorManager.GRAVITY_EARTH
        lastAccel = SensorManager.GRAVITY_EARTH
        sensorManager?.registerListener(shakeListener, accelerometer, SensorManager.SENSOR_DELAY_UI)

        // Try to register a step detector to count steps in this fragment while visible.
        val stepSensor = try {
            sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        } catch (e: SecurityException) {
            null
        }
        if (stepSensor != null) {
            // Load current stored steps for today instead of resetting
            val today = LocalDate.now().toString()
            stepCount = SharedPrefsHelper.getStepsForDate(ctx, today)
            val sid = resources.getIdentifier("textSteps", "id", ctx.packageName)
            if (sid != 0) binding.root.findViewById<TextView>(sid)?.text = getString(R.string.steps_today, stepCount)
            try {
                sensorManager?.registerListener(stepListener, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)
            } catch (_: SecurityException) {
                // Ignore if permission isn't granted on newer Android; step sensor then won't be available.
            }
        }
    }

    private fun unregisterShake() {
        sensorManager?.unregisterListener(shakeListener)
        sensorManager?.unregisterListener(stepListener)
    }

    private val shakeListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            lastAccel = accelCurrent
            accelCurrent = kotlin.math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            val delta = accelCurrent - lastAccel
            accel = accel * 0.9f + delta // low-cut filter

            val now = System.currentTimeMillis()
            if (accel > 8f && now - lastShakeTime > 2000) { // threshold and 2s debounce
                lastShakeTime = now
                addAutoMood()
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    // Step detector listener: increments a transient step counter and updates UI
    private val stepListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            // TYPE_STEP_DETECTOR typically reports 1.0 for each step (or a small count)
            val delta = event.values?.sumOf { it.toDouble() }?.toInt() ?: 0
            if (delta > 0) {
                val ctx = context ?: return
                val today = LocalDate.now().toString()
                // Persist and get new total
                stepCount = SharedPrefsHelper.addStepsForDate(ctx, today, delta)
                activity?.runOnUiThread {
                    val sid = resources.getIdentifier("textSteps", "id", ctx.packageName)
                    if (sid != 0) binding.root.findViewById<TextView>(sid)?.text = getString(R.string.steps_today, stepCount)
                }
                // Refresh widget to reflect updated steps
                WellnessWidget.updateAll(ctx)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private fun addAutoMood() {
        val ctx = context ?: return
        val list = SharedPrefsHelper.loadAllMoods(ctx)
        val now = LocalDateTime.now()
        val newMood = Mood(
            date = now.toLocalDate().toString(),
            time = now.format(DateTimeFormatter.ofPattern("HH:mm")),
            emoji = "\uD83D\uDE42",
            note = getString(R.string.added_by_shake)
        )
        list.add(0, newMood)
        SharedPrefsHelper.saveAllMoods(ctx, list)
    }

    companion object {
        private const val KEY_RECYCLER_STATE = "habit_recycler_state"
    }
}
