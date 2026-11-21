package com.modo.app.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.modo.app.data.AlarmStore
import com.modo.app.data.Prefs
import com.modo.app.logic.AlarmRules
import com.modo.app.logic.MeetingTimer
import com.modo.app.logic.TaskAlarm
import com.modo.app.ui.task.TaskEditActivity
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 정해진 시각에 [AlarmReceiver]를 깨우도록 시스템(AlarmManager)에 예약한다.
 * 앱이 꺼져 있어도 시스템이 방송을 보내 주므로 알람이 울린다.
 * (수업 범위 밖: 알람을 "정해진 시각"에 울리려면 AlarmManager가 꼭 필요해서 사용)
 */
object AlarmScheduler {

    const val ACTION_TASK_ALARM = "com.modo.app.action.TASK_ALARM"
    const val ACTION_DIGEST = "com.modo.app.action.DIGEST"
    const val ACTION_MEETING_TIMER = "com.modo.app.action.MEETING_TIMER"
    const val EXTRA_TASK_ID = "task_id"

    /** 방금 울리기 시작한 알람(리시버가 처리 중)으로 보는 시간 */
    private const val JUST_FIRED_MS = 60_000L

    private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    private fun manager(context: Context) = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** 정확한 시각에 울릴 수 있는지. Android 12(API 31)부터는 권한이 있어야 함 */
    fun canExact(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) manager(context).canScheduleExactAlarms() else true

    private fun taskIntent(context: Context, taskId: String): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(ACTION_TASK_ALARM)
            .setData(Uri.parse("modo://alarm/$taskId")) // 업무마다 다른 PendingIntent
            .putExtra(EXTRA_TASK_ID, taskId)
        return PendingIntent.getBroadcast(context, 0, intent, FLAGS)
    }

    private fun digestIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(context, 0, Intent(context, AlarmReceiver::class.java).setAction(ACTION_DIGEST), FLAGS)

    private fun timerIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(context, 0, Intent(context, AlarmReceiver::class.java).setAction(ACTION_MEETING_TIMER), FLAGS)

    fun schedule(context: Context, alarm: TaskAlarm) {
        val operation = taskIntent(context, alarm.taskId)
        if (canExact(context)) {
            // 알람 시계로 예약: 절전(Doze) 중에도 정확히 울리고 상태 바에 알람 아이콘이 뜸
            val show = PendingIntent.getActivity(
                context, 0,
                TaskEditActivity.intent(context, alarm.projectId, alarm.taskId).setData(Uri.parse("modo://task/${alarm.taskId}")),
                FLAGS,
            )
            manager(context).setAlarmClock(AlarmManager.AlarmClockInfo(alarm.triggerAt, show), operation)
        } else {
            manager(context).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.triggerAt, operation)
        }
    }

    fun cancel(context: Context, taskId: String) = manager(context).cancel(taskIntent(context, taskId))

    /** 기기에 저장 + 예약 */
    fun set(context: Context, alarm: TaskAlarm) {
        AlarmStore.put(alarm)
        schedule(context, alarm)
    }

    /** 저장한 알람을 지우고 예약도 취소 */
    fun clear(context: Context, taskId: String) {
        AlarmStore.remove(taskId)
        cancel(context, taskId)
    }

    /**
     * 저장해 둔 알람을 모두 다시 예약. 기기를 껐다 켜면 예약이 사라지므로 [BootReceiver]와 앱 시작 때 호출한다.
     * 꺼져 있는 동안 시각이 지나 버린 알람은 "놓친 알람" 알림으로 알려 주고 지운다.
     */
    fun rescheduleAll(context: Context) {
        val now = System.currentTimeMillis()
        for (alarm in AlarmStore.all()) {
            when {
                alarm.triggerAt > now -> schedule(context, alarm)
                alarm.taskId == AlarmRingService.ringingTaskId || now - alarm.triggerAt < JUST_FIRED_MS -> Unit
                else -> {
                    AlarmStore.remove(alarm.taskId)
                    Notifier.alarmMissed(context, alarm, willRepeat = false)
                }
            }
        }
        // 회의 타이머: 아직 안 끝났으면 다시 예약, 기기가 꺼져 있는 동안 끝나 버렸으면 지움
        Prefs.meetingTimer?.let { timer ->
            if (timer.endAt > now) scheduleTimer(context, timer) else if (now - timer.endAt >= JUST_FIRED_MS) Prefs.meetingTimer = null
        }
        scheduleDigest(context)
    }

    // ───────── 회의 타이머 ─────────

    /** 회의 타이머 시작: 기기에 저장하고, 끝나는 시각에 [AlarmReceiver]를 깨우도록 예약 (화면을 끄거나 앱을 나가 있어도 울림) */
    fun startTimer(context: Context, timer: MeetingTimer) {
        Prefs.meetingTimer = timer
        scheduleTimer(context, timer)
    }

    private fun scheduleTimer(context: Context, timer: MeetingTimer) {
        val operation = timerIntent(context)
        if (canExact(context)) {
            manager(context).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timer.endAt, operation)
        } else {
            manager(context).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timer.endAt, operation)
        }
    }

    /** 회의 타이머 멈춤: 저장한 것을 지우고 예약도 취소 */
    fun stopTimer(context: Context) {
        Prefs.meetingTimer = null
        manager(context).cancel(timerIntent(context))
    }

    /** 매일 한 번 오는 마감 알림(아침 목록)을 다음 시각에 예약. 설정에서 껐으면 취소 */
    fun scheduleDigest(context: Context) {
        val operation = digestIntent(context)
        if (!Prefs.alertOn(Prefs.Alert.DEADLINE)) {
            manager(context).cancel(operation)
            return
        }
        val next = AlarmRules.nextDaily(LocalDateTime.now(), Prefs.digestTime)
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // 정확한 알람을 쓸 수 있으면 정한 시각에, 아니면 시스템이 형편에 맞춰(조금 늦게) 보내 줌. 둘 다 절전 중에도 깨워 줌
        if (canExact(context)) {
            manager(context).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        } else {
            manager(context).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        }
    }

    /** 로그아웃: 이 기기의 알람 · 마감 알림 · 회의 타이머 예약을 모두 지움 */
    fun clearAll(context: Context) {
        for (alarm in AlarmStore.all()) cancel(context, alarm.taskId)
        AlarmStore.clear()
        Prefs.digestJson = ""
        manager(context).cancel(digestIntent(context))
        stopTimer(context)
    }
}
