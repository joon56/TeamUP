package com.modo.app.ui.project.sections

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import com.modo.app.R
import com.modo.app.data.AppData
import com.modo.app.data.Project
import com.modo.app.data.Task
import com.modo.app.data.TaskStatus
import com.modo.app.data.TeamRepository
import com.modo.app.databinding.ItemBoardCardBinding
import com.modo.app.databinding.ItemBoardColumnBinding
import com.modo.app.databinding.SectionBoardBinding
import com.modo.app.logic.TaskRules
import com.modo.app.ui.common.Fmt
import com.modo.app.ui.common.Labels
import com.modo.app.ui.common.colorOf
import com.modo.app.ui.common.failureToast
import com.modo.app.ui.common.tintOf
import com.modo.app.ui.project.ProjectActivity
import com.modo.app.ui.task.TaskEditActivity
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * 보드 (시안의 "할 일 / 진행 중 / 완료").
 * 열은 진행 상태, 마감이 지난 카드는 빨간 D+ 알약으로 표시. 카드를 길게 누르면 다른 열로 옮김.
 */
class BoardSection(private val host: ProjectActivity, inflater: LayoutInflater, parent: ViewGroup) : Section {

    private val b = SectionBoardBinding.inflate(inflater, parent, false)
    override val root: View get() = b.root
    private val statuses = listOf(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.DONE)
    private val columns: List<ItemBoardColumnBinding>
    private var projectId = ""
    private val columnWidth: Int
    private val columnGap: Int = host.resources.getDimensionPixelSize(R.dimen.gap_m)

    init {
        columnWidth = (host.resources.displayMetrics.widthPixels * 0.84f).roundToInt()
        columns = statuses.map { status ->
            val column = ItemBoardColumnBinding.inflate(inflater, b.columns, false)
            column.root.layoutParams = LinearLayout.LayoutParams(columnWidth, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                marginEnd = columnGap
            }
            column.title.setText(statusLabel(status))
            column.dot.backgroundTintList = host.tintOf(statusColor(status))
            column.add.setOnClickListener { TaskEditActivity.start(host, projectId, null, status) }
            b.columns.addView(column.root)
            column
        }
        b.fab.setOnClickListener { TaskEditActivity.start(host, projectId, null) }
        enableSnap()
    }

    /** 손을 떼면 가장 가까운 열의 시작에 맞춤 (터치 이벤트) */
    @SuppressLint("ClickableViewAccessibility")
    private fun enableSnap() {
        b.boardScroll.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                b.boardScroll.postDelayed({
                    val step = columnWidth + columnGap
                    val index = (b.boardScroll.scrollX.toFloat() / step).roundToInt().coerceIn(0, statuses.lastIndex)
                    b.boardScroll.smoothScrollTo(index * step, 0)
                }, 60)
            }
            false
        }
    }

    override fun render(data: AppData, project: Project) {
        projectId = project.id
        val today = LocalDate.now()
        val tasks = data.tasksOf(project.id)
        for ((index, status) in statuses.withIndex()) {
            val column = columns[index]
            val inColumn = tasks.filter { it.status == status }.let { list ->
                if (status == TaskStatus.DONE) list.sortedByDescending { it.completedAt ?: it.updatedAt }
                else list.sortedWith(compareBy<Task>({ it.dueDate }, { it.priority.ordinal }))
            }
            column.count.text = inColumn.size.toString()
            column.cards.removeAllViews()
            if (inColumn.isEmpty()) addEmpty(column.cards)
            for (task in inColumn) addCard(column.cards, task, data, today)
        }
    }

    private fun addEmpty(container: LinearLayout) {
        val text = TextView(host).apply {
            setText(R.string.board_empty_column)
            setTextAppearance(R.style.TextAppearance_Modo_Small)
            val pad = resources.getDimensionPixelSize(R.dimen.gap_l)
            setPadding(pad, pad, pad, pad)
        }
        container.addView(text)
    }

    private fun addCard(container: LinearLayout, task: Task, data: AppData, today: LocalDate) {
        val card = ItemBoardCardBinding.inflate(LayoutInflater.from(host), container, false)
        val done = task.status == TaskStatus.DONE
        card.priorityBar.backgroundTintList = host.tintOf(Labels.priorityColor(task.priority))
        card.title.text = task.title
        card.title.paintFlags = if (done) card.title.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        else card.title.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        card.title.alpha = if (done) 0.6f else 1f

        val member = data.member(task.projectId, task.assigneeId)
        val name = Labels.assignee(host, data, task.projectId, task.assigneeId)
        card.assignee.text = name
        card.avatar.isVisible = member != null
        if (member != null) {
            val color = Labels.avatarColor(host, member.avatarColor, member.name)
            card.avatar.text = member.name.take(1)
            card.avatar.backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 0x40))
            card.avatar.setTextColor(color)
        }
        card.attachIcon.isVisible = task.attachments.isNotEmpty()

        val overdue = TaskRules.isOverdue(task, today)
        card.dday.isVisible = !done
        card.dday.text = Fmt.dDay(host, task.dueDate, today)
        val pillColor = when {
            overdue || TaskRules.dDay(task.dueDate, today) == 0 -> R.color.danger
            TaskRules.isDueSoon(task, today, 2) -> R.color.warning
            else -> R.color.text_secondary
        }
        card.dday.setTextColor(host.colorOf(pillColor))
        card.dday.backgroundTintList = host.tintOf(
            when (pillColor) {
                R.color.danger -> R.color.danger_container
                R.color.warning -> R.color.warning_container
                else -> R.color.surface_high
            }
        )

        card.root.setOnClickListener { TaskEditActivity.start(host, task.projectId, task.id) }
        card.root.setOnLongClickListener { view ->
            showMoveMenu(view, task)
            true
        }
        container.addView(card.root)
    }

    private fun showMoveMenu(anchor: View, task: Task) {
        val popup = PopupMenu(host, anchor)
        for ((index, status) in statuses.withIndex()) {
            if (status == task.status) continue
            popup.menu.add(0, index, index, host.getString(R.string.board_move_to, host.getString(statusLabel(status))))
        }
        popup.menu.add(0, 99, 99, R.string.board_open)
        popup.setOnMenuItemClickListener { item ->
            if (item.itemId == 99) TaskEditActivity.start(host, task.projectId, task.id)
            else TeamRepository.setTaskStatus(task, statuses[item.itemId], host.failureToast())
            true
        }
        popup.show()
    }

    private fun statusLabel(status: TaskStatus): Int = when (status) {
        TaskStatus.TODO -> R.string.status_todo
        TaskStatus.IN_PROGRESS -> R.string.status_in_progress
        TaskStatus.DONE -> R.string.status_done
    }

    private fun statusColor(status: TaskStatus): Int = when (status) {
        TaskStatus.TODO -> R.color.text_secondary
        TaskStatus.IN_PROGRESS -> R.color.info
        TaskStatus.DONE -> R.color.success
    }
}
