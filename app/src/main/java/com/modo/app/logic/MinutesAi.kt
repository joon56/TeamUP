package com.modo.app.logic

import com.modo.app.data.ActionItem
import com.modo.app.data.Member
import com.modo.app.data.Minutes
import com.modo.app.data.MinutesSummary
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate

/** AI 회의록 요약에 보낼 요청문 */
object MinutesPrompt {
    const val MAX_NOTES = 8_000
    const val MAX_TRANSCRIPT = 20_000

    /** 담당자는 팀원 이름 중에서 고르게 하고, "금요일까지" 같은 말은 회의 날짜 기준으로 계산하게 함 */
    fun build(
        projectName: String,
        meetingDate: LocalDate,
        attendees: List<String>,
        members: List<String>,
        notes: String,
        transcript: String,
        today: LocalDate,
    ): String = buildString {
        appendLine("프로젝트: $projectName")
        appendLine("회의 날짜: $meetingDate (${meetingDate.dayOfWeek}), 오늘: $today")
        if (attendees.isNotEmpty()) appendLine("참석자: ${attendees.joinToString(", ")}")
        if (members.isNotEmpty()) appendLine("팀원 이름(담당자는 이 중에서): ${members.joinToString(", ")}")
        if (notes.isNotBlank()) {
            appendLine()
            appendLine("[회의 메모]")
            appendLine(notes.trim().take(MAX_NOTES))
        }
        if (transcript.isNotBlank()) {
            appendLine()
            appendLine("[녹음 받아쓰기]")
            // 길면 앞부분을 버림 (결론은 대개 끝에 나옴)
            appendLine(transcript.trim().takeLast(MAX_TRANSCRIPT))
        }
    }
}

/** Gemini가 돌려준 JSON → 회의록 요약. 코드 블록·빠진 필드·null을 견디고, 쓸 내용이 없으면 null */
object SummaryParser {

    fun parse(raw: String): MinutesSummary? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            val json = JSONObject(raw.substring(start, end + 1))
            val overview = json.text("summary")
            val decisions = json.optJSONArray("decisions")?.let { array ->
                (0 until array.length()).mapNotNull { i -> array.optString(i).trim().takeUnless { it.isEmpty() || array.isNull(i) } }
            }.orEmpty()
            val actions = (json.optJSONArray("actionItems") ?: json.optJSONArray("actions"))?.let { array ->
                (0 until array.length()).mapNotNull { i ->
                    val item = array.optJSONObject(i) ?: return@mapNotNull null
                    val task = item.text("task")
                    if (task.isEmpty()) null else ActionItem(task, item.text("assignee"), item.text("dueDate"))
                }
            }.orEmpty()
            if (overview.isEmpty() && decisions.isEmpty() && actions.isEmpty()) null
            else MinutesSummary(decisions, actions, overview)
        } catch (e: JSONException) {
            null
        }
    }

    /** 없거나 null이면 "" (org.json의 optString은 null을 "null"로 돌려줌) */
    private fun JSONObject.text(key: String): String = if (isNull(key)) "" else optString(key).trim()
}

/** AI가 고른 담당자 이름·마감 표현 → 팀원 id·날짜 */
object ActionMatcher {

    /** 같은 이름 → "님" 뗀 이름 → 한쪽이 다른 쪽을 포함하는 팀원이 한 명뿐일 때. 한 글자는 추측하지 않음 */
    fun memberId(assignee: String, members: List<Member>): String? {
        val name = assignee.trim().removeSuffix("님").trim()
        if (name.isEmpty()) return null
        members.firstOrNull { it.name == name }?.let { return it.id }
        if (name.length < 2) return null
        return members.filter { it.name.length >= 2 && (it.name.contains(name) || name.contains(it.name)) }.singleOrNull()?.id
    }

    private val monthDay = Regex("""(\d{1,2})\s*(?:/|\.|월)\s*(\d{1,2})""")

    /** "2026-11-14", "11/14", "11월 14일" → 날짜. 반년 넘게 지난 날짜면 내년으로 봄 */
    fun dueDate(raw: String, today: LocalDate): LocalDate? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        runCatching { return LocalDate.parse(text.take(10)) }
        val match = monthDay.find(text) ?: return null
        val (month, day) = match.destructured
        val date = runCatching { LocalDate.of(today.year, month.toInt(), day.toInt()) }.getOrNull() ?: return null
        return if (date.isBefore(today.minusMonths(6))) date.plusYears(1) else date
    }
}

/** 회의록 검색: 제목·메모·받아쓰기·요약·결정 사항·할 일에서 대소문자 구분 없이 */
object MinutesSearch {
    fun matches(minutes: Minutes, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        val summary = minutes.summary
        val fields = listOf(minutes.title, minutes.body, minutes.transcript, summary?.overview.orEmpty()) +
            summary?.decisions.orEmpty() + summary?.actions.orEmpty().map { it.title }
        return fields.any { it.contains(q, ignoreCase = true) }
    }
}
