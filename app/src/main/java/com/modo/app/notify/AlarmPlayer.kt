package com.modo.app.notify

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log
import com.modo.app.R
import com.modo.app.logic.AlarmSoundKind

/**
 * 알람 소리 재생기 (알람이 울릴 때 + 설정 화면의 미리 듣기).
 * - 기본 알람음: RingtoneManager로 기기의 알람음을 가져와 재생
 * - MODO 알람음: 앱에 들어 있는 res/raw/alarm_modo 를 MediaPlayer로 재생
 * - 내 노래: 사용자가 고른 음악 파일(Uri)을 MediaPlayer로 재생
 */
class AlarmPlayer(context: Context) {

    private val context = context.applicationContext
    private var player: MediaPlayer? = null
    private var ringtone: Ringtone? = null

    /** 알람 음량으로 재생 (무음 모드 · 미디어 음량과 무관) */
    private val attributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    val isPlaying: Boolean get() = player?.isPlaying == true || ringtone?.isPlaying == true

    /**
     * @param loop true면 [stop]을 부를 때까지 계속
     * @param onEnd 한 번 재생이 끝났을 때 (MediaPlayer만. 기본 알람음은 끝을 알려 주지 않음)
     */
    fun play(kind: AlarmSoundKind, uri: String, loop: Boolean, onEnd: () -> Unit = {}) {
        stop()
        when (kind) {
            AlarmSoundKind.MODO -> playBundled(loop, onEnd)
            AlarmSoundKind.SONG -> if (!playSong(uri, loop, onEnd)) playSystem(loop, onEnd)
            AlarmSoundKind.SYSTEM -> playSystem(loop, onEnd)
        }
    }

    private fun playBundled(loop: Boolean, onEnd: () -> Unit) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        // MediaPlayer.create: res/raw 파일을 읽어 재생 준비(prepare)까지 마친 플레이어를 돌려줌
        val created = MediaPlayer.create(context, R.raw.alarm_modo, attributes, audio.generateAudioSessionId()) ?: return
        created.isLooping = loop
        created.setOnCompletionListener { onEnd() }
        created.start()
        player = created
    }

    /** 고른 파일이 지워졌거나 읽을 권한이 사라졌으면 false */
    private fun playSong(uri: String, loop: Boolean, onEnd: () -> Unit): Boolean {
        if (uri.isEmpty()) return false
        val created = MediaPlayer()
        return try {
            created.setAudioAttributes(attributes)
            created.setDataSource(context, Uri.parse(uri))
            created.isLooping = loop
            created.setOnCompletionListener { onEnd() }
            created.prepare()
            created.start()
            player = created
            true
        } catch (e: Exception) {
            Log.w(TAG, "playSong", e)
            created.release()
            false
        }
    }

    private fun playSystem(loop: Boolean, onEnd: () -> Unit) {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val tone = uri?.let { RingtoneManager.getRingtone(context, it) }
        if (tone == null) {
            playBundled(loop, onEnd) // 기본 알람음이 없는 기기
            return
        }
        tone.audioAttributes = attributes
        // Ringtone 반복 재생은 API 28부터 지원 (그 전 기기에서는 한 번만 울림)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) tone.isLooping = loop
        tone.play()
        ringtone = tone
    }

    fun stop() {
        player?.let {
            try {
                if (it.isPlaying) it.stop()
            } catch (e: IllegalStateException) {
                Log.w(TAG, "stop", e)
            }
            it.release()
        }
        player = null
        ringtone?.stop()
        ringtone = null
    }

    private companion object {
        const val TAG = "AlarmPlayer"
    }
}
