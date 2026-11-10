package com.modo.app.ui.common

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.GridLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.modo.app.R
import com.modo.app.databinding.ItemMonthDayBinding
import com.modo.app.logic.CalendarMath
import com.modo.app.logic.DayMarkers
import java.time.LocalDate
import java.time.YearMonth

/**
 * 월간 달력: GridLayout 7열 (첫 줄 요일 머리글 + 6주 × 7일).
 * 칸 너비는 열 가중치(weight 1)로 똑같이 나눈다. 점 = 그날 일정이 있는 프로젝트 색 (최대 3개)
 */
class MonthCalendarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : GridLayout(context, attrs) {

    var onDaySelected: (LocalDate) -> Unit = {}

    private val cells: List<ItemMonthDayBinding>

    init {
        columnCount = 7
        // 요일 머리글
        for (day in 1..7) {
            val label = TextView(context).apply {
                text = Fmt.weekday(context, day)
                gravity = Gravity.CENTER
                setTextAppearance(R.style.TextAppearance_Modo_Small)
                setPadding(0, dp(6), 0, dp(6))
            }
            addView(label, cellParams())
        }
        cells = List(42) {
            val cell = ItemMonthDayBinding.inflate(LayoutInflater.from(context), this, false)
            addView(cell.root, cellParams())
            cell
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun cellParams() = LayoutParams(spec(UNDEFINED), spec(UNDEFINED, 1f)).apply { width = 0 }

    fun setMonth(month: YearMonth, markers: Map<LocalDate, DayMarkers>, selected: LocalDate, today: LocalDate) {
        val days = CalendarMath.monthGrid(month)
        for ((index, cell) in cells.withIndex()) {
            val date = days[index]
            val inMonth = YearMonth.from(date) == month
            val isSelected = date == selected
            val isToday = date == today
            cell.date.text = date.dayOfMonth.toString()
            cell.date.setBackgroundResource(
                when {
                    isSelected -> R.drawable.bg_day_selected
                    isToday -> R.drawable.bg_day_today
                    else -> 0
                }
            )
            cell.date.setTextColor(
                context.colorOf(
                    when {
                        isSelected -> R.color.on_primary
                        !inMonth -> R.color.text_tertiary
                        else -> R.color.text_primary
                    }
                )
            )
            cell.root.alpha = if (inMonth) 1f else 0.45f
            val colors = (markers[date] ?: DayMarkers()).colors
            listOf(cell.dot1, cell.dot2, cell.dot3).forEachIndexed { i, dot ->
                val color = colors.getOrNull(i)
                dot.isVisible = color != null
                if (color != null) dot.backgroundTintList = context.tintOf(Labels.projectColor(color))
            }
            cell.root.contentDescription = Fmt.date(context, date)
            cell.root.setOnClickListener { onDaySelected(date) }
        }
    }
}
