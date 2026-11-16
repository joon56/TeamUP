package com.modo.app.ui.alarm

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Log
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.modo.app.R
import com.modo.app.data.Prefs
import com.modo.app.databinding.ActivityAlarmSetBinding
import com.modo.app.logic.AlarmCodec
import com.modo.app.logic.AlarmOption
import com.modo.app.logic.AlarmRules
import com.modo.app.logic.AlarmSoundKind
import com.modo.app.logic.TaskAlarm
import com.modo.app.notify.AlarmPlayer
import com.modo.app.notify.AlarmScheduler
import com.modo.app.notify.Notifier
import com.modo.app.ui.common.applySystemBarsPadding
import com.modo.app.ui.common.toast
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 업무 알람 설정 시트. 업무 화면이 띄우고, 고른 내용을 결과(setResult)로 돌려준다.
 * - 언제: RadioGroup (30분 전 · 1시간 전 · 3시간 전 · 직접 설정 → 날짜/시간 선택창)
 * - 소리: 기본 알람음 · MODO 알람음 · 내 노래(기기의 음악 파일을 골라 옴) + 미리 듣기
 * - 완료 처리 전까지 반복: CheckBox
 */
class AlarmSetActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmSetBinding
    private lateinit var draft: TaskAlarm
    private lateinit var player: AlarmPlayer
    private val handler = Handler(Looper.getMainLooper())
    private val stopPreview = Runnable { endPreview() }

    private var option: AlarmOption? = null
    private var customAt = 0L
    private var soundKind = AlarmSoundKind.SYSTEM
    private var soundUri = ""
    private var soundName = ""
    private var previewing: AlarmSoundKind? = null

    /** 음악 파일 고르기: 다른 앱(파일 선택 화면)을 띄우고 결과를 받아 옴 */
    private val songPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) onSongPicked(uri) else showSound()
    }

    /** 알림 권한 요청 (Android 13+). 허용하든 거절하든 알람은 저장하고 닫음 */
    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Prefs.notifPermissionAsked = true
        if (!granted) toast(R.string.alarm_no_notif_permission)
        finishWithAlarm()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val passed = AlarmCodec.alarmsFromJson(intent.getStringExtra(EXTRA_DRAFT)).firstOrNull()
        if (passed == null) {
            finish()
            return
        }
        draft = passed
        enableEdgeToEdge()
        binding = ActivityAlarmSetBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.content.applySystemBarsPadding(top = false)
        player = AlarmPlayer(this)

        val hasAlarm = intent.getBooleanExtra(EXTRA_HAS_ALARM, false)
        if (savedInstanceState != null) {
            // 화면이 회전돼 다시 만들어진 경우: 고르던 값을 되살림
            option = AlarmOption.entries.firstOrNull { it.name == savedInstanceState.getString(KEY_OPTION) }
            customAt = savedInstanceState.getLong(KEY_CUSTOM)
            soundKind = AlarmSoundKind.entries.firstOrNull { it.name == savedInstanceState.getString(KEY_SOUND) } ?: AlarmSoundKind.SYSTEM
            soundUri = savedInstanceState.getString(KEY_SOUND_URI).orEmpty()
            soundName = savedInstanceState.getString(KEY_SOUND_NAME).orEmpty()
        } else if (hasAlarm) {
            option = draft.option
            if (draft.option == AlarmOption.CUSTOM) customAt = draft.triggerAt
            soundKind = draft.soundKind
            soundUri = draft.soundUri
            soundName = draft.soundName
        } else {
            val available = AlarmRules.availableOptions(draft.dueAt, System.currentTimeMillis())
            option = listOf(AlarmOption.HOUR_1, AlarmOption.MIN_30, AlarmOption.HOUR_3).firstOrNull { it in available }
            val sound = Prefs.alarmSound
            soundKind = sound.kind
            soundUri = sound.uri
            soundName = sound.name
        }
        if (savedInstanceState == null) binding.repeat.isChecked = if (hasAlarm) draft.repeat else true

        binding.subtitle.text = getString(R.string.alarm_set_subtitle, draft.title, Notifier.formatDue(this, draft.dueAt, long = true))
        binding.repeatDesc.text = getString(R.string.alarm_repeat_desc, AlarmRules.REPEAT_INTERVAL_MIN, AlarmRules.MAX_REPEATS)
        binding.remove.isVisible = hasAlarm

        binding.scrim.setOnClickListener { cancel() }
        binding.close.setOnClickListener { cancel() }
        binding.save.setOnClickListener { save() }
        binding.remove.setOnClickListener {
            setResult(RESULT_OK, Intent().putExtra(RESULT_ALARM, ""))
            finish()
        }
        setupWhen()
        setupSound()
        showWhen()
        showSound()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_OPTION, option?.name)
        outState.putLong(KEY_CUSTOM, customAt)
        outState.putString(KEY_SOUND, soundKind.name)
        outState.putString(KEY_SOUND_URI, soundUri)
        outState.putString(KEY_SOUND_NAME, soundName)
    }

    // ───────────────────────── 언제 울릴까요 ─────────────────────────

    private fun setupWhen() {
        val available = AlarmRules.availableOptions(draft.dueAt, System.currentTimeMillis())
        // 이미 지난 시각의 선택지는 누를 수 없게
        binding.when30m.isEnabled = AlarmOption.MIN_30 in available
        binding.when1h.isEnabled = AlarmOption.HOUR_1 in available
        binding.when3h.isEnabled = AlarmOption.HOUR_3 in available
        binding.when30m.setOnClickListener { choose(AlarmOption.MIN_30) }
        binding.when1h.setOnClickListener { choose(AlarmOption.HOUR_1) }
        binding.when3h.setOnClickListener { choose(AlarmOption.HOUR_3) }
        binding.whenCustom.setOnClickListener { pickCustom() }
    }

    private fun choose(picked: AlarmOption) {
        option = picked
        showWhen()
    }

    private fun triggerAt(): Long? = option?.let { AlarmRules.triggerAt(draft.dueAt, it, customAt) }

    private fun showWhen() {
        when (option) {
            AlarmOption.MIN_30 -> binding.whenGroup.check(R.id.when30m)
            AlarmOption.HOUR_1 -> binding.whenGroup.check(R.id.when1h)
            AlarmOption.HOUR_3 -> binding.whenGroup.check(R.id.when3h)
            AlarmOption.CUSTOM -> binding.whenGroup.check(R.id.whenCustom)
            null -> binding.whenGroup.clearCheck()
        }
        val at = triggerAt()
        if (at == null) {
            binding.whenResult.setText(R.string.alarm_pick_first)
            binding.save.setText(R.string.alarm_save_plain)
        } else {
            binding.whenResult.text = getString(R.string.alarm_at, Notifier.formatDue(this, at, long = true))
            val time = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalTime()
            binding.save.text = getString(R.string.alarm_save, Notifier.formatTime(time))
        }
    }

    /** 직접 설정: 날짜 선택창(DatePickerDialog) → 시간 선택창(TimePickerDialog) 순서로 띄움 */
    private fun pickCustom() {
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val start = Instant.ofEpochMilli(if (customAt > now) customAt else minOf(draft.dueAt, now + 3_600_000L)).atZone(zone).toLocalDateTime()

        val dateDialog = DatePickerDialog(
            this,
            { _, year, month, day ->
                val timeDialog = TimePickerDialog(
                    this,
                    { _, hour, minute ->
                        val picked = LocalDateTime.of(year, month + 1, day, hour, minute).atZone(zone).toInstant().toEpochMilli()
                        when {
                            picked <= System.currentTimeMillis() -> toast(R.string.alarm_past)
                            picked > draft.dueAt -> toast(R.string.alarm_after_due)
                            else -> {
                                customAt = picked
                                option = AlarmOption.CUSTOM
                            }
                        }
                        showWhen()
                    },
                    start.hour, start.minute, true, // true = 24시간 표기
                )
                timeDialog.setOnCancelListener { showWhen() }
                timeDialog.show()
            },
            start.year, start.monthValue - 1, start.dayOfMonth,
        )
        // 고를 수 있는 날짜: 오늘부터 마감일까지
        dateDialog.datePicker.minDate = now - 1000
        dateDialog.datePicker.maxDate = maxOf(draft.dueAt, now)
        dateDialog.setOnCancelListener { showWhen() } // 취소하면 이전 선택으로 되돌림
        dateDialog.show()
    }

    // ───────────────────────── 알람 소리 ─────────────────────────

    private fun setupSound() {
        binding.soundSystem.setOnClickListener { chooseSound(AlarmSoundKind.SYSTEM) }
        binding.soundModo.setOnClickListener { chooseSound(AlarmSoundKind.MODO) }
        binding.soundSong.setOnClickListener { if (soundUri.isEmpty()) pickSong() else chooseSound(AlarmSoundKind.SONG) }
        binding.songHint.setOnClickListener { pickSong() }
        binding.previewSystem.setOnClickListener { togglePreview(AlarmSoundKind.SYSTEM) }
        binding.previewModo.setOnClickListener { togglePreview(AlarmSoundKind.MODO) }
        binding.previewSong.setOnClickListener { if (soundUri.isEmpty()) pickSong() else togglePreview(AlarmSoundKind.SONG) }
    }

    private fun chooseSound(kind: AlarmSoundKind) {
        soundKind = kind
        showSound()
    }

    /** 라디오 버튼 3개가 서로 다른 줄(레이아웃)에 있어 RadioGroup으로 묶을 수 없으므로 직접 하나만 체크함 */
    private fun showSound() {
        binding.soundSystem.isChecked = soundKind == AlarmSoundKind.SYSTEM
        binding.soundModo.isChecked = soundKind == AlarmSoundKind.MODO
        binding.soundSong.isChecked = soundKind == AlarmSoundKind.SONG
        if (soundUri.isEmpty()) {
            binding.soundSong.setText(R.string.alarm_sound_song)
            binding.songHint.setText(R.string.alarm_sound_song_pick)
        } else {
            binding.soundSong.text = getString(R.string.alarm_sound_song_named, soundName)
            binding.songHint.text = getString(R.string.alarm_sound_song_hint, AlarmRules.RING_SECONDS)
        }
    }

    private fun pickSong() {
        endPreview()
        // ACTION_OPEN_DOCUMENT: 기기의 파일 선택 화면을 띄움. 저장소 권한 없이 고른 파일 하나만 읽을 수 있음
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("audio/*")
        try {
            songPicker.launch(intent)
        } catch (e: Exception) {
            Log.w(TAG, "pickSong", e)
            toast(R.string.file_no_app)
        }
    }

    private fun onSongPicked(uri: Uri) {
        // 앱을 껐다 켜도(재부팅 뒤에도) 이 파일을 읽을 수 있도록 권한을 계속 유지
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            Log.w(TAG, "persist", e)
        }
        soundUri = uri.toString()
        soundName = displayName(uri)
        soundKind = AlarmSoundKind.SONG
        showSound()
    }

    private fun displayName(uri: Uri): String {
        var name = ""
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) name = cursor.getString(0).orEmpty()
        }
        return name.ifBlank { getString(R.string.alarm_sound_song) }
    }

    /** 미리 듣기: 다시 누르거나 8초가 지나면 멈춤 */
    private fun togglePreview(kind: AlarmSoundKind) {
        if (previewing == kind) {
            endPreview()
            return
        }
        endPreview()
        player.play(kind, soundUri, loop = false) { endPreview() }
        if (!player.isPlaying) {
            toast(R.string.alarm_song_failed)
            return
        }
        previewing = kind
        previewButton(kind).setImageResource(R.drawable.ic_stop)
        previewButton(kind).contentDescription = getString(R.string.alarm_preview_stop)
        handler.postDelayed(stopPreview, PREVIEW_MS)
    }

    private fun endPreview() {
        handler.removeCallbacks(stopPreview)
        player.stop()
        val kind = previewing ?: return
        previewing = null
        previewButton(kind).setImageResource(R.drawable.ic_play)
        previewButton(kind).contentDescription = getString(R.string.alarm_preview)
    }

    private fun previewButton(kind: AlarmSoundKind): ImageButton = when (kind) {
        AlarmSoundKind.SYSTEM -> binding.previewSystem
        AlarmSoundKind.MODO -> binding.previewModo
        AlarmSoundKind.SONG -> binding.previewSong
    }

    // ───────────────────────── 저장 · 닫기 ─────────────────────────

    private fun save() {
        val at = triggerAt()
        when {
            at == null -> toast(R.string.alarm_pick_first)
            at <= System.currentTimeMillis() -> toast(R.string.alarm_past)
            // Android 13(API 33)부터는 알림을 띄우려면 사용자에게 허용을 받아야 함
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifier.canPost(this) ->
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> finishWithAlarm()
        }
    }

    /** 고른 내용을 결과로 돌려주고 닫음. 실제 저장과 예약은 이 화면을 띄운 업무 화면이 한다 */
    private fun finishWithAlarm() {
        val at = triggerAt() ?: return
        val picked = option ?: return
        val alarm = draft.copy(
            triggerAt = at, option = picked,
            soundKind = soundKind, soundUri = soundUri, soundName = soundName,
            repeat = binding.repeat.isChecked, repeatCount = 0,
        )
        if (!AlarmScheduler.canExact(this)) toast(R.string.alarm_inexact)
        setResult(RESULT_OK, Intent().putExtra(RESULT_ALARM, AlarmCodec.alarmsToJson(listOf(alarm))))
        finish()
    }

    private fun cancel() {
        setResult(RESULT_CANCELED)
        finish()
    }

    override fun onStop() {
        if (::player.isInitialized) endPreview()
        super.onStop()
    }

    companion object {
        private const val TAG = "AlarmSetActivity"
        private const val EXTRA_DRAFT = "draft"
        private const val EXTRA_HAS_ALARM = "has_alarm"
        private const val KEY_OPTION = "as_option"
        private const val KEY_CUSTOM = "as_custom"
        private const val KEY_SOUND = "as_sound"
        private const val KEY_SOUND_URI = "as_sound_uri"
        private const val KEY_SOUND_NAME = "as_sound_name"
        private const val PREVIEW_MS = 8_000L

        /** 결과 인텐트에 담기는 알람(JSON). 빈 문자열이면 "알람 해제" */
        const val RESULT_ALARM = "alarm"

        /** 아직 저장 전인 새 업무는 id가 없으므로 임시 id를 씀 */
        const val DRAFT_ID = "draft"

        /**
         * @param draft 업무 정보(제목 · 프로젝트 · 마감)와, 이미 맞춘 알람이 있으면 그 내용
         * @param hasAlarm 이미 맞춘 알람을 고치는 것인지
         */
        fun intent(context: Context, draft: TaskAlarm, hasAlarm: Boolean): Intent =
            Intent(context, AlarmSetActivity::class.java)
                .putExtra(EXTRA_DRAFT, AlarmCodec.alarmsToJson(listOf(draft)))
                .putExtra(EXTRA_HAS_ALARM, hasAlarm)
    }
}
