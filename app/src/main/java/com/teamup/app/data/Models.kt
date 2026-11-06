package com.teamup.app.data

import java.time.LocalDate
import java.time.LocalTime

enum class TaskStatus { TODO, IN_PROGRESS, DONE }

enum class Priority { HIGH, MEDIUM, LOW }

data class Project(
    val id: String,
    val name: String,
    val description: String = "",
    val startDate: LocalDate,
    val endDate: LocalDate,
    val colorIndex: Int = 0,
    /** 정기 회의 요일. 1=월 ... 7=일 (java.time.DayOfWeek.value 와 같음) */
    val meetingDay: Int = 3,
    val meetingTime: LocalTime = LocalTime.of(19, 0),
    val inviteCode: String = "",
    val ownerUid: String = "",
    val memberUids: List<String> = emptyList(),
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

data class Member(
    /** 앱 사용자는 uid와 같고, 수동으로 추가한 팀원은 임의 id */
    val id: String,
    val projectId: String,
    val name: String,
    val role: String = "",
    /** null 이면 앱을 쓰지 않는(수동 추가) 팀원 */
    val uid: String? = null,
    val joinedAt: Long = 0,
)

data class Task(
    val id: String,
    val projectId: String,
    val title: String,
    val description: String = "",
    /** 담당자 Member.id. 담당자 없으면 null */
    val assigneeId: String? = null,
    val dueDate: LocalDate,
    val priority: Priority = Priority.MEDIUM,
    val status: TaskStatus = TaskStatus.TODO,
    val createdBy: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val completedAt: Long? = null,
)

/** 화면이 그릴 때 쓰는 전체 데이터 스냅샷. Firestore가 바뀔 때마다 새로 만들어짐 */
data class AppData(
    val myUid: String = "",
    val myName: String = "",
    val projects: List<Project> = emptyList(),
    /** projectId → 팀원 목록 */
    val members: Map<String, List<Member>> = emptyMap(),
    /** projectId → 업무 목록 */
    val tasks: Map<String, List<Task>> = emptyMap(),
    val loading: Boolean = true,
) {
    val allTasks: List<Task> get() = tasks.values.flatten()

    fun project(id: String?): Project? = projects.firstOrNull { it.id == id }
    fun membersOf(projectId: String): List<Member> = members[projectId].orEmpty()
    fun tasksOf(projectId: String): List<Task> = tasks[projectId].orEmpty()
    fun task(projectId: String, taskId: String): Task? = tasksOf(projectId).firstOrNull { it.id == taskId }

    fun member(projectId: String, memberId: String?): Member? {
        if (memberId == null) return null
        return membersOf(projectId).firstOrNull { it.id == memberId }
    }
}
