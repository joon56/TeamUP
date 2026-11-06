package com.teamup.app.logic

import com.teamup.app.data.Task
import com.teamup.app.data.TaskStatus
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class TaskFilter { ALL, TODO, IN_PROGRESS, DONE, OVERDUE }

object TaskRules {

    /** 마감일이 지났는데 완료되지 않은 업무 */
    fun isOverdue(task: Task, today: LocalDate): Boolean =
        task.status != TaskStatus.DONE && task.dueDate.isBefore(today)

    /** 마감까지 남은 일수. 0 = 오늘, 음수 = 지남 */
    fun dDay(date: LocalDate, today: LocalDate): Int =
        ChronoUnit.DAYS.between(today, date).toInt()

    /** 오늘 포함 days일 안에 마감되는 미완료 업무 */
    fun isDueSoon(task: Task, today: LocalDate, days: Int = 3): Boolean =
        task.status != TaskStatus.DONE && dDay(task.dueDate, today) in 0..days

    /** "다음 주로 미루기": 7일씩 미뤄서 오늘 이후가 될 때까지 */
    fun postponeDate(due: LocalDate, today: LocalDate): LocalDate {
        var date = due.plusWeeks(1)
        while (date.isBefore(today)) {
            date = date.plusWeeks(1)
        }
        return date
    }

    fun filter(
        tasks: List<Task>,
        filter: TaskFilter,
        mineOnly: Boolean,
        myId: String,
        today: LocalDate,
    ): List<Task> = tasks.filter { task ->
        val statusOk = when (filter) {
            TaskFilter.ALL -> true
            TaskFilter.TODO -> task.status == TaskStatus.TODO
            TaskFilter.IN_PROGRESS -> task.status == TaskStatus.IN_PROGRESS
            TaskFilter.DONE -> task.status == TaskStatus.DONE
            TaskFilter.OVERDUE -> isOverdue(task, today)
        }
        statusOk && (!mineOnly || task.assigneeId == myId)
    }

    /** 미완료 → 마감 빠른 순 → 우선순위 높은 순, 완료는 맨 아래 */
    fun sortForList(tasks: List<Task>): List<Task> =
        tasks.sortedWith(
            compareBy<Task>({ it.status == TaskStatus.DONE }, { it.dueDate }, { it.priority.ordinal })
        )
}
