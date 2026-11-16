package com.modo.app.notify

import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresApi
import com.modo.app.R
import com.modo.app.data.Prefs

/** 소리 · 진동 피드백 */
object Feedback {

    /** 알람이 울릴 때 진동 패턴: 대기 0, 진동 500, 쉼 400, 진동 500, 쉼 1200 (밀리초) */
    val ALARM_PATTERN = longArrayOf(0, 500, 400, 500, 1200)

    /**
     * 진동 장치. API 31부터는 VibratorManager에서 꺼내고, 그 전에는 VIBRATOR_SERVICE를 쓴다.
     */
    fun vibrator(context: Context): Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            managerVibrator(context)
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

    /** API 31 이상에서만 호출할 수 있음 */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun managerVibrator(context: Context): Vibrator =
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator

    /** 짧게 한 번 (세기는 기기 기본값) */
    fun tick(context: Context, millis: Long = 40) {
        vibrator(context).vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    /** 패턴 진동. repeat: 되풀이할 위치(0 = 처음부터 계속), -1 = 한 번만 */
    fun pattern(context: Context, timings: LongArray, repeat: Int) {
        vibrator(context).vibrate(VibrationEffect.createWaveform(timings, repeat))
    }

    fun cancel(context: Context) {
        vibrator(context).cancel()
    }

    /** 업무를 완료로 체크했을 때: 앱에 들어 있는 효과음(res/raw) + 짧은 진동 */
    fun taskDone(context: Context) {
        if (!Prefs.doneFeedback) return
        val player = MediaPlayer.create(context.applicationContext, R.raw.task_done) ?: return
        player.setOnCompletionListener { it.release() }
        player.start()
        tick(context)
    }
}
