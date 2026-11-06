package com.teamup.app.ui.main

import android.os.Bundle
import com.teamup.app.R
import com.teamup.app.data.AppData
import com.teamup.app.data.TaskStatus
import com.teamup.app.data.TeamRepository
import com.teamup.app.databinding.TabTasksBinding
import com.teamup.app.logic.TaskFilter
import com.teamup.app.logic.TaskRules
import com.teamup.app.ui.common.Items
import com.teamup.app.ui.common.failureToast
import com.teamup.app.ui.common.toast
import com.teamup.app.ui.search.SearchActivity
import com.teamup.app.ui.task.TaskEditActivity
import java.time.LocalDate

/** 업무 탭: 내 모든 프로젝트의 업무를 상태(RadioGroup)·담당자(CheckBox)로 걸러서 마감순으로 */
class TasksTab(private val activity: MainActivity, private val b: TabTasksBinding) {

    private var filter = TaskFilter.ALL
    private var mineOnly = false
    private var data = AppData()

    init {
        b.tasksSearch.setOnClickListener { SearchActivity.start(activity) }

        // 값이 실제로 바뀔 때만 다시 그림 (코드에서 check()해도 무한 반복 안 됨)
        b.tasksFilterGroup.setOnCheckedChangeListener { _, checkedId ->
            val selected = filterOf(checkedId) ?: return@setOnCheckedChangeListener
            if (selected != filter) {
                filter = selected
                render(data)
            }
        }
        b.tasksMineOnly.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked != mineOnly) {
                mineOnly = isChecked
                render(data)
            }
        }

        b.tasksFab.setOnClickListener {
            if (data.projects.isEmpty()) {
                activity.toast(R.string.error_no_project)
            } else {
                TaskEditActivity.start(activity, null, null)
            }
        }
    }

    fun saveState(out: Bundle) {
        out.putString(KEY_FILTER, filter.name)
        out.putBoolean(KEY_MINE_ONLY, mineOnly)
    }

    fun restoreState(state: Bundle) {
        filter = TaskFilter.entries.firstOrNull { it.name == state.getString(KEY_FILTER) } ?: TaskFilter.ALL
        mineOnly = state.getBoolean(KEY_MINE_ONLY, false)
        b.tasksFilterGroup.check(buttonOf(filter))
        b.tasksMineOnly.isChecked = mineOnly
    }

    fun render(data: AppData) {
        this.data = data
        val today = LocalDate.now()
        val tasks = TaskRules.sortForList(
            TaskRules.filter(data.allTasks, filter, mineOnly, data.myUid, today)
        )
        b.tasksCount.text = activity.getString(R.string.tasks_count, tasks.size)

        b.tasksList.removeAllViews()
        for (task in tasks) {
            Items.addTask(
                b.tasksList, task, data, today, showProject = true,
                onToggle = { checked ->
                    TeamRepository.setTaskStatus(
                        task, if (checked) TaskStatus.DONE else TaskStatus.TODO, activity.failureToast(),
                    )
                },
                onClick = { TaskEditActivity.start(activity, task.projectId, task.id) },
            )
        }
        if (tasks.isEmpty()) {
            Items.addEmpty(b.tasksList, if (data.loading) R.string.loading else R.string.tasks_empty_filter)
        }
    }

    /** RadioButton id → 필터 */
    private fun filterOf(buttonId: Int): TaskFilter? = when (buttonId) {
        R.id.tasksFilterAll -> TaskFilter.ALL
        R.id.tasksFilterTodo -> TaskFilter.TODO
        R.id.tasksFilterInProgress -> TaskFilter.IN_PROGRESS
        R.id.tasksFilterDone -> TaskFilter.DONE
        R.id.tasksFilterOverdue -> TaskFilter.OVERDUE
        else -> null
    }

    /** 필터 → RadioButton id */
    private fun buttonOf(filter: TaskFilter): Int = when (filter) {
        TaskFilter.ALL -> R.id.tasksFilterAll
        TaskFilter.TODO -> R.id.tasksFilterTodo
        TaskFilter.IN_PROGRESS -> R.id.tasksFilterInProgress
        TaskFilter.DONE -> R.id.tasksFilterDone
        TaskFilter.OVERDUE -> R.id.tasksFilterOverdue
    }

    companion object {
        private const val KEY_FILTER = "tasks_filter"
        private const val KEY_MINE_ONLY = "tasks_mine_only"
    }
}
