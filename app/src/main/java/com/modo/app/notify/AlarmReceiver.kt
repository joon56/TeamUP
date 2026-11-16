package com.modo.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.modo.app.data.AlarmStore
import com.modo.app.data.DataHub
import com.modo.app.data.Prefs
import com.modo.app.data.TaskStatus
import com.modo.app.logic.AlarmCodec
import com.modo.app.logic.AlertRules
import java.time.LocalDate

/**
 * 예약한 시각이 되면 시스템(AlarmManager)이 보내는 방송을 받는 리시버.
 * 매니페스트에 등록(정적 등록)했기 때문에 앱이 꺼져 있어도 받는다.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AlarmScheduler.ACTION_TASK_ALARM -> onTaskAlarm(context, intent.getStringExtra(AlarmScheduler.EXTRA_TASK_ID) ?: return)
            AlarmScheduler.ACTION_DIGEST -> onDigest(context)
        }
    }

    /** 업무 알람: 소리와 진동은 오래 걸리는 일이라 포그라운드 서비스에 맡김 (리시버는 바로 끝나야 함) */
    private fun onTaskAlarm(context: Context, taskId: String) {
        val alarm = AlarmStore.get(taskId) ?: return
        // 앱이 켜져 있어 업무 상태를 알 수 있을 때: 이미 끝낸 업무면 울리지 않음
        val task = DataHub.current.task(alarm.projectId, taskId)
        if (task != null && task.status == TaskStatus.DONE) {
            AlarmStore.remove(taskId)
            return
        }
        ContextCompat.startForegroundService(context, AlarmRingService.ringIntent(context, alarm))
    }

    /** 아침 마감 알림: 기기에 저장해 둔 내 업무 목록으로 만들기 때문에 인터넷이 없어도 뜸 */
    private fun onDigest(context: Context) {
        if (Prefs.alertOn(Prefs.Alert.DEADLINE)) {
            Notifier.digest(context, AlertRules.digest(AlarmCodec.digestFromJson(Prefs.digestJson), LocalDate.now()))
        }
        AlarmScheduler.scheduleDigest(context) // 내일 같은 시각에 다시
    }
}
