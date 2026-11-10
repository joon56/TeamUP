package com.modo.app.logic

/** 채팅 메시지를 할 일로 만들 때 쓰는 규칙 */
object MessageTask {

    /**
     * 앞쪽 멘션을 뗀 제목. "@서연 @민수 발표 자료 부탁해요" → "발표 자료 부탁해요".
     * names: 이 메시지에서 멘션된 사람 이름 (이름에 공백이 있어도 되게 알려진 이름으로만 뗌)
     * 멘션뿐인 메시지는 원문을 그대로 쓴다.
     */
    fun title(text: String, names: Collection<String>): String {
        val whole = text.trim()
        var rest = whole
        while (rest.startsWith("@")) {
            val name = names.filter { it.isNotEmpty() && startsWithMention(rest, it) }.maxByOrNull { it.length } ?: break
            rest = rest.removePrefix("@$name").trimStart()
        }
        return rest.ifEmpty { whole }
    }

    /** "@서연 ..." 은 서연 멘션이지만 "@서연님" 의 "서" 는 아님 (이름 뒤는 공백이나 끝) */
    private fun startsWithMention(text: String, name: String): Boolean {
        val end = name.length + 1
        return text.startsWith("@$name") && (text.length == end || text[end].isWhitespace())
    }
}
