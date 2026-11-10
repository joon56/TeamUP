package com.modo.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class UserTagTest {

    @Test
    fun generate_isFourDigitsWithoutLeadingZero() {
        val random = Random(42)
        repeat(200) {
            val tag = UserTag.generate(random)
            assertTrue(tag, UserTag.isValidTag(tag))
        }
        assertFalse(UserTag.isValidTag("0123"))
        assertFalse(UserTag.isValidTag("12a4"))
        assertFalse(UserTag.isValidTag("123"))
    }

    @Test
    fun handleKey_ignoresCaseAndExtraSpaces() {
        assertEquals("kim yujin#4821", UserTag.handleKey("  Kim   Yujin ", "4821"))
        assertEquals("유진#4821", UserTag.handleKey("유진", "4821"))
    }

    @Test
    fun parse_handle() {
        assertEquals(UserTag.Query.Handle("유진", "4821"), UserTag.parse(" 유진#4821 "))
        assertEquals(UserTag.Query.Handle("유 진", "4821"), UserTag.parse("유 진 # 4821"))
    }

    @Test
    fun parse_email_isLowercased() {
        assertEquals(UserTag.Query.Email("yujin@modo.test"), UserTag.parse("YuJin@Modo.Test"))
    }

    @Test
    fun parse_invalidInputs() {
        assertEquals(UserTag.Query.Invalid, UserTag.parse("유진"))
        assertEquals(UserTag.Query.Invalid, UserTag.parse("#4821"))
        assertEquals(UserTag.Query.Invalid, UserTag.parse("유진#48"))
        assertEquals(UserTag.Query.Invalid, UserTag.parse("a@b"))
        assertEquals(UserTag.Query.Invalid, UserTag.parse(""))
    }

    @Test
    fun isValidName_rejectsCharactersThatBreakSearch() {
        assertTrue(UserTag.isValidName("유진"))
        assertFalse(UserTag.isValidName("유/진"))
        assertFalse(UserTag.isValidName("유진#1"))
        assertFalse(UserTag.isValidName("   "))
    }

    @Test
    fun display_joinsNameAndTag() {
        assertEquals("유진#4821", UserTag.display("유진", "4821"))
        assertEquals("유진", UserTag.display("유진", ""))
    }
}
