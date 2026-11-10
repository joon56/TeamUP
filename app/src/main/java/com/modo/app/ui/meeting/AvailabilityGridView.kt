package com.modo.app.ui.meeting

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import com.modo.app.R
import com.modo.app.logic.MeetingFinder
import com.modo.app.logic.MeetingSlot
import com.modo.app.logic.WeekAvailability
import com.modo.app.ui.common.Fmt
import com.modo.app.ui.common.colorOf
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * when2meet식 일주일 격자. 열 = 월~일, 행 = 30분 (08:00 ~ 24:00, 회의 추천 범위와 같음).
 * - 편집 모드: 누르거나 끌어서 사각형으로 칠함. 칠한 칸에서 시작하면 지움. 손을 떼면 onChange.
 * - 히트맵 모드(setHeatmap): 가능한 인원이 많을수록 진하게, 추천 시간에 테두리와 순위. 칸을 누르면 onCellTap.
 * 높이가 정해져 있으면(EXACTLY) 행 높이를 늘려 채우고, 아니면 기본 행 높이로 그린다.
 */
class AvailabilityGridView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val firstSlot = MeetingFinder.FIRST_SLOT
    private val rows = MeetingFinder.LAST_SLOT - MeetingFinder.FIRST_SLOT

    private fun dp(value: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)
    private fun sp(value: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    private val gutter = dp(40f)
    private val header = dp(28f)
    private val colGap = dp(3f)
    private val rowGap = dp(1f)
    private val radius = dp(4f)
    private val preferredRow = dp(16f)
    private val minRow = dp(12f)
    private val maxRow = dp(30f)
    private var rowHeight = preferredRow

    private val emptyColor = context.colorOf(R.color.surface_high)
    private val fillColor = context.colorOf(R.color.primary)
    private val strongText = context.colorOf(R.color.text_primary)

    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.colorOf(R.color.text_tertiary)
        textSize = sp(10f)
        textAlign = Paint.Align.RIGHT
        typeface = ResourcesCompat.getFont(context, R.font.pretendard)
    }
    private val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.colorOf(R.color.text_secondary)
        textSize = sp(12f)
        textAlign = Paint.Align.CENTER
        typeface = ResourcesCompat.getFont(context, R.font.pretendard_semibold)
    }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val badgeText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.colorOf(R.color.bg)
        textSize = sp(10f)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(ResourcesCompat.getFont(context, R.font.pretendard_semibold), Typeface.BOLD)
    }
    private val rect = RectF()
    private val dayLabels = (1..7).map { Fmt.weekday(context, it) }

    /** 편집 모드에서 칠한 가능 시간 */
    var week: WeekAvailability = WeekAvailability()
        set(value) {
            field = value
            invalidate()
        }

    /** 편집 모드: 한 번 끌기가 끝날 때마다 */
    var onChange: ((WeekAvailability) -> Unit)? = null

    /** 히트맵 모드: 칸을 눌렀을 때 (요일 1~7, 칸) */
    var onCellTap: ((day: Int, slot: Int) -> Unit)? = null

    private var heat: Array<IntArray>? = null
    private var heatTotal = 0
    private var highlights: List<MeetingSlot> = emptyList()
    private var selected: Pair<Int, Int>? = null

    // 끌기 상태 (편집 모드)
    private var dragBase: WeekAvailability? = null
    private var downCell: Pair<Int, Int>? = null
    private var lastCell: Pair<Int, Int>? = null
    private var paintFree = true

    /** 히트맵 모드로 바꾸고 인원 수(map[요일-1][칸])·전체 인원·추천 시간(순위 순)을 그림 */
    fun setHeatmap(map: Array<IntArray>, total: Int, recommended: List<MeetingSlot>) {
        heat = map
        heatTotal = total
        highlights = recommended
        invalidate()
    }

    fun select(cell: Pair<Int, Int>?) {
        selected = cell
        invalidate()
    }

    /** 인원 수에 맞는 칸 색 (범례에도 씀) */
    fun colorFor(count: Int, total: Int): Int =
        if (count <= 0 || total <= 0) emptyColor
        else ColorUtils.blendARGB(emptyColor, fillColor, 0.3f + 0.7f * count.coerceAtMost(total) / total)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val mode = MeasureSpec.getMode(heightMeasureSpec)
        val size = MeasureSpec.getSize(heightMeasureSpec)
        val fit = (size - header - paddingTop - paddingBottom) / rows
        rowHeight = when (mode) {
            MeasureSpec.EXACTLY -> fit.coerceIn(minRow, maxRow)
            MeasureSpec.AT_MOST -> min(preferredRow, fit).coerceAtLeast(minRow)
            else -> preferredRow
        }
        val desired = (paddingTop + header + rows * rowHeight + paddingBottom).toInt()
        setMeasuredDimension(width, if (mode == MeasureSpec.EXACTLY) size else desired)
    }

    private val colWidth: Float get() = (width - paddingLeft - paddingRight - gutter) / 7f
    private fun colLeft(day: Int): Float = paddingLeft + gutter + (day - 1) * colWidth
    private fun rowTop(slot: Int): Float = paddingTop + header + (slot - firstSlot) * rowHeight

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawHeader(canvas)
        drawTimeLabels(canvas)
        for (day in 1..7) drawColumn(canvas, day)
        drawHighlights(canvas)
        selected?.let { (day, slot) ->
            cellRect(day, slot, slot + 1)
            strokePaint.color = context.colorOf(R.color.warning)
            canvas.drawRoundRect(rect, radius, radius, strokePaint)
        }
    }

    private fun drawHeader(canvas: Canvas) {
        val baseline = paddingTop + header / 2 - (dayPaint.descent() + dayPaint.ascent()) / 2
        for (day in 1..7) canvas.drawText(dayLabels[day - 1], colLeft(day) + colWidth / 2, baseline, dayPaint)
    }

    /** 정시마다 그 시간 첫 칸 위쪽에 "08" 같은 시각 */
    private fun drawTimeLabels(canvas: Canvas) {
        val x = paddingLeft + gutter - dp(6f)
        for (slot in firstSlot until firstSlot + rows step 2) {
            val hour = WeekAvailability.slotTime(slot).hour
            canvas.drawText("%02d:00".format(hour), x, rowTop(slot) - labelPaint.ascent(), labelPaint)
        }
    }

    /** 같은 상태가 이어지는 칸은 한 덩어리로, 빈 칸은 하나씩 그림 */
    private fun drawColumn(canvas: Canvas, day: Int) {
        var slot = firstSlot
        val end = firstSlot + rows
        while (slot < end) {
            val level = levelOf(day, slot)
            var runEnd = slot + 1
            if (level > 0) while (runEnd < end && levelOf(day, runEnd) == level) runEnd++
            cellRect(day, slot, runEnd)
            cellPaint.color = colorOfLevel(level)
            canvas.drawRoundRect(rect, radius, radius, cellPaint)
            slot = runEnd
        }
    }

    /** 0 = 비어 있음. 편집 모드는 1(칠함), 히트맵 모드는 가능 인원 수 */
    private fun levelOf(day: Int, slot: Int): Int {
        val map = heat
        return if (map != null) map[day - 1][slot] else if (week.isFree(day, slot)) 1 else 0
    }

    private fun colorOfLevel(level: Int): Int = when {
        level <= 0 -> emptyColor
        heat == null -> fillColor
        else -> colorFor(level, heatTotal)
    }

    private fun cellRect(day: Int, fromSlot: Int, toSlotExclusive: Int) {
        val left = colLeft(day)
        rect.set(
            left + colGap / 2, rowTop(fromSlot) + rowGap / 2,
            left + colWidth - colGap / 2, rowTop(toSlotExclusive) - rowGap / 2,
        )
    }

    /** 추천 시간: 테두리 + 오른쪽 위 순위 동그라미 */
    private fun drawHighlights(canvas: Canvas) {
        if (heat == null) return
        strokePaint.color = strongText
        badgePaint.color = strongText
        val badgeRadius = dp(8f)
        // 1순위가 맨 위에 보이도록 거꾸로 그림
        for ((index, slot) in highlights.withIndex().reversed()) {
            cellRect(slot.day, slot.start, slot.end)
            canvas.drawRoundRect(rect, radius, radius, strokePaint)
            val cx = rect.right - badgeRadius * 0.6f
            val cy = rect.top + badgeRadius * 0.6f
            canvas.drawCircle(cx, cy, badgeRadius, badgePaint)
            canvas.drawText((index + 1).toString(), cx, cy - (badgeText.descent() + badgeText.ascent()) / 2, badgeText)
        }
    }

    /** (요일, 칸). clamp면 격자 밖도 가장 가까운 칸으로 */
    private fun cellAt(x: Float, y: Float, clamp: Boolean = false): Pair<Int, Int>? {
        val gx = x - paddingLeft - gutter
        val gy = y - paddingTop - header
        var col = floor(gx / colWidth).toInt()
        var row = floor(gy / rowHeight).toInt()
        if (clamp) {
            col = col.coerceIn(0, 6)
            row = row.coerceIn(0, rows - 1)
        } else if (gx < 0 || gy < 0 || col !in 0..6 || row !in 0 until rows) {
            return null
        }
        return Pair(col + 1, firstSlot + row)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        val editing = heat == null
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val cell = cellAt(event.x, event.y) ?: return false // 시각·요일 줄은 스크롤에 양보
                downCell = cell
                if (editing) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    dragBase = week
                    paintFree = !week.isFree(cell.first, cell.second)
                    dragTo(cell)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (editing && dragBase != null) {
                    val cell = cellAt(event.x, event.y, clamp = true)
                    if (cell != null && cell != lastCell) {
                        dragTo(cell)
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (editing) {
                    finishDrag()
                } else {
                    val cell = cellAt(event.x, event.y)
                    if (cell != null && cell == downCell) {
                        select(cell)
                        onCellTap?.invoke(cell.first, cell.second)
                    }
                }
                downCell = null
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (editing) finishDrag()
                downCell = null
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean = super.performClick()

    /** 누른 칸 ~ 지금 칸 사각형을 칠하거나 지움 (끌기 시작 때 상태 기준) */
    private fun dragTo(cell: Pair<Int, Int>) {
        val base = dragBase ?: return
        val start = downCell ?: return
        val fromSlot = min(start.second, cell.second)
        val toSlot = max(start.second, cell.second) + 1
        var next = base
        for (day in min(start.first, cell.first)..max(start.first, cell.first)) {
            next = next.withRange(day, fromSlot, toSlot, paintFree)
        }
        lastCell = cell
        week = next
    }

    private fun finishDrag() {
        val base = dragBase ?: return
        dragBase = null
        lastCell = null
        if (week != base) onChange?.invoke(week)
    }
}
