package com.modo.app.logic

import com.modo.app.data.Member
import com.modo.app.data.Project
import com.modo.app.data.Task
import com.modo.app.data.TaskStatus
import java.time.LocalDate

data class WeekProgress(
    val index: Int,
    val range: ClosedRange<LocalDate>,
    val total: Int,
    val done: Int,
) {
    /** 업무가 없는 주차는 null ("업무 없음") */
    val rate: Int? get() = if (total == 0) null else done * 100 / total
}

data class MemberStat(val member: Member, val total: Int, val done: Int) {
    val rate: Int? get() = if (total == 0) null else done * 100 / total
}

/** 분류별 개수 ([TaskCategory] 기준이라 서로 겹치지 않음) */
data class StatusCounts(val todo: Int, val inProgress: Int, val done: Int, val overdue: Int) {
    val total: Int get() = todo + inProgress + done + overdue
}

object ProgressCalculator {

    /** 완료 비율 0~100 (내림). 업무가 없으면 null. 100%는 전부 완료일 때만 */
    fun rate(tasks: List<Task>): Int? {
        if (tasks.isEmpty()) return null
        val done = tasks.count { it.status == TaskStatus.DONE }
        return done * 100 / tasks.size
    }

    fun tasksInWeek(project: Project, tasks: List<Task>, index: Int): List<Task> =
        tasks.filter { WeekCalculator.weekOf(project, it.dueDate) == index }

    fun weekRate(project: Project, tasks: List<Task>, index: Int): Int? =
        rate(tasksInWeek(project, tasks, index))

    /** 1주차부터 마지막 주차(프로젝트 마감 또는 가장 늦은 업무)까지 주차별 진행률 */
    fun weeklyProgress(project: Project, tasks: List<Task>): List<WeekProgress> {
        var lastWeek = WeekCalculator.totalWeeks(project.startDate, project.endDate, project.meetingDay)
        for (task in tasks) {
            lastWeek = maxOf(lastWeek, WeekCalculator.weekOf(project, task.dueDate))
        }
        val result = mutableListOf<WeekProgress>()
        for (index in 1..lastWeek) {
            val inWeek = tasksInWeek(project, tasks, index)
            result.add(
                WeekProgress(
                    index = index,
                    range = WeekCalculator.rangeOf(project, index),
                    total = inWeek.size,
                    done = inWeek.count { it.status == TaskStatus.DONE },
                )
            )
        }
        return result
    }

    /** 팀원별 담당 업무 수와 완료 수 (담당자 없는 업무는 제외) */
    fun memberStats(members: List<Member>, tasks: List<Task>): List<MemberStat> =
        members.map { member ->
            val mine = tasks.filter { it.assigneeId == member.id }
            MemberStat(member, mine.size, mine.count { it.status == TaskStatus.DONE })
        }

    /** 분류별 개수. 마감이 지난 미완료 업무는 할 일·진행 중이 아니라 지연으로만 센다 */
    fun statusCounts(tasks: List<Task>, today: LocalDate): StatusCounts {
        var todo = 0
        var inProgress = 0
        var done = 0
        var overdue = 0
        for (task in tasks) {
            when (TaskRules.category(task, today)) {
                TaskCategory.TODO -> todo++
                TaskCategory.IN_PROGRESS -> inProgress++
                TaskCategory.DONE -> done++
                TaskCategory.OVERDUE -> overdue++
            }
        }
        return StatusCounts(todo, inProgress, done, overdue)
    }
}
