package com.modo.app.notify

import android.content.Context
import android.widget.Toast
import com.modo.app.R
import com.modo.app.data.AlarmStore
import com.modo.app.data.TeamRepository
import com.modo.app.logic.AlarmRules

/** 알람이 울릴 때 할 수 있는 세 가지 동작. 알림 버튼([NotificationActionReceiver])과 알람 화면이 같이 쓴다 */
object AlarmActions {

    /** 완료로 표시: 업무를 완료 처리하고 알람을 지움. onSaved는 서버 반영이 끝났거나 잠시 기다린 뒤 호출 */
    fun done(context: Context, taskId: String, projectIdHint: String, onSaved: () -> Unit = {}) {
        val alarm = AlarmStore.get(taskId)
        val projectId = alarm?.projectId ?: projectIdHint
        AlarmScheduler.clear(context, taskId)
        Notifier.cancelAlarmNotices(context, taskId)
        AlarmRingService.silence(context)
        if (projectId.isEmpty()) {
            onSaved()
            return
        }
        TeamRepository.setTaskDone(projectId, taskId, onSaved)
        Feedback.taskDone(context)
        Toast.makeText(context, R.string.alarm_done_toast, Toast.LENGTH_SHORT).show()
    }

    /** 10분 뒤 다시 */
    fun snooze(context: Context, taskId: String) {
        val alarm = AlarmStore.get(taskId)
        if (alarm != null) {
            AlarmScheduler.set(context, AlarmRules.snooze(alarm, System.currentTimeMillis()))
            Toast.makeText(context, context.getString(R.string.alarm_snoozed_toast, AlarmRules.SNOOZE_MIN), Toast.LENGTH_SHORT).show()
        }
        Notifier.cancelAlarmNotices(context, taskId)
        AlarmRingService.silence(context)
    }

    /** 업무 열기: 지금은 소리만 멈춤. 반복을 켜 뒀으면 완료 전까지 다음 차례에 다시 울림 */
    fun later(context: Context, taskId: String) {
        val alarm = AlarmStore.get(taskId)
        if (alarm != null) {
            val next = AlarmRules.nextRepeat(alarm, System.currentTimeMillis())
            if (next != null) AlarmScheduler.set(context, next) else AlarmScheduler.clear(context, taskId)
        }
        AlarmRingService.silence(context)
    }

    /** 알람 끄기: 더 울리지 않음 (업무는 그대로) */
    fun dismiss(context: Context, taskId: String) {
        AlarmScheduler.clear(context, taskId)
        AlarmRingService.silence(context)
    }
}
