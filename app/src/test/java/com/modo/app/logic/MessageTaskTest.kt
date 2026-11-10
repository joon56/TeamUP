package com.modo.app.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageTaskTest {

    @Test
    fun title_dropsLeadingMentions() {
        assertEquals("발표 자료 부탁해요", MessageTask.title("@서연 발표 자료 부탁해요", listOf("서연")))
        assertEquals("발표 자료 부탁해요", MessageTask.title("@서연 @민수  발표 자료 부탁해요", listOf("민수", "서연")))
    }

    @Test
    fun title_handlesNamesWithSpaces_andPrefersLongestName() {
        assertEquals("slides", MessageTask.title("@Kim Minsu slides", listOf("Kim", "Kim Minsu")))
    }

    @Test
    fun title_keepsMentionsInTheMiddle_andUnknownLeadingNames() {
        assertEquals("자료는 @서연 이 정리", MessageTask.title("자료는 @서연 이 정리", listOf("서연")))
        assertEquals("@누구 확인", MessageTask.title("@누구 확인", listOf("서연")))
        assertEquals("@서연님 확인", MessageTask.title("@서연님 확인", listOf("서")))
    }

    @Test
    fun title_fallsBackToWholeText_whenOnlyMentions() {
        assertEquals("@서연", MessageTask.title("  @서연 ", listOf("서연")))
    }
}
