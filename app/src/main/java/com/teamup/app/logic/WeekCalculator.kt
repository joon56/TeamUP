package com.teamup.app.logic

import com.teamup.app.data.Project
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 주차 계산. 주차 = 정기 회의일부터 다음 회의 전날까지.
 * 1주차만 예외로 프로젝트 시작일부터 두 번째 회의 전날까지.
 */
object WeekCalculator {

    /** 시작일(포함) 이후 첫 회의일. meetingDay: 1=월 ... 7=일 */
    fun firstMeeting(start: LocalDate, meetingDay: Int): LocalDate {
        require(meetingDay in 1..7) { "meetingDay must be 1..7" }
        var date = start
        while (date.dayOfWeek.value != meetingDay) {
            date = date.plusDays(1)
        }
        return date
    }

    /** date가 속한 주차 (1부터 시작). 시작일 이전 날짜는 1주차로 본다 */
    fun weekIndex(start: LocalDate, meetingDay: Int, date: LocalDate): Int {
        val days = ChronoUnit.DAYS.between(firstMeeting(start, meetingDay), date)
        return maxOf(1, (days / 7 + 1).toInt())
    }

    /** index주차의 첫날 ~ 마지막 날 */
    fun weekRange(start: LocalDate, meetingDay: Int, index: Int): ClosedRange<LocalDate> {
        val first = firstMeeting(start, meetingDay)
        val from = if (index == 1) start else first.plusWeeks((index - 1).toLong())
        val to = first.plusWeeks(index.toLong()).minusDays(1)
        return from..to
    }

    /** 프로젝트 마감일이 속한 주차 = 전체 주차 수 */
    fun totalWeeks(start: LocalDate, end: LocalDate, meetingDay: Int): Int =
        weekIndex(start, meetingDay, end)

    // Project를 받는 편의 함수
    fun weekOf(project: Project, date: LocalDate): Int =
        weekIndex(project.startDate, project.meetingDay, date)

    fun rangeOf(project: Project, index: Int): ClosedRange<LocalDate> =
        weekRange(project.startDate, project.meetingDay, index)
}
