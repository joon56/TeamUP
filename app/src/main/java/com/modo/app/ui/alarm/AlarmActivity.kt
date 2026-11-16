package com.modo.app.ui.alarm

import android.animation.Animator
import android.animation.AnimatorInflater
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnLayout
import com.modo.app.R
import com.modo.app.data.AlarmStore
import com.modo.app.databinding.ActivityAlarmBinding
import com.modo.app.logic.AlarmRules
import com.modo.app.logic.AlarmSoundKind
import com.modo.app.logic.TaskAlarm
import com.modo.app.notify.AlarmActions
import com.modo.app.notify.AlarmRingService
import com.modo.app.notify.AppEvents
import com.modo.app.notify.Notifier
import com.modo.app.ui.common.applySystemBarsPadding
import com.modo.app.ui.task.TaskEditActivity
import java.time.LocalTime

/**
 * 알람이 울릴 때 뜨는 전체 화면. 화면이 꺼져 있거나 잠겨 있어도 그 위에 뜬다.
 * 버튼: 완료로 표시 / 10분 뒤 다시 / 알람 끄기 / 업무 열기
 */
class AlarmActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmBinding
    private var alarm: TaskAlarm? = null
    private val animators = mutableListOf<Animator>()
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            showElapsed()
            handler.postDelayed(this, 500)
        }
    }

    /** 알람이 멈췄다는 방송(알림 버튼으로 끔 · 30초가 지남)을 받으면 화면을 닫음 */
    private val stoppedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        enableEdgeToEdge()
        binding = ActivityAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsPadding()

        binding.snooze.text = getString(R.string.alarm_action_snooze, AlarmRules.SNOOZE_MIN)
        binding.done.setOnClickListener { act { AlarmActions.done(applicationContext, it.taskId, it.projectId) } }
        binding.snooze.setOnClickListener { act { AlarmActions.snooze(applicationContext, it.taskId) } }
        binding.stop.setOnClickListener { act { AlarmActions.dismiss(applicationContext, it.taskId) } }
        binding.openTask.setOnClickListener { openTask() }

        bind(intent)
        startAnimations()
    }

    /** 이미 떠 있는데 다른 알람이 울리면(singleTask) 새 인텐트가 여기로 옴 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        bind(intent)
    }

    /**
     * 잠금 화면 위에 띄우고 화면을 켬.
     * API 27부터는 전용 메서드가 있고, 그 전에는 창(window) 플래그로 지정한다.
     */
    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun bind(intent: Intent) {
        val found = intent.getStringExtra(EXTRA_TASK_ID)?.let { AlarmStore.get(it) }
        if (found == null) {
            finish()
            return
        }
        alarm = found
        binding.chipText.text = getString(R.string.alarm_chip, Notifier.alarmLabel(this, found))
        binding.time.text = Notifier.formatTime(LocalTime.now())
        binding.title.text = found.title
        binding.subtitle.text = Notifier.alarmSubtitle(this, found)
        binding.soundName.text = when (found.soundKind) {
            AlarmSoundKind.SYSTEM -> getString(R.string.alarm_sound_system)
            AlarmSoundKind.MODO -> getString(R.string.alarm_sound_modo)
            AlarmSoundKind.SONG -> found.soundName.ifBlank { getString(R.string.alarm_sound_song) }
        }
        showElapsed()
    }

    /** "재생 중 · 0:12 / 0:30" */
    private fun showElapsed() {
        val seconds = ((SystemClock.elapsedRealtime() - AlarmRingService.ringStartedAt) / 1000).toInt().coerceIn(0, AlarmRules.RING_SECONDS)
        binding.soundStatus.text = getString(R.string.alarm_playing, AlarmRules.clock(seconds), AlarmRules.clock(AlarmRules.RING_SECONDS))
    }

    private fun startAnimations() {
        play(R.animator.alarm_pulse, binding.pulse, 0)
        // 막대는 아래쪽을 기준으로 늘어나야 하므로, 크기가 정해진 뒤 기준점(pivot)을 바닥으로 옮기고 시작
        for (i in 0 until binding.bars.childCount) {
            val bar = binding.bars.getChildAt(i)
            bar.doOnLayout {
                bar.pivotY = bar.height.toFloat()
                play(R.animator.eq_bar, bar, i * 110L)
            }
        }
    }

    private fun play(animator: Int, target: View, delayMs: Long) {
        val loaded = AnimatorInflater.loadAnimator(this, animator)
        loaded.setTarget(target)
        loaded.startDelay = delayMs
        loaded.start()
        animators.add(loaded)
    }

    /** 버튼 공통: 동작을 실행하고 화면을 닫음 */
    private fun act(action: (TaskAlarm) -> Unit) {
        alarm?.let(action)
        finish()
    }

    /** 업무 열기: 소리를 멈추고 업무 화면으로. 반복을 켜 뒀으면 완료 전까지 다음 차례에 다시 울림 */
    private fun openTask() {
        val current = alarm ?: return finish()
        AlarmActions.later(applicationContext, current.taskId)
        // 잠겨 있으면 잠금을 풀어야 업무 화면이 보임
        (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).requestDismissKeyguard(this, null)
        startActivity(TaskEditActivity.intent(this, current.projectId, current.taskId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    // 화면이 보이는 동안(onStart ~ onStop)에만 리시버를 등록(동적 등록)하고, 안 보이면 해제한다
    override fun onStart() {
        super.onStart()
        AppEvents.register(this, stoppedReceiver, AppEvents.ACTION_ALARM_STOPPED)
        // 화면이 안 보이는 사이에 알람이 이미 멈췄으면 닫음
        if (AlarmRingService.ringingTaskId != alarm?.taskId) {
            finish()
            return
        }
        handler.post(ticker)
    }

    override fun onStop() {
        AppEvents.unregister(this, stoppedReceiver)
        handler.removeCallbacks(ticker)
        super.onStop()
    }

    override fun onDestroy() {
        for (animator in animators) animator.cancel()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_TASK_ID = "task_id"

        fun intent(context: Context, taskId: String): Intent =
            Intent(context, AlarmActivity::class.java)
                .putExtra(EXTRA_TASK_ID, taskId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
    }
}
