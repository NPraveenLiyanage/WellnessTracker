package com.example.wellnesstracker

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.wellnesstracker.util.SharedPrefsHelper
import com.google.android.material.button.MaterialButton

class OnboardingActivity : AppCompatActivity() {
    private lateinit var viewPager: ViewPager2
    private lateinit var dotsLayout: LinearLayout
    private lateinit var btnSkip: MaterialButton
    private lateinit var btnNext: MaterialButton

    private val pages = listOf(
        Page(
            image = R.drawable.ic_habits_24,
            title = R.string.onboarding_title_habits,
            desc = R.string.onboarding_desc_habits
        ),
        Page(
            image = R.drawable.ic_mood_24,
            title = R.string.onboarding_title_mood,
            desc = R.string.onboarding_desc_mood
        ),
        Page(
            image = R.drawable.ic_water,
            title = R.string.onboarding_title_hydration,
            desc = R.string.onboarding_desc_hydration
        ),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_onboarding)

        viewPager = findViewById(R.id.viewPager)
        dotsLayout = findViewById(R.id.dotsLayout)
        btnSkip = findViewById(R.id.buttonSkip)
        btnNext = findViewById(R.id.buttonNext)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        viewPager.adapter = Adapter(pages)
        // Subtle fade/scale transformer for a polished feel
        viewPager.setPageTransformer { page, position ->
            val absPos = kotlin.math.abs(position)
            page.alpha = 1f - (absPos * 0.4f)
            val scale = 1f - (absPos * 0.06f)
            page.scaleX = scale
            page.scaleY = scale
        }
        setupDots(pages.size)
        setCurrentDot(0)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                setCurrentDot(position)
                updateButtons(position)
            }
        })

        btnSkip.setOnClickListener { completeAndGo() }
        btnNext.setOnClickListener {
            val pos = viewPager.currentItem
            if (pos < pages.lastIndex) {
                viewPager.currentItem = pos + 1
            } else {
                completeAndGo()
            }
        }

        updateButtons(0)
    }

    private fun setupDots(count: Int) {
        dotsLayout.removeAllViews()
        val size = resources.displayMetrics.density * 8 // 8dp
        val margin = resources.displayMetrics.density * 4 // 4dp
        repeat(count) { _ ->
            val v = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(size.toInt(), size.toInt()).also {
                    it.leftMargin = margin.toInt()
                    it.rightMargin = margin.toInt()
                }
                background = ContextCompat.getDrawable(this@OnboardingActivity, R.drawable.dot_unselected)
            }
            dotsLayout.addView(v)
        }
    }

    private fun setCurrentDot(index: Int) {
        for (i in 0 until dotsLayout.childCount) {
            val child = dotsLayout.getChildAt(i)
            child.background = ContextCompat.getDrawable(
                this,
                if (i == index) R.drawable.dot_selected else R.drawable.dot_unselected
            )
        }
    }

    private fun updateButtons(position: Int) {
        val last = position == pages.lastIndex
        btnSkip.visibility = if (last) View.INVISIBLE else View.VISIBLE
        btnNext.text = getString(if (last) R.string.get_started else R.string.next)
    }

    private fun completeAndGo() {
        SharedPrefsHelper.setOnboardingDone(this, true)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private data class Page(val image: Int, val title: Int, val desc: Int)

    private class Adapter(private val items: List<Page>) : RecyclerView.Adapter<Adapter.VH>() {
        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val image: ImageView = view.findViewById(R.id.image)
            val title: TextView = view.findViewById(R.id.title)
            val desc: TextView = view.findViewById(R.id.desc)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_onboarding_page, parent, false)
            return VH(view)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.image.setImageResource(item.image)
            holder.title.setText(item.title)
            holder.desc.setText(item.desc)
        }
    }
}
