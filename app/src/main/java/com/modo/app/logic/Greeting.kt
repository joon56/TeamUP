package com.modo.app.logic

enum class Greeting { MORNING, AFTERNOON, EVENING, NIGHT }

/** 시간대별 인사 (홈 화면) */
fun greetingOf(hour: Int): Greeting = when (hour) {
    in 5..11 -> Greeting.MORNING
    in 12..17 -> Greeting.AFTERNOON
    in 18..21 -> Greeting.EVENING
    else -> Greeting.NIGHT
}
