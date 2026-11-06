package com.teamup.app.data

import java.time.LocalDate
import java.time.LocalTime

/**
 * 시연용 샘플 프로젝트 3개 (레퍼런스 화면의 데이터분석 팀플 / 앱 개발 프로젝트 / 마케팅 기획안).
 * 날짜는 모두 오늘 기준 상대값이라 언제 만들어도 진행 중인 프로젝트처럼 보인다.
 * 문구는 리소스(strings, arrays)에서 받아서 언어 설정을 따른다.
 */
object SampleData {

    data class Labels(
        val projectNames: List<String>,
        val projectDescriptions: List<String>,
        val leaderRole: String,
        /** 민수, 서연, 지훈, 하은 */
        val memberNames: List<String>,
        /** 프로젝트별 팀원 역할 (A: 2명, B: 3명, C: 2명 순서) */
        val memberRoles: List<String>,
        val tasksA: List<String>,
        val tasksB: List<String>,
        val tasksC: List<String>,
    )

    data class SampleProject(val project: Project, val members: List<Member>, val tasks: List<Task>)

    /** assignee: 0 = 나, 1 이상 = 해당 프로젝트 팀원 순서 */
    private class TaskSpec(val dayOffset: Long, val status: TaskStatus, val assignee: Int, val priority: Priority)

    private class ProjectSpec(
        val colorIndex: Int,
        val startOffset: Long,
        val endOffset: Long,
        val meetingOffset: Long,
        val meetingTime: LocalTime,
        /** memberNames 인덱스 목록 */
        val memberIndexes: List<Int>,
        val tasks: List<TaskSpec>,
    )

    // 이번 주(회의일 기준) 진행률이 레퍼런스처럼 75% / 40% / 20%가 되도록 날짜를 배치
    private val specs = listOf(
        ProjectSpec(
            // 데이터분석 팀플: 3일 전 회의 → 이번 주 = 오늘-3 ~ 오늘+3, 업무 4개 중 3개 완료(75%)
            colorIndex = 0, startOffset = -20, endOffset = 5, meetingOffset = -3, meetingTime = LocalTime.of(19, 0),
            memberIndexes = listOf(0, 1),
            tasks = listOf(
                TaskSpec(-18, TaskStatus.DONE, 0, Priority.MEDIUM),
                TaskSpec(-12, TaskStatus.DONE, 1, Priority.MEDIUM),
                TaskSpec(-2, TaskStatus.DONE, 1, Priority.HIGH),
                TaskSpec(-1, TaskStatus.DONE, 2, Priority.MEDIUM),
                TaskSpec(0, TaskStatus.DONE, 0, Priority.HIGH),
                TaskSpec(2, TaskStatus.IN_PROGRESS, 2, Priority.HIGH),
                TaskSpec(5, TaskStatus.TODO, 0, Priority.MEDIUM),
            ),
        ),
        ProjectSpec(
            // 앱 개발 프로젝트: 어제 회의 → 이번 주 업무 5개 중 2개 완료(40%), 지난주 업무 1개 지연
            colorIndex = 1, startOffset = -15, endOffset = 12, meetingOffset = -1, meetingTime = LocalTime.of(18, 0),
            memberIndexes = listOf(2, 0, 1),
            tasks = listOf(
                TaskSpec(-13, TaskStatus.DONE, 3, Priority.MEDIUM),
                TaskSpec(-9, TaskStatus.DONE, 2, Priority.MEDIUM),
                TaskSpec(-8, TaskStatus.DONE, 1, Priority.HIGH),
                TaskSpec(-3, TaskStatus.IN_PROGRESS, 1, Priority.HIGH),
                TaskSpec(-1, TaskStatus.DONE, 0, Priority.HIGH),
                TaskSpec(1, TaskStatus.IN_PROGRESS, 0, Priority.HIGH),
                TaskSpec(2, TaskStatus.DONE, 2, Priority.LOW),
                TaskSpec(4, TaskStatus.TODO, 1, Priority.MEDIUM),
                TaskSpec(5, TaskStatus.TODO, 3, Priority.MEDIUM),
            ),
        ),
        ProjectSpec(
            // 마케팅 기획안: 오늘 19:00 정기 회의 → 홈 "다가오는 일정"에 표시, 이번 주 5개 중 1개 완료(20%)
            colorIndex = 3, startOffset = -6, endOffset = 19, meetingOffset = 0, meetingTime = LocalTime.of(19, 0),
            memberIndexes = listOf(3, 2),
            tasks = listOf(
                TaskSpec(0, TaskStatus.DONE, 1, Priority.MEDIUM),
                TaskSpec(1, TaskStatus.IN_PROGRESS, 1, Priority.MEDIUM),
                TaskSpec(2, TaskStatus.TODO, 2, Priority.MEDIUM),
                TaskSpec(3, TaskStatus.TODO, 0, Priority.HIGH),
                TaskSpec(5, TaskStatus.TODO, 0, Priority.LOW),
            ),
        ),
    )

    fun build(
        myUid: String,
        myName: String,
        today: LocalDate,
        labels: Labels,
        newProjectId: () -> String,
        newCode: () -> String,
        time: Long,
    ): List<SampleProject> {
        val taskTitles = listOf(labels.tasksA, labels.tasksB, labels.tasksC)
        var roleIndex = 0
        val result = mutableListOf<SampleProject>()

        for ((index, spec) in specs.withIndex()) {
            val projectId = newProjectId()
            val project = Project(
                id = projectId,
                name = labels.projectNames[index],
                description = labels.projectDescriptions[index],
                startDate = today.plusDays(spec.startOffset),
                endDate = today.plusDays(spec.endOffset),
                colorIndex = spec.colorIndex,
                meetingDay = today.plusDays(spec.meetingOffset).dayOfWeek.value,
                meetingTime = spec.meetingTime,
                inviteCode = newCode(),
                ownerUid = myUid,
                memberUids = listOf(myUid),
                createdAt = time,
                updatedAt = time,
            )

            val members = mutableListOf(
                Member(id = myUid, projectId = projectId, name = myName, role = labels.leaderRole, uid = myUid, joinedAt = time)
            )
            for ((order, nameIndex) in spec.memberIndexes.withIndex()) {
                members.add(
                    Member(
                        id = "sample_${nameIndex}",
                        projectId = projectId,
                        name = labels.memberNames[nameIndex],
                        role = labels.memberRoles[roleIndex++],
                        uid = null,
                        joinedAt = time + order + 1,
                    )
                )
            }

            val tasks = mutableListOf<Task>()
            for ((taskIndex, taskSpec) in spec.tasks.withIndex()) {
                val done = taskSpec.status == TaskStatus.DONE
                tasks.add(
                    Task(
                        id = "sample_task_$taskIndex",
                        projectId = projectId,
                        title = taskTitles[index][taskIndex],
                        assigneeId = members[taskSpec.assignee].id,
                        dueDate = today.plusDays(taskSpec.dayOffset),
                        priority = taskSpec.priority,
                        status = taskSpec.status,
                        createdBy = myUid,
                        createdAt = time,
                        updatedAt = time - (spec.tasks.size - taskIndex) * 60_000L,
                        completedAt = if (done) time else null,
                    )
                )
            }
            result.add(SampleProject(project, members, tasks))
        }
        return result
    }
}
