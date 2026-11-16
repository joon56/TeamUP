package com.modo.app.notify

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ServiceCompat
import com.modo.app.ModoApp
import com.modo.app.logic.AlarmCodec
import com.modo.app.logic.AlarmRules
import com.modo.app.logic.TaskAlarm
import com.modo.app.ui.alarm.AlarmActivity

/**
 * 알람이 울리는 동안 소리와 진동을 내는 포그라운드 서비스.
 * 리시버는 금방 끝나야 해서 30초 동안 이어지는 재생은 서비스가 맡는다.
 * (수업 범위 밖: 앱이 꺼져 있어도 소리를 내려면 포그라운드 서비스가 꼭 필요해서 사용)
 *
 * - 울리는 동안: 지울 수 없는 알림(버튼 3개) + 화면이 꺼져 있으면 알람 화면
 * - 30초 동안 아무것도 안 누르면 멈추고, 완료 처리 전까지 10분 뒤 다시(최대 3회)
 */
class AlarmRingService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var player: AlarmPlayer
    private var current: TaskAlarm? = null
    private val timeout = Runnable { onTimeout() }

    override fun onCreate() {
        super.onCreate()
        player = AlarmPlayer(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RING -> {
                val alarm = AlarmCodec.alarmsFromJson(intent.getStringExtra(EXTRA_ALARM)).firstOrNull()
                if (alarm != null) ring(alarm) else if (current == null) stopSelf()
            }
            // 알림의 [알람 끄기] 버튼 (PendingIntent.getService)
            ACTION_STOP -> {
                current?.let { AlarmScheduler.clear(this, it.taskId) }
                finishRing()
            }
            // 완료 · 다시 알림을 눌러서 이미 정리가 끝난 경우: 소리만 멈춤
            ACTION_SILENCE -> finishRing()
            else -> if (current == null) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun ring(alarm: TaskAlarm) {
        if (current?.taskId == alarm.taskId) return // 같은 알람이 겹쳐서 온 경우
        current?.let { settle(it) }                  // 다른 알람이 울리던 중이면 그 알람은 안 받은 것으로 정리
        current = alarm
        ringingTaskId = alarm.taskId
        ringStartedAt = SystemClock.elapsedRealtime()

        ServiceCompat.startForeground(
            this, Notifier.ID_ALARM, Notifier.alarmRinging(this, alarm),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        Notifier.ensureSummary(this)
        player.play(alarm.soundKind, alarm.soundUri, loop = true)
        Feedback.pattern(this, Feedback.ALARM_PATTERN, repeat = 0)
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, AlarmRules.RING_SECONDS * 1000L)

        // 앱을 보고 있는 중이면 전체 화면 알림이 배너로만 뜨므로 알람 화면을 직접 연다
        if (ModoApp.isForeground) {
            try {
                startActivity(AlarmActivity.intent(this, alarm.taskId))
            } catch (e: Exception) {
                Log.w(TAG, "open alarm screen", e)
            }
        }
    }

    private fun onTimeout() {
        current?.let { settle(it) }
        finishRing()
    }

    /** 아무 버튼도 누르지 않고 끝난 알람: 다시 울릴 차례를 잡거나(최대 3회) 지움 */
    private fun settle(alarm: TaskAlarm) {
        val next = AlarmRules.nextRepeat(alarm, System.currentTimeMillis())
        if (next != null) AlarmScheduler.set(this, next) else AlarmScheduler.clear(this, alarm.taskId)
        Notifier.alarmMissed(this, alarm, willRepeat = next != null)
    }

    private fun finishRing() {
        handler.removeCallbacks(timeout)
        player.stop()
        Feedback.cancel(this)
        val wasRinging = current != null
        current = null
        ringingTaskId = null
        if (wasRinging) AppEvents.send(this, AppEvents.ACTION_ALARM_STOPPED) // 알람 화면을 닫으라는 신호
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        // 울리던 알림이 지워진 뒤에 묶음 대표 알림을 정리 (지워지는 데 잠깐 걸림)
        val app = applicationContext
        handler.postDelayed({ Notifier.pruneSummary(app) }, 500)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(timeout)
        player.stop()
        Feedback.cancel(this)
        ringingTaskId = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AlarmRingService"
        private const val ACTION_RING = "com.modo.app.action.RING"
        private const val ACTION_STOP = "com.modo.app.action.RING_STOP"
        private const val ACTION_SILENCE = "com.modo.app.action.RING_SILENCE"
        private const val EXTRA_ALARM = "alarm"

        /** 지금 울리고 있는 알람의 업무 id (없으면 null) */
        var ringingTaskId: String? = null
            private set

        /** 울리기 시작한 시각 (SystemClock.elapsedRealtime). 알람 화면의 "0:12 / 0:30" 표시용 */
        var ringStartedAt = 0L
            private set

        fun ringIntent(context: Context, alarm: TaskAlarm): Intent =
            Intent(context, AlarmRingService::class.java)
                .setAction(ACTION_RING)
                .putExtra(EXTRA_ALARM, AlarmCodec.alarmsToJson(listOf(alarm)))

        fun stopIntent(context: Context): Intent = Intent(context, AlarmRingService::class.java).setAction(ACTION_STOP)

        /** 울리는 중이면 소리만 멈춤 (알람 정리는 부른 쪽이 이미 했을 때) */
        fun silence(context: Context) {
            if (ringingTaskId == null) return
            try {
                context.startService(Intent(context, AlarmRingService::class.java).setAction(ACTION_SILENCE))
            } catch (e: IllegalStateException) {
                Log.w(TAG, "silence", e)
            }
        }
    }
}
