package com.modo.app.ui.root

import android.os.Bundle
import com.modo.app.R
import com.modo.app.data.AppData
import com.modo.app.data.Task
import com.modo.app.data.TaskStatus
import com.modo.app.data.TeamRepository
import com.modo.app.databinding.TabTasksBinding
import com.modo.app.logic.CalendarMath
import com.modo.app.logic.ProgressCalculator
import com.modo.app.logic.TaskFilter
import com.modo.app.logic.TaskRules
import com.modo.app.ui.common.Items
import com.modo.app.ui.common.failureToast
import com.modo.app.ui.search.SearchActivity
import com.modo.app.ui.task.TaskEditActivity
import java.time.LocalDate

/**
 * 루트 "할 일" 탭: 전 프로젝트 업무를 겹치지 않는 4분류(할 일·진행 중·지연·완료)로.
 * 마감이 지난 업무는 "할 일"에 섞이지 않고 "지연"에만 나온다 (회의 피드백 7).
 */
class TasksTab(private val activity: RootActivity, private val b: TabTasksBinding) {

    private var data = AppData()
    private var filter = TaskFilter.TODO
    private var mineOnly = true

    init {
        b.mineOnly.isChecked = mineOnly
        b.mineOnly.setOnCheckedChangeListener { _, checked ->
            mineOnly = checked
            render(data)
        }
        b.segments.check(buttonOf(filter))
        b.segments.setOnCheckedChangeListener { _, checkedId ->
            filterOf(checkedId)?.let {
                filter = it
                render(data)
            }
        }
        b.search.setOnClickListener { SearchActivity.start(activity) }
    }

    fun setFilter(newFilter: TaskFilter) {
        filter = newFilter
        b.segments.check(buttonOf(newFilter))
    }

    fun render(data: AppData) {
        this.data = data
        val today = LocalDate.now()
        val base = if (mineOnly) data.allTasks.filter { it.assigneeId == data.myUid } else data.allTasks
        val counts = ProgressCalculator.statusCounts(base, today)
        b.segTodo.text = activity.getString(R.string.seg_todo, counts.todo)
        b.segInProgress.text = activity.getString(R.string.seg_in_progress, counts.inProgress)
        b.segOverdue.text = activity.getString(R.string.seg_overdue, counts.overdue)
        b.segDone.text = activity.getString(R.string.seg_done, counts.done)

        b.list.removeAllViews()
        val tasks = TaskRules.filter(base, filter, false, data.myUid, today)
        if (tasks.isEmpty()) {
            Items.addEmpty(
                b.list,
                when (filter) {
                    TaskFilter.IN_PROGRESS -> R.string.tasks_empty_in_progress
                    TaskFilter.OVERDUE -> R.string.tasks_empty_overdue
                    TaskFilter.DONE -> R.string.tasks_empty_done
                    else -> R.string.tasks_empty_todo
                }
            )
            return
        }
        when (filter) {
            TaskFilter.DONE -> {
                Items.addHeader(b.list, activity.getString(R.string.group_recent_done))
                tasks.sortedByDescending { it.completedAt ?: it.updatedAt }.take(DONE_LIMIT).forEach { addTask(it, today) }
            }
            TaskFilter.OVERDUE -> {
                // 오래 밀린 것부터
                tasks.sortedWith(compareBy<Task>({ it.dueDate }, { it.priority.ordinal })).forEach { addTask(it, today) }
            }
            else -> addGroupedByDue(tasks, today)
        }
    }

    /** 오늘 / 내일 / 이번 주 / 다음 주 이후 */
    private fun addGroupedByDue(tasks: List<Task>, today: LocalDate) {
        val weekEnd = CalendarMath.weekStart(today).plusDays(6)
        val groups = tasks.sortedWith(compareBy<Task>({ it.dueDate }, { it.priority.ordinal })).groupBy { task ->
            when {
                task.dueDate == today -> activity.getString(R.string.today)
                task.dueDate == today.plusDays(1) -> activity.getString(R.string.tomorrow)
                !task.dueDate.isAfter(weekEnd) -> activity.getString(R.string.group_this_week)
                else -> activity.getString(R.string.group_after)
            }
        }
        for ((title, items) in groups) {
            Items.addHeader(b.list, activity.getString(R.string.dot_join, title, items.size.toString()))
            items.forEach { addTask(it, today) }
        }
    }

    private fun addTask(task: Task, today: LocalDate) {
        Items.addTask(
            b.list, task, data, today, showProject = true,
            onToggle = { checked ->
                TeamRepository.setTaskStatus(task, if (checked) TaskStatus.DONE else TaskStatus.TODO, activity.failureToast())
            },
            onClick = { TaskEditActivity.start(activity, task.projectId, task.id) },
        )
    }

    private fun filterOf(buttonId: Int): TaskFilter? = when (buttonId) {
        R.id.segTodo -> TaskFilter.TODO
        R.id.segInProgress -> TaskFilter.IN_PROGRESS
        R.id.segOverdue -> TaskFilter.OVERDUE
        R.id.segDone -> TaskFilter.DONE
        else -> null
    }

    private fun buttonOf(filter: TaskFilter): Int = when (filter) {
        TaskFilter.IN_PROGRESS -> R.id.segInProgress
        TaskFilter.OVERDUE -> R.id.segOverdue
        TaskFilter.DONE -> R.id.segDone
        else -> R.id.segTodo
    }

    fun saveState(out: Bundle) {
        out.putString(KEY_FILTER, filter.name)
        out.putBoolean(KEY_MINE, mineOnly)
    }

    fun restoreState(state: Bundle) {
        filter = TaskFilter.entries.firstOrNull { it.name == state.getString(KEY_FILTER) } ?: TaskFilter.TODO
        mineOnly = state.getBoolean(KEY_MINE, true)
        b.mineOnly.isChecked = mineOnly
        b.segments.check(buttonOf(filter))
    }

    companion object {
        private const val DONE_LIMIT = 50
        private const val KEY_FILTER = "tasks_filter"
        private const val KEY_MINE = "tasks_mine"
    }
}
