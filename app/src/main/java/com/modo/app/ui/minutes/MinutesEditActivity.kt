package com.modo.app.ui.minutes

import android.Manifest
import android.animation.Animator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Bundle
import android.os.SystemClock
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.CheckedTextView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.appcompat.app.AlertDialog
import com.google.firebase.ai.type.QuotaExceededException
import com.modo.app.R
import com.modo.app.data.AiSummarizer
import com.modo.app.data.AppData
import com.modo.app.data.DataHub
import com.modo.app.data.Member
import com.modo.app.data.Minutes
import com.modo.app.data.MinutesSummary
import com.modo.app.data.Prefs
import com.modo.app.data.Project
import com.modo.app.data.Task
import com.modo.app.data.TeamRepository
import com.modo.app.databinding.ActivityMinutesEditBinding
import com.modo.app.databinding.ItemMinutesActionBinding
import com.modo.app.logic.ActionMatcher
import com.modo.app.logic.MinutesPrompt
import com.modo.app.logic.Schedule
import com.modo.app.logic.WeekCalculator
import com.modo.app.ui.common.Dialogs
import com.modo.app.ui.common.Fmt
import com.modo.app.ui.common.applySystemBarsPadding
import com.modo.app.ui.common.colorOf
import com.modo.app.ui.common.ensureSignedIn
import com.modo.app.ui.common.failureToast
import com.modo.app.ui.common.setBusy
import com.modo.app.ui.common.toast
import com.modo.app.ui.files.AttachmentsEditor
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 회의록 작성·수정. 메모를 쓰거나 녹음을 받아쓰고, AI로 요약해 결정 사항과 할 일 후보를 뽑아
 * 체크한 일을 보드에 업무로 등록한다. (화면을 돌려도 녹음이 끊기지 않게 회전은 직접 처리)
 */
class MinutesEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMinutesEditBinding
    private lateinit var projectId: String
    private var minutesId: String? = null
    private var original: Minutes? = null
    private var ready = false
    private var initialSnapshot = ""

    private var meetingDate: LocalDate = LocalDate.now()
    private val attendees = mutableSetOf<String>()
    private var summary: MinutesSummary? = null
    /** 체크를 푼 할 일 후보 (순번) */
    private val unchecked = mutableSetOf<Int>()

    private var speech: SpeechSession? = null
    private var summarizing = false
    private var aiAnimator: Animator? = null
    private lateinit var attachments: AttachmentsEditor

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else toast(R.string.minutes_mic_denied)
    }

    /** 화면이 꺼지는 바람에 녹음이 멈췄는지 (다시 켜지면 이어서 녹음) */
    private var pausedByScreenOff = false
    private var stoppedInBackgroundAt = 0L

    /**
     * 기기 상태 방송을 받는 리시버 (동적 등록).
     * 화면 켜짐 · 꺼짐과 배터리 방송은 매니페스트에 적어서는 받을 수 없고, 실행 중에 코드로 등록해야만 받는다.
     */
    private val deviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                // 화면이 꺼지면 마이크를 못 쓰므로 녹음이 멈춤 → 켜지면 이어서 하도록 표시해 둠
                Intent.ACTION_SCREEN_OFF -> {
                    val justStopped = SystemClock.elapsedRealtime() - stoppedInBackgroundAt < JUST_STOPPED_MS
                    if (speech?.isRunning == true || justStopped) pausedByScreenOff = true
                    stopRecording()
                }
                Intent.ACTION_SCREEN_ON -> resumeAfterScreenOn()
                // 배터리 부족 · 회복 · 충전기 연결 · 분리
                Intent.ACTION_BATTERY_LOW -> if (speech?.isRunning == true) {
                    binding.batteryWarning.setText(R.string.minutes_battery_low_now)
                    binding.batteryWarning.isVisible = true
                }
                Intent.ACTION_BATTERY_OKAY, Intent.ACTION_POWER_CONNECTED -> binding.batteryWarning.isVisible = false
                Intent.ACTION_POWER_DISCONNECTED -> if (speech?.isRunning == true) checkBattery()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ensureSignedIn()) return
        projectId = intent.getStringExtra(EXTRA_PROJECT_ID) ?: return finish()
        minutesId = intent.getStringExtra(EXTRA_MINUTES_ID)
        enableEdgeToEdge()
        binding = ActivityMinutesEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsPadding()

        val bar = binding.topBar
        bar.title.setText(if (minutesId == null) R.string.minutes_new else R.string.minutes_title_screen)
        bar.back.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        if (minutesId != null) {
            bar.action1.isVisible = true
            bar.action1.setImageResource(R.drawable.ic_delete)
            bar.action1.contentDescription = getString(R.string.action_delete)
            bar.action1.setOnClickListener { confirmDelete() }
        }

        binding.dateInput.setOnClickListener {
            Dialogs.pickDate(this, meetingDate) {
                meetingDate = it
                renderDate()
                renderSummary()
            }
        }
        binding.titleInput.doAfterTextChanged { binding.titleLayout.error = null }
        binding.recordButton.setOnClickListener { toggleRecording() }
        binding.aiButton.setOnClickListener { summarize() }
        binding.addTasksButton.setOnClickListener { addTasks() }
        binding.saveButton.setOnClickListener { save(finishAfter = true) }
        attachments = AttachmentsEditor(
            this, binding.attachmentList, binding.attachButton, binding.attachStatus, "minutes.attach",
            projectId = { projectId }, onChanged = {},
        )

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                stopRecording()
                if (ready && snapshot() != initialSnapshot) {
                    Dialogs.confirm(
                        this@MinutesEditActivity, R.string.msg_discard_title, R.string.msg_discard_body, R.string.action_discard,
                    ) {
                        attachments.discard()
                        finish()
                    }
                } else {
                    finish()
                }
            }
        })

        DataHub.data.observe(this) { data ->
            if (!ready) {
                if (isDataReady(data)) setup(data)
            } else {
                renderAttendees(data)
                renderSummary()
            }
        }

        // 리시버 동적 등록. 화면이 꺼진 뒤(이 화면이 멈춘 뒤)에도 "다시 켜짐"을 받아야 해서
        // onStart/onStop이 아니라 onCreate에서 등록하고 onDestroy에서 해제한다
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_OKAY)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        ContextCompat.registerReceiver(this, deviceReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
    }

    private var receiverRegistered = false

    override fun onStart() {
        super.onStart()
        resumeAfterScreenOn() // 화면이 켜진 뒤에 이 화면이 다시 보이게 된 경우
    }

    override fun onStop() {
        super.onStop()
        // 백그라운드에서는 마이크를 못 쓰므로 멈춤
        if (speech?.isRunning == true) stoppedInBackgroundAt = SystemClock.elapsedRealtime()
        stopRecording()
    }

    override fun onDestroy() {
        if (receiverRegistered) unregisterReceiver(deviceReceiver) // 등록한 리시버는 꼭 해제
        aiAnimator?.cancel()
        speech?.stop()
        if (::attachments.isInitialized) attachments.release()
        super.onDestroy()
    }

    private fun isDataReady(data: AppData): Boolean =
        data.project(projectId) != null && data.members.containsKey(projectId) &&
            (minutesId == null || data.minutes.containsKey(projectId))

    private fun setup(data: AppData) {
        val project = data.project(projectId) ?: return finish()
        val existing = minutesId?.let { id -> data.minutesOf(projectId).firstOrNull { it.id == id } }
        if (minutesId != null && existing == null) {
            finish()
            return
        }
        original = existing
        val today = LocalDate.now()
        meetingDate = existing?.meetingDate ?: today
        attendees.clear()
        attendees += existing?.attendeeIds ?: data.membersOf(projectId).map { it.id }
        summary = existing?.summary
        binding.titleInput.setText(existing?.title ?: defaultTitle(project, today))
        binding.notesInput.setText(existing?.body.orEmpty())
        binding.transcriptInput.setText(existing?.transcript.orEmpty())
        attachments.set(existing?.attachments.orEmpty())
        renderDate()
        renderAttendees(data)
        renderSummary()
        initialSnapshot = snapshot()
        ready = true
    }

    /** "4주차 회의" (회의 요일로 나눈 주차) */
    private fun defaultTitle(project: Project, date: LocalDate): String =
        getString(R.string.minutes_default_title, WeekCalculator.weekOf(project, date).coerceAtLeast(1))

    private fun snapshot(): String = listOf(
        binding.titleInput.text.toString().trim(), binding.notesInput.text.toString().trim(),
        binding.transcriptInput.text.toString().trim(), meetingDate, attendees.sorted(), summary,
        attachments.attachments.map { it.path },
    ).joinToString("|")

    private fun renderDate() {
        binding.dateInput.setText(Fmt.dateFull(this, meetingDate))
    }

    /** 참석자 칩 (누르면 넣고 빼기) */
    private fun renderAttendees(data: AppData) {
        binding.attendeeChips.removeAllViews()
        val gap = resources.getDimensionPixelSize(R.dimen.gap_s)
        for ((index, member) in data.membersOf(projectId).withIndex()) {
            val chip = CheckedTextView(this, null, 0, R.style.Widget_Modo_ChipRadio).apply {
                text = member.name
                isChecked = member.id in attendees
                checkMarkDrawable = null
                setOnClickListener {
                    if (!attendees.remove(member.id)) attendees += member.id
                    isChecked = member.id in attendees
                }
            }
            val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            if (index > 0) params.marginStart = gap
            binding.attendeeChips.addView(chip, params)
        }
    }

    // ───────────────────────── 받아쓰기 ─────────────────────────

    private fun toggleRecording() {
        if (speech?.isRunning == true) {
            stopRecording()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecording()
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startRecording() {
        val session = speech ?: SpeechSession(
            this,
            onFinal = { appendTranscript(it) },
            onPartial = { binding.partialText.text = it },
            onFatal = { error ->
                stopRecording()
                toast(if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) R.string.minutes_mic_denied else R.string.minutes_stt_language)
            },
        ).also { speech = it }
        if (!session.isAvailable()) {
            toast(R.string.minutes_stt_unavailable)
            return
        }
        session.start()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding.recordButton.setText(R.string.minutes_record_stop)
        binding.recordButton.setIconResource(R.drawable.ic_stop)
        binding.partialText.isVisible = true
        binding.partialText.setText(R.string.minutes_listening)
        checkBattery()
    }

    private fun stopRecording() {
        if (speech?.isRunning != true) return
        speech?.stop()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding.recordButton.setText(R.string.minutes_record_start)
        binding.recordButton.setIconResource(R.drawable.ic_mic)
        binding.partialText.isVisible = false
        binding.batteryWarning.isVisible = false
    }

    /** 화면이 꺼져 멈췄던 녹음을, 화면이 켜지고 이 화면이 다시 보이면 이어서 시작 */
    private fun resumeAfterScreenOn() {
        if (!pausedByScreenOff || !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        pausedByScreenOff = false
        toggleRecording()
        if (speech?.isRunning == true) toast(R.string.minutes_record_resumed)
    }

    /**
     * 지금 배터리 상태를 읽어, 충전 중이 아닌데 얼마 안 남았으면 알려 줌 (회의 녹음은 오래 걸리므로).
     * ACTION_BATTERY_CHANGED는 마지막 값이 계속 남아 있는 방송(sticky)이라, 리시버 없이(null) 등록하면 그 값을 바로 돌려준다.
     */
    private fun checkBattery() {
        val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return
        val level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)   // 지금 남은 양
        val scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1)   // 가득 찼을 때의 값
        val status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1) // 충전 중 · 방전 중 · 가득 참
        val plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) // 0이면 전원에 연결 안 됨 (AC · USB · 무선)
        if (level < 0 || scale <= 0) return
        val percent = level * 100 / scale
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL || plugged != 0
        val low = !charging && percent <= LOW_BATTERY_PERCENT
        binding.batteryWarning.isVisible = low
        if (low) binding.batteryWarning.text = getString(R.string.minutes_battery_low, percent)
    }

    /** 확정된 문장을 받아쓰기 끝에 이어 붙임 */
    private fun appendTranscript(sentence: String) {
        val input = binding.transcriptInput
        val current = input.text.toString().trimEnd()
        input.setText(if (current.isEmpty()) sentence else "$current $sentence")
        input.setSelection(input.text.length)
        binding.partialText.setText(R.string.minutes_listening)
    }

    // ───────────────────────── AI 요약 ─────────────────────────

    private fun summarize() {
        if (summarizing) return
        val notes = binding.notesInput.text.toString()
        val transcript = binding.transcriptInput.text.toString()
        if (notes.isBlank() && transcript.isBlank()) {
            toast(R.string.minutes_ai_need_text)
            return
        }
        // 무료 요금제에서는 보낸 내용이 제품 개선에 쓰일 수 있어 처음 한 번 알림
        if (!Prefs.aiNoticeAccepted) {
            AlertDialog.Builder(this)
                .setTitle(R.string.minutes_ai_notice_title)
                .setMessage(R.string.minutes_ai_notice_body)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.minutes_ai_notice_ok) { _, _ ->
                    Prefs.aiNoticeAccepted = true
                    summarize()
                }
                .show()
            return
        }
        val data = DataHub.current
        val project = data.project(projectId) ?: return
        val members = data.membersOf(projectId)
        val prompt = MinutesPrompt.build(
            projectName = project.name, meetingDate = meetingDate,
            attendees = members.filter { it.id in attendees }.map { it.name }, members = members.map { it.name },
            notes = notes, transcript = transcript, today = LocalDate.now(),
        )
        setSummarizing(true)
        lifecycleScope.launch {
            try {
                summary = AiSummarizer.summarize(prompt)
                unchecked.clear()
                binding.aiHelp.setTextColor(colorOf(R.color.text_tertiary))
                binding.aiHelp.setText(R.string.minutes_ai_help)
            } catch (e: CancellationException) {
                throw e
            } catch (e: QuotaExceededException) {
                showAiError(R.string.minutes_ai_quota)
            } catch (e: Exception) {
                Log.w(TAG, "summarize", e)
                showAiError(R.string.minutes_ai_failed)
            } finally {
                setSummarizing(false)
                renderSummary()
            }
        }
    }

    private fun setSummarizing(running: Boolean) {
        summarizing = running
        aiAnimator = binding.aiProgress.setBusy(running, aiAnimator)
        binding.aiButton.isEnabled = !running
        if (running) binding.aiButton.setText(R.string.minutes_ai_running)
    }

    private fun showAiError(message: Int) {
        binding.aiHelp.setTextColor(colorOf(R.color.danger))
        binding.aiHelp.setText(message)
    }

    /** 요약 · 결정 사항 · 할 일 후보(체크) */
    private fun renderSummary() {
        if (!summarizing) binding.aiButton.setText(if (summary == null) R.string.minutes_ai_button else R.string.minutes_ai_again)
        val s = summary
        binding.overviewText.isVisible = !s?.overview.isNullOrBlank()
        binding.overviewText.text = s?.overview

        binding.decisionList.removeAllViews()
        binding.decisionsLabel.isVisible = !s?.decisions.isNullOrEmpty()
        s?.decisions?.forEach { decision ->
            val row = TextView(this)
            row.setTextAppearance(R.style.TextAppearance_Modo_Body)
            row.text = getString(R.string.bullet_item, decision)
            row.setPadding(0, resources.getDimensionPixelSize(R.dimen.gap_xs), 0, 0)
            binding.decisionList.addView(row)
        }

        binding.actionList.removeAllViews()
        val actions = s?.actions.orEmpty()
        binding.actionsLabel.isVisible = actions.isNotEmpty()
        binding.actionsHelp.isVisible = actions.isNotEmpty()
        val members = DataHub.current.membersOf(projectId)
        for ((index, action) in actions.withIndex()) {
            val row = ItemMinutesActionBinding.inflate(layoutInflater, binding.actionList, false)
            val added = action.taskId.isNotEmpty()
            row.title.text = action.title
            val assignee = ActionMatcher.memberId(action.assignee, members)?.let { id -> members.firstOrNull { it.id == id }?.name }
            val due = ActionMatcher.dueDate(action.due, meetingDate) ?: defaultDue()
            row.meta.text = if (added) getString(R.string.minutes_action_added)
            else getString(R.string.dot_join, assignee ?: getString(R.string.minutes_no_assignee), Fmt.dateShort(this, due))
            row.check.isChecked = added || index !in unchecked
            row.check.isEnabled = !added
            row.check.setOnCheckedChangeListener { _, checked ->
                if (checked) unchecked -= index else unchecked += index
                renderAddButton()
            }
            binding.actionList.addView(row.root)
        }
        renderAddButton()
    }

    private fun pendingActions(): List<Int> =
        summary?.actions.orEmpty().withIndex().filter { (i, a) -> a.taskId.isEmpty() && i !in unchecked }.map { it.index }

    private fun renderAddButton() {
        val count = pendingActions().size
        binding.addTasksButton.isVisible = count > 0
        binding.addTasksButton.text = getString(R.string.minutes_add_tasks, count)
    }

    /** 마감 표현이 없으면 다음 정기 회의 날, 회의가 안 정해졌으면 일주일 뒤 */
    private fun defaultDue(): LocalDate {
        val project = DataHub.current.project(projectId)?.takeIf { it.meetingDecided }
        val next = project?.let { Schedule.nextMeeting(it, LocalDateTime.now())?.toLocalDate() }
        return next ?: LocalDate.now().plusDays(7)
    }

    /** 체크한 할 일을 보드에 업무로 만들고, 어떤 일이 등록됐는지 회의록에도 저장 */
    private fun addTasks() {
        val s = summary ?: return
        val pending = pendingActions().toSet()
        if (pending.isEmpty()) return
        val title = binding.titleInput.text.toString().trim()
        // 회의록을 같이 저장해야 하므로 제목부터 확인 (업무만 생기고 연결이 안 남는 일 방지)
        if (title.isEmpty()) {
            binding.titleLayout.error = getString(R.string.minutes_title_required)
            binding.scroll.smoothScrollTo(0, 0)
            return
        }
        val members = DataHub.current.membersOf(projectId)
        val updated = s.actions.mapIndexed { i, action ->
            if (i !in pending) return@mapIndexed action
            val taskId = TeamRepository.saveTask(
                Task(
                    id = "", projectId = projectId, title = action.title,
                    description = getString(R.string.minutes_task_source, title),
                    assigneeId = ActionMatcher.memberId(action.assignee, members),
                    dueDate = ActionMatcher.dueDate(action.due, meetingDate) ?: defaultDue(),
                ),
                failureToast(),
            )
            action.copy(taskId = taskId)
        }
        summary = s.copy(actions = updated)
        save(finishAfter = false)
        toast(getString(R.string.minutes_tasks_added, pending.size))
        renderSummary()
    }

    // ───────────────────────── 저장 · 삭제 ─────────────────────────

    private fun save(finishAfter: Boolean) {
        if (!ready) return
        val title = binding.titleInput.text.toString().trim()
        if (title.isEmpty()) {
            binding.titleLayout.error = getString(R.string.minutes_title_required)
            binding.scroll.smoothScrollTo(0, 0)
            return
        }
        val base = original
        val minutes = Minutes(
            id = base?.id ?: minutesId.orEmpty(),
            projectId = projectId,
            title = title,
            meetingDate = meetingDate,
            body = binding.notesInput.text.toString().trim(),
            transcript = binding.transcriptInput.text.toString().trim(),
            summary = summary,
            attendeeIds = DataHub.current.membersOf(projectId).map { it.id }.filter { it in attendees },
            attachments = attachments.attachments,
            createdBy = base?.createdBy.orEmpty(),
            createdByName = base?.createdByName.orEmpty(),
            createdAt = base?.createdAt ?: 0,
        )
        val id = TeamRepository.saveMinutes(minutes, failureToast())
        attachments.commit(projectId, "minutes", id)
        // 새 회의록을 한 번 저장했으면 다음 저장부터는 같은 문서를 고침
        original = minutes.copy(
            id = id, createdAt = base?.createdAt ?: System.currentTimeMillis(),
            createdBy = base?.createdBy ?: DataHub.uid, createdByName = base?.createdByName ?: DataHub.current.myName,
        )
        minutesId = id
        initialSnapshot = snapshot()
        if (finishAfter) {
            toast(R.string.minutes_saved)
            finish()
        }
    }

    private fun confirmDelete() {
        val target = original ?: return
        Dialogs.confirm(this, R.string.minutes_delete_confirm, null, R.string.action_delete) {
            stopRecording()
            TeamRepository.deleteMinutes(target, failureToast())
            finish()
        }
    }

    companion object {
        private const val TAG = "MinutesEdit"
        private const val EXTRA_PROJECT_ID = "project_id"
        private const val EXTRA_MINUTES_ID = "minutes_id"
        private const val LOW_BATTERY_PERCENT = 20
        private const val JUST_STOPPED_MS = 3_000L

        /** minutesId == null 이면 새 회의록 */
        fun start(context: Context, projectId: String, minutesId: String? = null) {
            context.startActivity(
                Intent(context, MinutesEditActivity::class.java)
                    .putExtra(EXTRA_PROJECT_ID, projectId)
                    .putExtra(EXTRA_MINUTES_ID, minutesId)
            )
        }
    }
}
