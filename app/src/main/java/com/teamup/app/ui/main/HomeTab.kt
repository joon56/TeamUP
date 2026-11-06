package com.teamup.app.ui.main

import android.os.Bundle
import android.widget.PopupMenu
import androidx.core.view.isVisible
import com.teamup.app.R
import com.teamup.app.data.AppData
import com.teamup.app.data.TaskStatus
import com.teamup.app.data.TeamRepository
import com.teamup.app.databinding.TabHomeBinding
import com.teamup.app.logic.Greeting
import com.teamup.app.logic.ProgressCalculator
import com.teamup.app.logic.Schedule
import com.teamup.app.logic.ScheduleItem
import com.teamup.app.logic.TaskRules
import com.teamup.app.logic.greetingOf
import com.teamup.app.ui.common.Dialogs
import com.teamup.app.ui.common.Items
import com.teamup.app.ui.common.addPressEffect
import com.teamup.app.ui.common.failureToast
import com.teamup.app.ui.common.sampleLabels
import com.teamup.app.ui.common.toast
import com.teamup.app.ui.project.ProjectActions
import com.teamup.app.ui.project.ProjectDetailActivity
import com.teamup.app.ui.project.ProjectEditActivity
import com.teamup.app.ui.search.SearchActivity
import com.teamup.app.ui.task.TaskEditActivity
import java.time.LocalDate
import java.time.LocalDateTime

/** 홈 대시보드 탭 */
class HomeTab(private val activity: MainActivity, private val b: TabHomeBinding) {

    private enum class Filter { ACTIVE, ALL, FINISHED }

    private var filter = Filter.ACTIVE
    private var data = AppData()

    init {
        b.actionNew.setOnClickListener { ProjectEditActivity.start(activity, null) }
        b.actionJoin.setOnClickListener {
            Dialogs.showJoin(activity) { projectId -> ProjectDetailActivity.start(activity, projectId) }
        }
        b.actionNew.addPressEffect()
        b.actionJoin.addPressEffect()
        b.search.setOnClickListener { SearchActivity.start(activity) }
        b.bell.setOnClickListener { activity.selectTab(R.id.nav_schedule) }
        b.filterButton.setOnClickListener { showFilterMenu() }
        b.sampleButton.setOnClickListener {
            TeamRepository.createSampleProjects(activity.sampleLabels(), activity.failureToast())
            activity.toast(R.string.msg_sample_created)
        }
    }

    fun saveState(out: Bundle) = out.putString(KEY_FILTER, filter.name)

    fun restoreState(state: Bundle) {
        filter = Filter.entries.firstOrNull { it.name == state.getString(KEY_FILTER) } ?: Filter.ACTIVE
    }

    fun render(data: AppData) {
        this.data = data
        val today = LocalDate.now()
        val now = LocalDateTime.now()

        // 인사 (시간대별)
        val greetingRes = when (greetingOf(now.hour)) {
            Greeting.MORNING -> R.string.greeting_morning
            Greeting.AFTERNOON -> R.string.greeting_afternoon
            Greeting.EVENING -> R.string.greeting_evening
            Greeting.NIGHT -> R.string.greeting_night
        }
        b.greeting.text = activity.getString(greetingRes, data.myName)

        // 통계 2×2
        val allTasks = data.allTasks
        val counts = ProgressCalculator.statusCounts(allTasks, today)
        b.statTotal.text = counts.total.toString()
        b.statDone.text = counts.done.toString()
        b.statInProgress.text = counts.inProgress.toString()
        b.statOverdue.text = counts.overdue.toString()

        // 알림 배지: 지연 + 3일 안에 마감
        val alerts = allTasks.count { TaskRules.isOverdue(it, today) || TaskRules.isDueSoon(it, today) }
        b.bellBadge.isVisible = alerts > 0
        b.bellBadge.text = if (alerts > 99) "99+" else alerts.toString()

        renderProjects(today)
        renderNextUp(now, today)
        renderRecent(today)
    }

    private fun renderProjects(today: LocalDate) {
        val filtered = data.projects.filter { project ->
            when (filter) {
                Filter.ACTIVE -> !project.endDate.isBefore(today)
                Filter.ALL -> true
                Filter.FINISHED -> project.endDate.isBefore(today)
            }
        }
        b.filterButton.setText(
            when (filter) {
                Filter.ACTIVE -> R.string.filter_active
                Filter.ALL -> R.string.filter_all
                Filter.FINISHED -> R.string.filter_finished
            }
        )
        b.projectsCount.text = filtered.size.toString()
        b.emptyState.isVisible = !data.loading && data.projects.isEmpty()

        b.projectList.removeAllViews()
        for (project in filtered) {
            Items.addProject(
                b.projectList, project, data, today,
                onClick = { ProjectDetailActivity.start(activity, project.id) },
                onMore = { anchor -> ProjectActions.showMenu(activity, anchor, project, data.myUid) },
            )
        }
        if (filtered.isEmpty() && data.projects.isNotEmpty()) {
            Items.addEmpty(b.projectList, R.string.home_no_projects_in_filter)
        }
    }

    private fun renderNextUp(now: LocalDateTime, today: LocalDate) {
        b.nextUpList.removeAllViews()
        val items = Schedule.upcoming(data, now)
        for (item in items) {
            Items.addSchedule(b.nextUpList, item, today) {
                when (item) {
                    is ScheduleItem.TaskDue -> TaskEditActivity.start(activity, item.project.id, item.task.id)
                    else -> ProjectDetailActivity.start(activity, item.project.id)
                }
            }
        }
        if (items.isEmpty()) Items.addEmpty(b.nextUpList, R.string.home_no_upcoming)
    }

    private fun renderRecent(today: LocalDate) {
        b.recentList.removeAllViews()
        val recent = data.allTasks.sortedByDescending { it.updatedAt }.take(3)
        for (task in recent) {
            Items.addTask(
                b.recentList, task, data, today, showProject = true,
                onToggle = { checked ->
                    TeamRepository.setTaskStatus(
                        task, if (checked) TaskStatus.DONE else TaskStatus.TODO, activity.failureToast(),
                    )
                },
                onClick = { TaskEditActivity.start(activity, task.projectId, task.id) },
            )
        }
        if (recent.isEmpty()) Items.addEmpty(b.recentList, R.string.home_no_recent)
    }

    private fun showFilterMenu() {
        val popup = PopupMenu(activity, b.filterButton)
        val filters = Filter.entries
        for ((index, f) in filters.withIndex()) {
            val title = when (f) {
                Filter.ACTIVE -> R.string.filter_active
                Filter.ALL -> R.string.filter_all
                Filter.FINISHED -> R.string.filter_finished
            }
            popup.menu.add(0, index, index, title)
        }
        popup.setOnMenuItemClickListener { item ->
            filter = filters[item.itemId]
            render(data)
            true
        }
        popup.show()
    }

    companion object {
        private const val KEY_FILTER = "home_filter"
    }
}
