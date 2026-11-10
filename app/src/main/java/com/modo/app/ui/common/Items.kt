package com.modo.app.ui.common

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import com.modo.app.R
import com.modo.app.data.AppData
import com.modo.app.data.Member
import com.modo.app.data.Project
import com.modo.app.data.Task
import com.modo.app.data.TaskStatus
import com.modo.app.databinding.ItemMemberBinding
import com.modo.app.databinding.ItemProjectBinding
import com.modo.app.databinding.ItemScheduleBinding
import com.modo.app.databinding.ItemTaskBinding
import com.modo.app.databinding.ItemWeekBinding
import com.modo.app.logic.MemberStat
import com.modo.app.logic.ProgressCalculator
import com.modo.app.logic.ScheduleItem
import com.modo.app.logic.TaskRules
import com.modo.app.logic.WeekCalculator
import com.modo.app.logic.WeekProgress
import java.time.LocalDate
import kotlin.math.abs

/**
 * 목록 항목을 LinearLayout에 하나씩 inflate 해서 붙이는 함수 모음.
 * (RecyclerView 대신 수업에서 배운 LinearLayout + ViewBinding 사용)
 */
object Items {

    private fun inflater(container: LinearLayout) = LayoutInflater.from(container.context)

    /**
     * 목록 항목들은 같은 id(check, progress …)를 여러 개 가지므로, 화면 회전 때
     * 시스템이 상태를 저장·복원하면 엉뚱한 항목에 값이 들어간다. 항목은 데이터로 다시 그리므로 저장에서 제외.
     */
    private fun excludeFromStateSaving(view: View) {
        view.isSaveFromParentEnabled = false
    }

