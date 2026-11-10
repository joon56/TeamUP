package com.modo.app.logic

import com.modo.app.data.ActionItem
import com.modo.app.data.Member
import com.modo.app.data.Minutes
import com.modo.app.data.MinutesSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MinutesAiTest {

    // ── AI 응답(JSON) 해석 ──

    @Test
    fun parse_readsSummaryDecisionsAndActions() {
        val raw = """
            {"summary":"중간 발표 준비를 나눴다.","decisions":["발표는 목요일","자료는 PDF"],
             "actionItems":[{"task":"슬라이드 초안","assignee":"민수","dueDate":"2026-11-14"},
                            {"task":"자료 조사","assignee":null,"dueDate":null}]}
        """.trimIndent()
        val s = SummaryParser.parse(raw)!!
        assertEquals("중간 발표 준비를 나눴다.", s.overview)
        assertEquals(listOf("발표는 목요일", "자료는 PDF"), s.decisions)
        assertEquals(listOf(ActionItem("슬라이드 초안", "민수", "2026-11-14"), ActionItem("자료 조사", "", "")), s.actions)
    }

    @Test
    fun parse_toleratesCodeFencesMissingFieldsAndAlias() {
        val raw = "```json\n{\"decisions\":[\"주 1회 회의\"],\"actions\":[{\"task\":\"회의록 정리\"}]}\n```"
        val s = SummaryParser.parse(raw)!!
        assertEquals("", s.overview)
        assertEquals(listOf("주 1회 회의"), s.decisions)
        assertEquals(listOf(ActionItem("회의록 정리")), s.actions)
    }

    @Test
    fun parse_returnsNullForBrokenOrEmptyAnswers() {
        assertNull(SummaryParser.parse("요약할 내용이 없어요"))
        assertNull(SummaryParser.parse("{\"summary\": \"\", \"decisions\": []}"))
        assertNull(SummaryParser.parse("{\"summary\": \"끊긴"))
    }

    // ── 담당자 이름 → 팀원 ──

    private val members = listOf(
        Member(id = "u1", projectId = "p", name = "김민수"),
        Member(id = "u2", projectId = "p", name = "서연"),
        Member(id = "g1", projectId = "p", name = "이서연"),
    )

    @Test
    fun memberId_exactThenHonorificThenUniquePartial() {
        assertEquals("u2", ActionMatcher.memberId("서연", members))
        assertEquals("u1", ActionMatcher.memberId("김민수님", members))
        assertEquals("u1", ActionMatcher.memberId("민수", members))
        assertEquals("g1", ActionMatcher.memberId(" 이서연 ", members))
    }

    @Test
    fun memberId_nullWhenUnknownAmbiguousOrBlank() {
        assertNull(ActionMatcher.memberId("지훈", members))
        assertNull(ActionMatcher.memberId("", members))
        assertNull(ActionMatcher.memberId("연", members)) // 한 글자는 추측하지 않음
    }

    // ── 마감일 ──

    private val today = LocalDate.of(2026, 11, 11)

    @Test
    fun dueDate_readsIsoAndKoreanStyles() {
        assertEquals(LocalDate.of(2026, 11, 14), ActionMatcher.dueDate("2026-11-14", today))
        assertEquals(LocalDate.of(2026, 11, 20), ActionMatcher.dueDate("11/20", today))
        assertEquals(LocalDate.of(2026, 12, 3), ActionMatcher.dueDate("12월 3일까지", today))
        assertEquals(LocalDate.of(2027, 1, 5), ActionMatcher.dueDate("1/5", today)) // 해가 바뀜
        assertNull(ActionMatcher.dueDate("다음 주", today))
        assertNull(ActionMatcher.dueDate("13/40", today))
    }

    // ── 요청문 ──

    @Test
    fun prompt_includesContextAndKeepsTheEndOfLongTranscripts() {
        val transcript = "가".repeat(MinutesPrompt.MAX_TRANSCRIPT) + "마지막 결론"
        val prompt = MinutesPrompt.build(
            projectName = "데이터분석", meetingDate = today, attendees = listOf("서연", "김민수"),
            members = listOf("서연", "김민수", "이서연"), notes = "발표 순서 정함", transcript = transcript, today = today,
        )
        assertTrue(prompt.contains("데이터분석"))
        assertTrue(prompt.contains("서연, 김민수, 이서연"))
        assertTrue(prompt.contains("발표 순서 정함"))
        assertTrue(prompt.contains("마지막 결론"))
        assertTrue(prompt.length < MinutesPrompt.MAX_TRANSCRIPT + MinutesPrompt.MAX_NOTES + 1_000)
    }

    // ── 검색 ──

    @Test
    fun search_looksIntoNotesTranscriptAndSummary() {
        val m = Minutes(
            id = "m", projectId = "p", title = "4주차 회의", meetingDate = today, body = "Survey 문항 확정",
            transcript = "발표는 목요일에", summary = MinutesSummary(listOf("PDF로 제출"), listOf(ActionItem("슬라이드 초안"))),
        )
        assertTrue(MinutesSearch.matches(m, "4주차"))
        assertTrue(MinutesSearch.matches(m, "survey"))
        assertTrue(MinutesSearch.matches(m, "목요일"))
        assertTrue(MinutesSearch.matches(m, "pdf"))
        assertTrue(MinutesSearch.matches(m, "슬라이드"))
        assertTrue(MinutesSearch.matches(m, "  "))
        assertFalse(MinutesSearch.matches(m, "예산"))
    }
}
