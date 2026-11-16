package com.modo.app.logic

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

/** 기기에 저장하는 알람 · 마감 알림 스냅샷을 JSON 문자열로 바꾸고 되돌림 */
object AlarmCodec {

    fun alarmsToJson(alarms: List<TaskAlarm>): String {
        val array = JSONArray()
        for (a in alarms) {
            array.put(
                JSONObject()
                    .put("taskId", a.taskId).put("projectId", a.projectId)
                    .put("title", a.title).put("projectName", a.projectName)
                    .put("dueAt", a.dueAt).put("triggerAt", a.triggerAt)
                    .put("option", a.option.name)
                    .put("soundKind", a.soundKind.name).put("soundUri", a.soundUri).put("soundName", a.soundName)
                    .put("repeat", a.repeat).put("repeatCount", a.repeatCount)
            )
        }
        return array.toString()
    }

    /** 깨진 항목은 건너뜀 (저장 형식이 바뀌어도 앱이 죽지 않게) */
    fun alarmsFromJson(json: String?): List<TaskAlarm> {
        if (json.isNullOrBlank()) return emptyList()
        val result = mutableListOf<TaskAlarm>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val taskId = o.optString("taskId")
                if (taskId.isEmpty()) continue
                result.add(
                    TaskAlarm(
                        taskId = taskId,
                        projectId = o.optString("projectId"),
                        title = o.optString("title"),
                        projectName = o.optString("projectName"),
                        dueAt = o.optLong("dueAt"),
                        triggerAt = o.optLong("triggerAt"),
                        option = AlarmOption.entries.firstOrNull { it.name == o.optString("option") } ?: AlarmOption.CUSTOM,
                        soundKind = AlarmSoundKind.entries.firstOrNull { it.name == o.optString("soundKind") } ?: AlarmSoundKind.SYSTEM,
                        soundUri = o.optString("soundUri"),
                        soundName = o.optString("soundName"),
                        repeat = o.optBoolean("repeat", true),
                        repeatCount = o.optInt("repeatCount"),
                    )
                )
            }
        } catch (e: Exception) {
            return result
        }
        return result
    }

    fun digestToJson(tasks: List<DigestTask>): String {
        val array = JSONArray()
        for (t in tasks) {
            array.put(
                JSONObject()
                    .put("taskId", t.taskId).put("projectId", t.projectId)
                    .put("title", t.title).put("projectName", t.projectName)
                    .put("dueDate", t.dueDate.toString()).put("dueTime", t.dueTime?.toString() ?: "")
                    .put("description", t.description)
            )
        }
        return array.toString()
    }

    fun digestFromJson(json: String?): List<DigestTask> {
        if (json.isNullOrBlank()) return emptyList()
        val result = mutableListOf<DigestTask>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val date = runCatching { LocalDate.parse(o.optString("dueDate")) }.getOrNull() ?: continue
                result.add(
                    DigestTask(
                        taskId = o.optString("taskId"),
                        projectId = o.optString("projectId"),
                        title = o.optString("title"),
                        projectName = o.optString("projectName"),
                        dueDate = date,
                        dueTime = o.optString("dueTime").takeIf { it.isNotEmpty() }?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
                        description = o.optString("description"),
                    )
                )
            }
        } catch (e: Exception) {
            return result
        }
        return result
    }
}
