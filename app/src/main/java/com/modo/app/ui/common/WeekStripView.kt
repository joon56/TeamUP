package com.modo.app.ui.common

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.widget.LinearLayout
import androidx.core.view.isVisible
import com.modo.app.R
import com.modo.app.databinding.ItemWeekDayBinding
import com.modo.app.logic.DayMarkers
import java.time.LocalDate
import kotlin.math.abs

/**
 * 홈 상단 "주간 캘린더 한 줄".
 * 날짜를 누르면 [onDaySelected], 좌우로 밀면 [onSwipe](-1 지난주 / +1 다음 주).
 * 점: 보라 = 정기 회의, 주황 = 마감, 빨강 = 지연된 마감
 */
class WeekStripView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    var onDaySelected: (LocalDate) -> Unit = {}
    var onSwipe: (Int) -> Unit = {}

    private val cells = List(7) { ItemWeekDayBinding.inflate(LayoutInflater.from(context), this, false) }

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            val start = e1 ?: return false
            val dx = e2.x - start.x
            if (abs(dx) < width / 6f || abs(dx) < abs(e2.y - start.y)) return false
            onSwipe(if (dx < 0) 1 else -1)
            return true
        }
    })

    init {
        orientation = HORIZONTAL
        cells.forEach { addView(it.root) }
    }

    /** 가로 밀기를 먼저 확인하고, 아니면 날짜 칸이 클릭을 받도록 넘긴다 */
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        gestures.onTouchEvent(ev)
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean = gestures.onTouchEvent(event) || super.onTouchEvent(event)

    fun setDays(days: List<LocalDate>, markers: Map<LocalDate, DayMarkers>, selected: LocalDate, today: LocalDate) {
        for ((index, cell) in cells.withIndex()) {
            val date = days.getOrNull(index) ?: continue
            val isSelected = date == selected
            val isToday = date == today
            cell.weekday.text = Fmt.weekday(context, date.dayOfWeek.value)
            cell.date.text = date.dayOfMonth.toString()
            cell.root.setBackgroundResource(
                when {
                    isSelected -> R.drawable.bg_day_selected
                    isToday -> R.drawable.bg_day_today
                    else -> 0
                }
            )
            val onSelected = context.colorOf(R.color.on_primary)
            cell.date.setTextColor(if (isSelected) onSelected else context.colorOf(R.color.text_primary))
            cell.weekday.setTextColor(
                when {
                    isSelected -> onSelected
                    date.dayOfWeek.value >= 6 -> context.colorOf(R.color.text_tertiary)
                    else -> context.colorOf(R.color.text_secondary)
                }
            )
            val mark = markers[date] ?: DayMarkers()
            cell.dot1.isVisible = mark.meeting
            cell.dot1.backgroundTintList = context.tintOf(if (isSelected) R.color.on_primary else R.color.primary)
            cell.dot2.isVisible = mark.deadline
            cell.dot2.backgroundTintList = context.tintOf(
                when {
                    isSelected -> R.color.on_primary
                    mark.overdue -> R.color.danger
                    else -> R.color.warning
                }
            )
            cell.root.contentDescription = Fmt.date(context, date)
            cell.root.setOnClickListener { onDaySelected(date) }
        }
    }
}
