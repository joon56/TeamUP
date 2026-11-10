package com.modo.app.data

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.modo.app.logic.SummaryParser

/**
 * 회의록 AI 요약 (Firebase AI Logic → Gemini Developer API, Spark 무료).
 * 응답은 JSON 스키마로 받아 SummaryParser로 읽는다. 콘솔에서 AI Logic을 켜고 App Check 토큰을 등록해야 동작.
 */
object AiSummarizer {

    private const val MODEL = "gemini-3.8-flash"

    private val schema = Schema.obj(
        mapOf(
            "summary" to Schema.string(description = "회의 내용 3~5문장 요약"),
            "decisions" to Schema.array(Schema.string(), description = "회의에서 정해진 것"),
            "actionItems" to Schema.array(
                Schema.obj(
                    mapOf(
                        "task" to Schema.string(description = "해야 할 일 (짧은 문장)"),
                        "assignee" to Schema.string(description = "팀원 이름 중 하나", nullable = true),
                        "dueDate" to Schema.string(description = "YYYY-MM-DD", nullable = true),
                    ),
                    optionalProperties = listOf("assignee", "dueDate"),
                ),
                description = "회의에서 나온 할 일",
            ),
        )
    )

    private val model by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            },
            systemInstruction = content {
                text(
                    "너는 대학생 팀 프로젝트의 회의록 비서다. 회의 메모와 녹음 받아쓰기를 읽고 한국어로만 답한다. " +
                        "summary는 3~5문장, decisions는 정해진 것만, actionItems는 실제로 맡기로 한 일만 쓴다. " +
                        "assignee는 주어진 팀원 이름 중 하나이거나 null, dueDate는 회의 날짜 기준으로 계산한 YYYY-MM-DD이거나 null이다. " +
                        "내용에 없는 것은 지어내지 않는다."
                )
            },
        )
    }

    /** 요청문(MinutesPrompt.build) → 요약. 응답을 못 읽으면 IllegalStateException */
    suspend fun summarize(prompt: String): MinutesSummary {
        val text = model.generateContent(prompt).text ?: throw IllegalStateException("empty response")
        return SummaryParser.parse(text) ?: throw IllegalStateException("unreadable response")
    }
}
