package com.modo.app.logic

import com.modo.app.data.Task
import com.modo.app.data.TaskStatus
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class TaskFilter { ALL, TODO, IN_PROGRESS, DONE, OVERDUE }

/**
 * 화면에 보여 줄 업무 분류. 서로 겹치지 않는다.
 * 마감이 지났는데 끝나지 않은 업무는 상태(할 일·진행 중)와 상관없이 [OVERDUE].
 */
enum class TaskCategory { TODO, IN_PROGRESS, OVERDUE, DONE }

object TaskRules {

    /** 마감일이 지났는데 완료되지 않은 업무 */
    fun isOverdue(task: Task, today: LocalDate): Boolean =
        task.status != TaskStatus.DONE && task.dueDate.isBefore(today)

    fun category(task: Task, today: LocalDate): TaskCategory = when {
        task.status == TaskStatus.DONE -> TaskCategory.DONE
        isOverdue(task, today) -> TaskCategory.OVERDUE
        task.status == TaskStatus.IN_PROGRESS -> TaskCategory.IN_PROGRESS
        else -> TaskCategory.TODO
    }

    /** 마감까지 남은 일수. 0 = 오늘, 음수 = 지남 */
    fun dDay(date: LocalDate, today: LocalDate): Int =
        ChronoUnit.DAYS.between(today, date).toInt()

    /** 오늘 포함 days일 안에 마감되는 미완료 업무 */
    fun isDueSoon(task: Task, today: LocalDate, days: Int = 3): Boolean =
        task.status != TaskStatus.DONE && dDay(task.dueDate, today) in 0..days

    /** 마감 임박 알림 대상: 지연 또는 D-days 이내 */
    fun needsAttention(task: Task, today: LocalDate, days: Int = 2): Boolean =
        isOverdue(task, today) || isDueSoon(task, today, days)

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
            TaskFilter.TODO -> category(task, today) == TaskCategory.TODO
            TaskFilter.IN_PROGRESS -> category(task, today) == TaskCategory.IN_PROGRESS
            TaskFilter.DONE -> category(task, today) == TaskCategory.DONE
            TaskFilter.OVERDUE -> category(task, today) == TaskCategory.OVERDUE
        }
        statusOk && (!mineOnly || task.assigneeId == myId)
    }

    /** 미완료 → 마감 빠른 순 → 우선순위 높은 순, 완료는 맨 아래 */
    fun sortForList(tasks: List<Task>): List<Task> =
        tasks.sortedWith(
            compareBy<Task>({ it.status == TaskStatus.DONE }, { it.dueDate }, { it.priority.ordinal })
        )
}
