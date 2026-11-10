package com.modo.app.logic

import com.modo.app.data.AppData
import com.modo.app.data.Member
import com.modo.app.data.Minutes
import com.modo.app.data.Notice
import com.modo.app.data.Task
import com.modo.app.data.TaskStatus

/** 진행률 중심 모드의 "최근 활동" 한 줄 */
sealed class ActivityEvent {
    abstract val at: Long

    data class NoticePosted(val notice: Notice) : ActivityEvent() {
        override val at: Long get() = notice.createdAt
    }

    data class MinutesWritten(val minutes: Minutes) : ActivityEvent() {
        override val at: Long get() = minutes.createdAt
    }

    data class TaskCompleted(val task: Task, val assigneeName: String?) : ActivityEvent() {
        override val at: Long get() = task.completedAt ?: 0L
    }

    data class MemberJoined(val member: Member) : ActivityEvent() {
        override val at: Long get() = member.joinedAt
    }
}

/** 공지·회의록·업무 완료·팀원 합류를 시간순으로 합친다 (별도 활동 기록 없이 있는 데이터에서) */
object ActivityFeed {

    fun build(data: AppData, projectId: String, limit: Int = 10): List<ActivityEvent> {
        val events = mutableListOf<ActivityEvent>()
        data.noticesOf(projectId).forEach { events.add(ActivityEvent.NoticePosted(it)) }
        data.minutesOf(projectId).forEach { events.add(ActivityEvent.MinutesWritten(it)) }
        for (task in data.tasksOf(projectId)) {
            if (task.status == TaskStatus.DONE && task.completedAt != null) {
                events.add(ActivityEvent.TaskCompleted(task, data.member(projectId, task.assigneeId)?.name))
            }
        }
        data.membersOf(projectId).forEach { events.add(ActivityEvent.MemberJoined(it)) }
        return events.filter { it.at > 0 }.sortedByDescending { it.at }.take(limit)
    }
}
