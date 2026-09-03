package com.fasterscale.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.fasterscale.app.databinding.FragmentIntrospectionBinding
import kotlin.math.atan2

class IntrospectionFragment : Fragment() {

    private var _binding: FragmentIntrospectionBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentIntrospectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupFeelingsWheel()
        setupBehaviorsWheel()
    }

    private fun setupFeelingsWheel() {
        val feelingsWheel = SemiCircleWheelView(requireContext()).apply {
            val categories = listOf(
                WheelItem("Joyful", Color.parseColor("#FFEB3B"), listOf("Happy", "Cheerful", "Proud")),
                WheelItem("Peaceful", Color.parseColor("#4CAF50"), listOf("Content", "Relaxed", "Serene")),
                WheelItem("Powerful", Color.parseColor("#FF9800"), listOf("Confident", "Strong", "Valued")),
                WheelItem("Sad", Color.parseColor("#2196F3"), listOf("Lonely", "Hurt", "Depressed")),
                WheelItem("Mad", Color.parseColor("#F44336"), listOf("Angry", "Frustrated", "Hateful")),
                WheelItem("Scared", Color.parseColor("#9C27B0"), listOf("Anxious", "Insecure", "Helpless"))
            )
            setItems(categories)
            onItemSelected = { item, subItem ->
                binding.selectedDetailsTitle.text = "Feeling: ${subItem ?: item.label}"
                binding.selectedDetailsContent.text = if (subItem != null) {
                    "You are feeling ${subItem.lowercase()} within the ${item.label} category."
                } else {
                    "Explore the ${item.label} category to find a more specific emotion."
                }
            }
        }
        binding.feelingsWheelContainer.addView(feelingsWheel)
    }

    private fun setupBehaviorsWheel() {
        val behaviorsWheel = SemiCircleWheelView(requireContext()).apply {
            val categories = listOf(
                WheelItem("Restoration", Color.parseColor("#1B5E20"), listOf("Honest", "Accountable", "Grateful")),
                WheelItem("Forgetting", Color.parseColor("#827717"), listOf("Denial", "Isolating", "Bored")),
                WheelItem("Anxiety", Color.parseColor("#F9A825"), listOf("Worry", "Fearful", "Stressed")),
                WheelItem("Speeding", Color.parseColor("#E65100"), listOf("Busy", "Driven", "Racing")),
                WheelItem("Ticked Off", Color.parseColor("#BF360C"), listOf("Angry", "Resentful", "Blaming")),
                WheelItem("Exhausted", Color.parseColor("#B71C1C"), listOf("Numb", "Overwhelmed", "Depressed")),
                WheelItem("Relapse", Color.parseColor("#4A0000"), listOf("Secretive", "Shameful", "Giving up"))
            )
            setItems(categories)
            onItemSelected = { item, subItem ->
                binding.selectedDetailsTitle.text = "Behavior: ${subItem ?: item.label}"
                binding.selectedDetailsContent.text = if (subItem != null) {
                    "This behavior is part of the ${item.label} stage of the FASTER scale."
                } else {
                    "Select a specific behavior in ${item.label}."
                }
            }
        }
        binding.behaviorsWheelContainer.addView(behaviorsWheel)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

data class WheelItem(
    val label: String,
    val color: Int,
    val subItems: List<String> = emptyList()
)

class SemiCircleWheelView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textAlign = Paint.Align.CENTER
        textSize = 40f
    }
    private val rect = RectF()
    private var items = listOf<WheelItem>()
    private var selectedIndex = -1
    private var drillingIn = false
    
    var onItemSelected: ((WheelItem, String?) -> Unit)? = null

    fun setItems(newItems: List<WheelItem>) {
        items = newItems
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (items.isEmpty()) return

        val width = width.toFloat()
        val height = height.toFloat()
        val radius = width.coerceAtMost(height * 2) / 2 * 0.9f
        rect.set(width / 2 - radius, height - radius, width / 2 + radius, height + radius)

        val sweepAngle = 180f / items.size
        
        if (!drillingIn) {
            items.forEachIndexed { index, item ->
                paint.color = item.color
                paint.alpha = if (selectedIndex == index) 255 else 180
                canvas.drawArc(rect, 180f + index * sweepAngle, sweepAngle, true, paint)
                
                // Draw Text
                val angle = 180f + index * sweepAngle + sweepAngle / 2
                val textRadius = radius * 0.7f
                val x = (width / 2 + textRadius * kotlin.math.cos(Math.toRadians(angle.toDouble()))).toFloat()
                val y = (height + textRadius * kotlin.math.sin(Math.toRadians(angle.toDouble()))).toFloat()
                
                canvas.save()
                canvas.rotate(angle + 90, x, y)
                canvas.drawText(item.label, x, y, textPaint)
                canvas.restore()
            }
        } else if (selectedIndex != -1) {
            val item = items[selectedIndex]
            val subItems = item.subItems
            val subSweep = 180f / subItems.size
            
            subItems.forEachIndexed { index, subLabel ->
                paint.color = item.color
                paint.alpha = 255 - (index * 20)
                canvas.drawArc(rect, 180f + index * subSweep, subSweep, true, paint)

                val angle = 180f + index * subSweep + subSweep / 2
                val textRadius = radius * 0.7f
                val x = (width / 2 + textRadius * kotlin.math.cos(Math.toRadians(angle.toDouble()))).toFloat()
                val y = (height + textRadius * kotlin.math.sin(Math.toRadians(angle.toDouble()))).toFloat()

                canvas.save()
                canvas.rotate(angle + 90, x, y)
                canvas.drawText(subLabel, x, y, textPaint)
                canvas.restore()
            }
            
            // Draw a "back" button in the center
            paint.color = Color.LTGRAY
            canvas.drawCircle(width / 2, height, radius * 0.3f, paint)
            canvas.drawText("Back", width / 2, height - 10, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val x = event.x - width / 2
            val y = event.y - height
            val dist = kotlin.math.sqrt(x * x + y * y)
            val radius = width.coerceAtMost(height * 2) / 2 * 0.9f

            if (dist <= radius) {
                if (drillingIn && dist < radius * 0.3f) {
                    drillingIn = false
                    selectedIndex = -1
                    invalidate()
                    return true
                }

                var angle = Math.toDegrees(atan2(y.toDouble(), x.toDouble()))
                if (angle < 0) angle += 360
                
                if (angle in 180.0..360.0) {
                    val relativeAngle = angle - 180
                    if (!drillingIn) {
                        val sweep = 180f / items.size
                        val index = (relativeAngle / sweep).toInt().coerceIn(0, items.size - 1)
                        selectedIndex = index
                        drillingIn = true
                        onItemSelected?.invoke(items[selectedIndex], null)
                        invalidate()
                    } else {
                        val subItems = items[selectedIndex].subItems
                        val sweep = 180f / subItems.size
                        val index = (relativeAngle / sweep).toInt().coerceIn(0, subItems.size - 1)
                        onItemSelected?.invoke(items[selectedIndex], subItems[index])
                        invalidate()
                    }
                }
            }
            return true
        }
        return super.onTouchEvent(event)
    }
}
