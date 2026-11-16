package com.modo.app.data

import com.modo.app.logic.AlarmCodec
import com.modo.app.logic.TaskAlarm

/**
 * 업무 알람 저장소. 알람은 이 기기에서만 울리므로 Firestore가 아니라 기기(Prefs)에 둔다.
 * 재부팅 뒤에는 네트워크 없이도 여기서 읽어 다시 예약한다.
 */
object AlarmStore {

    /** 한 번 읽은 목록 (목록 화면에서 업무마다 알람이 있는지 확인하므로 매번 다시 읽지 않게) */
    private var cache: List<TaskAlarm>? = null

    fun all(): List<TaskAlarm> = cache ?: AlarmCodec.alarmsFromJson(Prefs.alarmsJson).also { cache = it }

    fun get(taskId: String): TaskAlarm? = all().firstOrNull { it.taskId == taskId }

    fun has(taskId: String): Boolean = all().any { it.taskId == taskId }

    fun put(alarm: TaskAlarm) {
        val list = all().toMutableList()
        val index = list.indexOfFirst { it.taskId == alarm.taskId }
        if (index >= 0) list[index] = alarm else list.add(alarm)
        save(list)
    }

    fun remove(taskId: String) {
        val list = all()
        if (list.none { it.taskId == taskId }) return
        save(list.filter { it.taskId != taskId })
    }

    fun clear() = save(emptyList())

    private fun save(list: List<TaskAlarm>) {
        cache = list
        Prefs.alarmsJson = AlarmCodec.alarmsToJson(list)
    }
}