    /** 아이콘 상자를 색 20% 배경 + 색 아이콘으로 */
    fun tintIconBox(view: android.widget.ImageView, color: Int) {
        view.backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 0x38))
        view.imageTintList = ColorStateList.valueOf(color)
    }

    /** 이름 첫 글자 아바타 색 (이름마다 고정) */
    fun avatarColor(context: Context, name: String): Int =
        context.colorOf(Labels.PROJECT_COLORS[abs(name.hashCode()) % Labels.PROJECT_COLORS.size])

    fun addHeader(container: LinearLayout, text: String) {
        val view = inflater(container).inflate(R.layout.item_section_header, container, false) as TextView
        view.text = text
        container.addView(view)
    }

    fun addEmpty(container: LinearLayout, @StringRes text: Int) {
        val view = inflater(container).inflate(R.layout.item_empty, container, false) as TextView
        view.setText(text)
        container.addView(view)
    }

    // ───────────────────────── 프로젝트 카드 ─────────────────────────

    fun addProject(
        container: LinearLayout,
        project: Project,
        data: AppData,
        today: LocalDate,
        onClick: () -> Unit,
        onMore: (View) -> Unit,
    ) {
        val context = container.context
        val b = ItemProjectBinding.inflate(inflater(container), container, false)
        val color = context.colorOf(Labels.projectColor(project.colorIndex))
        tintIconBox(b.icon, color)
        b.name.text = project.name

        val tasks = data.tasksOf(project.id)
        val finished = project.endDate.isBefore(today)
        // 진행 중이면 이번 주 진행률, 끝났으면 전체 진행률
        val rate = if (finished) ProgressCalculator.rate(tasks)
        else ProgressCalculator.weekRate(project, tasks, WeekCalculator.weekOf(project, today))
        val left = if (finished) context.getString(R.string.project_finished) else Fmt.dDay(context, project.endDate, today)
        val right = when {
            finished -> Fmt.percent(context, rate)
            rate == null -> context.getString(R.string.this_week_none)
            else -> context.getString(R.string.this_week_rate, rate)
        }
        b.meta.text = context.getString(R.string.dot_join, left, right)
        b.progress.setIndicatorColor(color)
        b.progress.progress = rate ?: 0

        b.root.setOnClickListener { onClick() }
        b.root.addPressEffect()
        b.more.setOnClickListener { onMore(it) }
        excludeFromStateSaving(b.root)
        container.addView(b.root)
    }

    // ───────────────────────── 업무 카드 ─────────────────────────

    fun addTask(
        container: LinearLayout,
        task: Task,
        data: AppData,
        today: LocalDate,
        showProject: Boolean,
        onToggle: (Boolean) -> Unit,
        onClick: () -> Unit,
    ) {
        val context = container.context
        val b = ItemTaskBinding.inflate(inflater(container), container, false)
        val done = task.status == TaskStatus.DONE
        val overdue = TaskRules.isOverdue(task, today)

        b.priorityBar.backgroundTintList = context.tintOf(Labels.priorityColor(task.priority))
        b.title.text = task.title
        b.title.paintFlags = if (done) b.title.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        else b.title.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        b.title.alpha = if (done) 0.55f else 1f

        val parts = mutableListOf<String>()
        if (showProject) data.project(task.projectId)?.let { parts.add(it.name) }
        parts.add(Labels.assignee(context, data, task.projectId, task.assigneeId))
        parts.add(context.getString(R.string.due_format, Fmt.relativeDay(context, task.dueDate, today)))
        b.meta.text = parts.joinToString(" · ")
        b.meta.setTextColor(context.colorOf(if (overdue) R.color.danger else R.color.text_secondary))

        b.statusBadge.text = Labels.status(context, task.status, overdue)
        b.statusBadge.setTextColor(context.colorOf(Labels.statusColor(task.status, overdue)))
        b.statusBadge.backgroundTintList = context.tintOf(Labels.statusContainer(task.status, overdue))

        b.check.isChecked = done
        b.check.contentDescription = task.title
        // 체크 "상태 변경"이 아니라 사용자 "클릭"에만 반응해야 함.
        // (화면 회전 시 시스템이 체크 상태를 복원하면서 변경 리스너가 불려 업무가 잘못 완료 처리되는 것을 막음)
        b.check.setOnClickListener { onToggle(b.check.isChecked) }

        b.root.setOnClickListener { onClick() }
        b.root.addPressEffect()
        excludeFromStateSaving(b.root)
        container.addView(b.root)
    }

    // ───────────────────────── 팀원 카드 ─────────────────────────

    /** 팀원 카드: 팀장이면 "팀장", 앱 사용자면 태그(#1234), 앱 안 쓰는 팀원이면 "앱 미사용" */
    fun addMember(
        container: LinearLayout,
        member: Member,
        stat: MemberStat,
        isMe: Boolean,
        isLeader: Boolean = false,
        onClick: () -> Unit,
    ) {
        val context = container.context
        val b = ItemMemberBinding.inflate(inflater(container), container, false)
        val color = Labels.avatarColor(context, member.avatarColor, member.name)
        b.avatarBg.imageTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 0x40))
        b.initial.text = member.name.take(1)
        b.initial.setTextColor(color)

        b.name.text = if (isMe) context.getString(R.string.name_with_me, member.name) else member.name
        when {
            isLeader -> {
                b.kind.setText(R.string.members_leader)
                b.kind.backgroundTintList = context.tintOf(R.color.primary_container)
                b.kind.setTextColor(context.colorOf(R.color.on_primary_container))
            }
            !member.isAppUser -> b.kind.setText(R.string.members_guest)
            member.tag.isNotEmpty() -> b.kind.text = "#${member.tag}"
            else -> b.kind.setText(R.string.member_app_user)
        }
        b.role.text = member.role
        b.role.isVisible = member.role.isNotBlank()
        b.progress.setIndicatorColor(color)
        b.progress.progress = stat.rate ?: 0
        b.doneCount.text = context.getString(R.string.done_of_total, stat.done, stat.total)

        b.root.setOnClickListener { onClick() }
        b.root.addPressEffect()
        excludeFromStateSaving(b.root)
        container.addView(b.root)
    }

    // ───────────────────────── 일정 ─────────────────────────

    fun addSchedule(container: LinearLayout, item: ScheduleItem, today: LocalDate, onClick: () -> Unit) {
        val context = container.context
        val b = ItemScheduleBinding.inflate(inflater(container), container, false)
        val project = item.project
        val color = context.colorOf(Labels.projectColor(project.colorIndex))
        tintIconBox(b.icon, color)
        val day = Fmt.relativeDay(context, item.date, today)

        when (item) {
            is ScheduleItem.Meeting -> {
                b.icon.setImageResource(R.drawable.ic_event)
                b.title.text = context.getString(R.string.meeting_with_project, project.name)
                val time = Fmt.time(context, item.dateTime.toLocalTime())
                b.subtitle.text = "$day $time"
                b.trailing.text = ""
            }
            is ScheduleItem.TaskDue -> {
                val overdue = TaskRules.isOverdue(item.task, today)
                b.icon.setImageResource(if (overdue) R.drawable.ic_warning else R.drawable.ic_tasks)
                b.title.text = item.task.title
                b.subtitle.text = context.getString(R.string.dot_join, context.getString(R.string.due_format, day), project.name)
                b.trailing.text = Fmt.dDay(context, item.date, today)
                b.trailing.setTextColor(context.colorOf(if (overdue) R.color.danger else R.color.text_tertiary))
            }
            is ScheduleItem.ProjectEnd -> {
                b.icon.setImageResource(R.drawable.ic_flag)
                b.title.setText(R.string.item_project_end)
                b.subtitle.text = context.getString(R.string.dot_join, day, project.name)
                b.trailing.text = Fmt.dDay(context, item.date, today)
            }
        }
        b.root.setOnClickListener { onClick() }
        b.root.addPressEffect()
        excludeFromStateSaving(b.root)
        container.addView(b.root)
    }

    // ───────────────────────── 주차별 진행률 ─────────────────────────

    fun addWeek(container: LinearLayout, week: WeekProgress, isCurrent: Boolean, color: Int) {
        val context = container.context
        val b = ItemWeekBinding.inflate(inflater(container), container, false)
        b.label.text = context.getString(R.string.week_label, week.index)
        b.currentBadge.isVisible = isCurrent
        val range = context.getString(
            R.string.range_format,
            Fmt.dateShort(context, week.range.start),
            Fmt.dateShort(context, week.range.endInclusive),
        )
        b.range.text = if (week.total == 0) range
        else context.getString(R.string.dot_join, range, context.getString(R.string.done_of_total, week.done, week.total))
        b.rate.text = Fmt.percent(context, week.rate)
        b.progress.setIndicatorColor(color)
        b.progress.progress = week.rate ?: 0
        excludeFromStateSaving(b.root)
        container.addView(b.root)
    }
}
