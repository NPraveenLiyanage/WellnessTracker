package com.example.wellnesstracker.ui

import android.app.DatePickerDialog
import android.graphics.drawable.ColorDrawable
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
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
import com.example.wellnesstracker.model.Habit
import com.example.wellnesstracker.util.SharedPrefsHelper
import android.app.Dialog
import android.view.WindowManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import java.time.LocalDate

class HabitFragment : Fragment() {
    private var _binding: FragmentHabitBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HabitViewModel by viewModels()
    private lateinit var adapter: HabitAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHabitBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = HabitAdapter(
            onChecked = { habit, checked ->
                viewModel.setCompleted(requireContext(), habit.id, checked)
                persistAndRefreshWidget()
            },
            onClick = { habit -> showEditHabitDialog(habit.id, habit.name, habit.date) },
            onLongPressed = { habit -> showDeleteHabitDialog(habit.id, habit.name) }
        )

        binding.recyclerHabits.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerHabits.adapter = adapter

        val swipe = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = false
            override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {
                val pos = vh.bindingAdapterPosition
                val item = adapter.currentList.getOrNull(pos)
                if (item != null) {
                    viewModel.deleteHabit(requireContext(), item.id)
                    persistAndRefreshWidget()
                }
            }

            override fun isLongPressDragEnabled(): Boolean = false
        }
        ItemTouchHelper(swipe).attachToRecyclerView(binding.recyclerHabits)

        binding.fabAdd.apply {
            bringToFront()
            translationZ = 20f
            elevation = 20f
            isClickable = true
            isFocusable = true
        }

        binding.fabAdd.setOnClickListener { showAddHabitDialog() }
        binding.btnViewAllHabits?.setOnClickListener { showAllHabitsDialog() }

        viewModel.habits.observe(viewLifecycleOwner) { list ->
            // Show all habits in the main list (user requested all, not only today's)
            adapter.submitList(list)
            binding.textEmpty?.isVisible = list.isEmpty()
            updateProgressUI(list)

            val total = list.size
            val done = list.count { it.completed }
            val pct = if (total == 0) 0 else ((done * 100f) / total).toInt()
            binding.textProgress.text = getString(R.string.progress_percent, pct)
            binding.root.findViewById<LinearProgressIndicator>(R.id.progressBar)?.setProgressCompat(pct, true)
        }

        viewModel.weekly.observe(viewLifecycleOwner) { values ->
            updateWeeklyChart(values)
        }

        viewModel.load(requireContext(), LocalDate.now().toString())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun showAddHabitDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_habit, null)
        val input = dialogView.findViewById<EditText>(R.id.edit_habit_input)
        val tvDate = dialogView.findViewById<TextView>(R.id.tv_habit_date)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btn_dialog_cancel)
        val btnSave = dialogView.findViewById<MaterialButton>(R.id.btn_dialog_save)

        var selectedDate = LocalDate.now().toString()
        tvDate.text = selectedDate

        tvDate.setOnClickListener {
            val base = try { LocalDate.parse(selectedDate) } catch (_: Exception) { LocalDate.now() }
            val year = base.year
            val month = base.monthValue - 1
            val day = base.dayOfMonth
            val dp = DatePickerDialog(requireContext(), { _, y, m, d ->
                val picked = LocalDate.of(y, m + 1, d)
                selectedDate = picked.toString()
                tvDate.text = selectedDate
            }, year, month, day)
            dp.show()
        }

        val dialog = Dialog(requireContext())
        dialog.setContentView(dialogView)
        dialog.setCancelable(true)
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setDimAmount(0f)
        dialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        val dlgWidth = (resources.displayMetrics.widthPixels * 0.92).toInt()
        dialog.window?.setLayout(dlgWidth, WindowManager.LayoutParams.WRAP_CONTENT)

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnSave.setOnClickListener {
            val name = input.text?.toString()?.trim().orEmpty()
            if (name.isNotEmpty()) {
                val id = viewModel.addHabit(requireContext(), name, selectedDate)
                if (id > 0) adapter.animateNextAdded(id)
                persistAndRefreshWidget()
                Snackbar.make(binding.root, R.string.add_habit, Snackbar.LENGTH_SHORT).show()
                dialog.dismiss()
            } else {
                input.error = getString(R.string.habit_name_required)
            }
        }
    }

    private fun showEditHabitDialog(id: Int, currentName: String, currentDate: String, onSaved: (() -> Unit)? = null) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_habit, null)
        val input = dialogView.findViewById<EditText>(R.id.edit_habit_input)
        val tvDate = dialogView.findViewById<TextView>(R.id.tv_habit_date)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btn_dialog_cancel)
        val btnSave = dialogView.findViewById<MaterialButton>(R.id.btn_dialog_save)
        input.setText(currentName)
        input.setSelection(currentName.length)

        var selectedDate = currentDate
        tvDate.text = selectedDate
        tvDate.setOnClickListener {
            val base = try { LocalDate.parse(selectedDate) } catch (_: Exception) { LocalDate.now() }
            val year = base.year
            val month = base.monthValue - 1
            val day = base.dayOfMonth
            val dp = DatePickerDialog(requireContext(), { _, y, m, d ->
                val picked = LocalDate.of(y, m + 1, d)
                selectedDate = picked.toString()
                tvDate.text = selectedDate
            }, year, month, day)
            dp.show()
        }

        val dialog = Dialog(requireContext())
        dialog.setContentView(dialogView)
        dialog.setCancelable(true)
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setDimAmount(0f)
        dialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        val dlgWidth2 = (resources.displayMetrics.widthPixels * 0.92).toInt()
        dialog.window?.setLayout(dlgWidth2, WindowManager.LayoutParams.WRAP_CONTENT)

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnSave.setOnClickListener {
            val newName = input.text?.toString()?.trim().orEmpty()
            if (newName.isNotEmpty()) {
                // Persist name and date (move if date changed)
                viewModel.updateHabit(requireContext(), id, newName, selectedDate)
                persistAndRefreshWidget()
                Snackbar.make(binding.root, R.string.edit_habit, Snackbar.LENGTH_SHORT).show()
                dialog.dismiss()
                onSaved?.invoke()
            } else {
                input.error = getString(R.string.habit_name_required)
            }
        }
    }

    private fun showDeleteHabitDialog(id: Int, name: String, onDeleted: (() -> Unit)? = null) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_habit, null)
        val titleView = dialogView.findViewById<TextView>(R.id.dialogDeleteTitle)
        val msgView = dialogView.findViewById<TextView>(R.id.dialogDeleteMessage)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btn_dialog_cancel)
        val btnDelete = dialogView.findViewById<MaterialButton>(R.id.btn_dialog_delete)
        titleView.text = name
        msgView.text = getString(R.string.confirm_delete_habit)

        val dialog = Dialog(requireContext())
        dialog.setContentView(dialogView)
        dialog.setCancelable(true)
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setDimAmount(0f)
        dialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        val dlgWidth3 = (resources.displayMetrics.widthPixels * 0.92).toInt()
        dialog.window?.setLayout(dlgWidth3, WindowManager.LayoutParams.WRAP_CONTENT)

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnDelete.setOnClickListener {
            viewModel.deleteHabit(requireContext(), id)
            persistAndRefreshWidget()
            Snackbar.make(binding.root, R.string.habit_deleted, Snackbar.LENGTH_LONG).show()
            dialog.dismiss()
            onDeleted?.invoke()
        }
    }

    private fun showAllHabitsDialog() {
        val ctx = requireContext()
        val dialogView = layoutInflater.inflate(R.layout.dialog_all_habits, null)
        val rv = dialogView.findViewById<RecyclerView>(R.id.recyclerAllHabits)
        rv.layoutManager = LinearLayoutManager(ctx)

        var all = SharedPrefsHelper.loadAllHabits(ctx)
        lateinit var allAdapter: AllHabitsAdapter
        allAdapter = AllHabitsAdapter(
            onToggle = { habit, checked ->
                SharedPrefsHelper.setHabitCompleted(ctx, habit.date, habit.id, checked)
                all = SharedPrefsHelper.loadAllHabits(ctx)
                allAdapter.submitList(all.toList())
                viewModel.load(ctx, LocalDate.now().toString())
                WellnessWidget.updateAll(ctx)
            },
            onEdit = { habit ->
                // Use our custom edit dialog (with date picker)
                showEditHabitDialog(habit.id, habit.name, habit.date) {
                    all = SharedPrefsHelper.loadAllHabits(ctx)
                    allAdapter.submitList(all.toList())
                    viewModel.load(ctx, LocalDate.now().toString())
                    WellnessWidget.updateAll(ctx)
                }
            },
            onDelete = { habit ->
                // Use our custom delete dialog
                showDeleteHabitDialog(habit.id, habit.name) {
                    all = SharedPrefsHelper.loadAllHabits(ctx)
                    allAdapter.submitList(all.toList())
                    viewModel.load(ctx, LocalDate.now().toString())
                    WellnessWidget.updateAll(ctx)
                }
            }
        )
        rv.adapter = allAdapter
        allAdapter.submitList(all.toList())

        val allDialog = Dialog(ctx)
        allDialog.setContentView(dialogView)
        allDialog.setCancelable(true)
        allDialog.show()
        allDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        allDialog.window?.setDimAmount(0f)
        allDialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        val allWidth = (resources.displayMetrics.widthPixels * 0.94).toInt()
        val allHeight = (resources.displayMetrics.heightPixels * 0.78).toInt()
        allDialog.window?.setLayout(allWidth, allHeight)
    }

    private fun updateProgressUI(list: List<Habit>) {
        val total = list.size
        val done = list.count { it.completed }
        binding.chipSummary?.text = getString(R.string.habits_done_progress, done, total)
    }

    private fun updateWeeklyChart(values: List<Int>) {
        if (values.size != 7) return
        val root = binding.root
        val container = root.findViewById<View>(R.id.weeklyChartContainer) ?: return

        // Map values (oldest..newest) into fixed Mon..Sun slots using weeklyDates
        val weekdayPercents = IntArray(7) { 0 } // 0=Mon .. 6=Sun
        val dates = viewModel.weeklyDates.value
        if (dates != null && dates.size == 7) {
            for (i in values.indices) {
                try {
                    val d = LocalDate.parse(dates[i])
                    val idx = (d.dayOfWeek.value - 1).coerceIn(0, 6)
                    weekdayPercents[idx] = values[i].coerceIn(0, 100)
                } catch (_: Exception) {
                    // ignore parse error and skip
                }
            }
        } else {
            // fallback: compute exact dates for oldest..newest and map
            val base = LocalDate.now()
            for (i in values.indices) {
                val offset = 6 - i
                val d = base.minusDays(offset.toLong())
                val idx = (d.dayOfWeek.value - 1).coerceIn(0, 6)
                weekdayPercents[idx] = values[i].coerceIn(0, 100)
            }
        }

        // Update fixed Mon..Sun labels and bars
        val shortDays = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val labelIds = listOf(
            R.id.label_mon,
            R.id.label_tue,
            R.id.label_wed,
            R.id.label_thu,
            R.id.label_fri,
            R.id.label_sat,
            R.id.label_sun
        )

        for (i in 0..6) {
            val tv = root.findViewById<TextView>(labelIds[i])
            tv?.let {
                it.text = "${shortDays[i]}\n${weekdayPercents[i]}%"
                it.maxLines = 2
                it.textAlignment = View.TEXT_ALIGNMENT_CENTER
            }
        }

        // Ensure measurements are available; run after layout
        container.post {
            fun setBarHeight(viewId: Int, percent: Int) {
                val bar = root.findViewById<View>(viewId) ?: return
                val parentFrame = bar.parent
                val maxHeight = if (parentFrame is View) parentFrame.height else container.height
                val minPx = (6 * resources.displayMetrics.density).toInt()
                val px = ((percent / 100f) * maxHeight).toInt().coerceAtLeast(minPx)
                val lp = bar.layoutParams
                lp.height = px
                bar.layoutParams = lp
                bar.requestLayout()
            }

            setBarHeight(R.id.bar_mon, weekdayPercents[0])
            setBarHeight(R.id.bar_tue, weekdayPercents[1])
            setBarHeight(R.id.bar_wed, weekdayPercents[2])
            setBarHeight(R.id.bar_thu, weekdayPercents[3])
            setBarHeight(R.id.bar_fri, weekdayPercents[4])
            setBarHeight(R.id.bar_sat, weekdayPercents[5])
            setBarHeight(R.id.bar_sun, weekdayPercents[6])
        }
    }

    private fun persistAndRefreshWidget() {
        viewModel.save(requireContext())
        viewModel.recalcWeekly(requireContext())
        WellnessWidget.updateAll(requireContext())
    }
}
