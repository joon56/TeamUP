package com.teamup.app.logic

import kotlin.random.Random

/** 프로젝트 초대 코드 (예: K7Q2MX) */
object InviteCode {
    /** 헷갈리는 글자(I, O, 0, 1) 제외 */
    private const val CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    const val LENGTH = 6

    fun generate(random: Random = Random.Default): String {
        val builder = StringBuilder()
        for (i in 0 until LENGTH) {
            builder.append(CHARS[random.nextInt(CHARS.length)])
        }
        return builder.toString()
    }

    /** 사용자가 입력한 코드 정리: 공백·하이픈 제거, 대문자 */
    fun normalize(input: String): String =
        input.trim().uppercase().replace(" ", "").replace("-", "")

    fun isValid(code: String): Boolean =
        code.length == LENGTH && code.all { it in CHARS }
}
