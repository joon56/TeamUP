package com.modo.app.logic

import java.util.Locale
import kotlin.random.Random

/**
 * 사용자 식별 태그 "이름#1234".
 * 같은 이름이어도 태그로 구분하고, 초대할 때는 이름#태그 또는 가입 이메일로 정확한 사람을 찾는다.
 */
object UserTag {

    sealed class Query {
        data class Handle(val name: String, val tag: String) : Query()
        data class Email(val email: String) : Query()
        object Invalid : Query()
    }

    private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    private val SPACES = Regex("\\s+")

    /** 1000~9999 */
    fun generate(random: Random = Random.Default): String = random.nextInt(1000, 10000).toString()

    fun isValidTag(tag: String): Boolean = tag.length == 4 && tag.all { it in '0'..'9' } && tag[0] != '0'

    /** 검색 문서 id에 쓰이므로 '/'와 '#'은 이름에 쓸 수 없다 */
    fun isValidName(name: String): Boolean = name.isNotBlank() && '/' !in name && '#' !in name

    fun normalizeName(name: String): String = name.trim().replace(SPACES, " ").lowercase(Locale.ROOT)

    /** userHandles 문서 id */
    fun handleKey(name: String, tag: String): String = normalizeName(name) + "#" + tag

    fun normalizeEmail(email: String): String = email.trim().lowercase(Locale.ROOT)

    fun display(name: String, tag: String): String = if (tag.isEmpty()) name else "$name#$tag"

    /** 초대 검색창 입력 해석: "유진#4821" 또는 이메일 */
    fun parse(raw: String): Query {
        val text = raw.trim()
        if (text.isEmpty()) return Query.Invalid
        if ('@' in text) {
            val email = normalizeEmail(text)
            return if (EMAIL.matches(email)) Query.Email(email) else Query.Invalid
        }
        val hash = text.lastIndexOf('#')
        if (hash <= 0) return Query.Invalid
        val name = text.substring(0, hash).trim().replace(SPACES, " ")
        val tag = text.substring(hash + 1).trim()
        if (!isValidName(name) || !isValidTag(tag)) return Query.Invalid
        return Query.Handle(name, tag)
    }
}
