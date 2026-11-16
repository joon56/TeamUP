package com.modo.app.ui.task

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.modo.app.R
import com.modo.app.data.AlarmStore
import com.modo.app.data.AppData
import com.modo.app.data.ChatRepository
import com.modo.app.data.Member
import com.modo.app.data.MessageType
import com.modo.app.data.Priority
import com.modo.app.data.Project
import com.modo.app.data.Task
import com.modo.app.data.TaskStatus
import com.modo.app.data.TeamRepository
import com.modo.app.databinding.ActivityTaskEditBinding
import com.modo.app.logic.AlarmCodec
import com.modo.app.logic.AlarmOption
import com.modo.app.logic.AlarmRules
import com.modo.app.logic.TaskAlarm
import com.modo.app.logic.TaskRules
import com.modo.app.logic.WeekCalculator
import com.modo.app.notify.AlarmScheduler
import com.modo.app.notify.Feedback
import com.modo.app.notify.Notifier
import com.modo.app.ui.alarm.AlarmSetActivity
import com.modo.app.ui.common.Dialogs
import com.modo.app.ui.common.Fmt
import com.modo.app.ui.common.Items
import com.modo.app.ui.common.Labels
import com.modo.app.ui.common.applySystemBarsPadding
import com.modo.app.ui.common.colorOf
import com.modo.app.ui.common.ensureSignedIn
import com.modo.app.ui.common.failureToast
import com.modo.app.ui.common.toast
import com.modo.app.ui.files.AttachmentsEditor
import com.modo.app.ui.root.RootActivity
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 업무 추가 / 수정 화면.
 * - taskId 가 있으면 수정, 없으면 새 업무
 * - projectId 가 없으면(업무 탭에서 추가) 프로젝트를 선택창에서 고름
 */
class TaskEditActivity : AppCompatActivity() {

    /** 저장 안 한 변경이 있는지 비교하기 위한 입력값 묶음 */
    private data class Form(
        val projectId: String,
        val title: String,
        val description: String,
        val assigneeId: String?,
        val dueDate: LocalDate,
        val dueTime: LocalTime?,
        val priority: Priority,
        val status: TaskStatus,
        /** 첨부 파일 경로 (바뀌었는지만 비교) */
        val attachments: List<String> = emptyList(),
    )

    private lateinit var binding: ActivityTaskEditBinding
    private lateinit var initialForm: Form

    private val taskId: String? by lazy { intent.getStringExtra(EXTRA_TASK_ID) }
    private val extraProjectId: String? by lazy { intent.getStringExtra(EXTRA_PROJECT_ID) }

    private var savedState: Bundle? = null
    private var ready = false   // 데이터가 와서 화면 초기화를 마쳤는지
    private var closing = false // 저장·삭제·나가기로 닫는 중인지

    private var original: Task? = null // 수정 모드일 때 원래 업무
    private var projectLocked = true   // false 면 프로젝트를 고를 수 있음
    private var projectId = ""
    private var assigneeId: String? = null
    private var dueDate: LocalDate = LocalDate.now()
    private var dueTime: LocalTime? = null // null = 그날 끝(23:59)까지
    private lateinit var attachments: AttachmentsEditor

    /** 이 업무에 맞춘 알람 (없으면 null). 새 업무는 저장할 때 id가 정해지므로 그때 예약함 */
    private var alarm: TaskAlarm? = null

    /**
     * 알람 설정 시트를 띄우고 결과를 돌려받음.
     * 시트가 setResult(RESULT_OK, 인텐트)로 닫히면 인텐트에 담긴 알람을 꺼내 쓴다.
     */
    private val alarmSheet = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult // 취소하고 닫음
        val json = result.data?.getStringExtra(AlarmSetActivity.RESULT_ALARM).orEmpty()
        onAlarmChosen(AlarmCodec.alarmsFromJson(json).firstOrNull())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ensureSignedIn()) return
        enableEdgeToEdge()
        binding = ActivityTaskEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsPadding()
        savedState = savedInstanceState

        binding.topBar.title.setText(if (taskId == null) R.string.task_new_title else R.string.task_edit_title)
        binding.topBar.back.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.topBar.textAction.isVisible = true
        binding.topBar.textAction.setText(R.string.action_save)
        binding.topBar.textAction.setOnClickListener { save() }
        binding.saveButton.setOnClickListener { save() }
        attachments = AttachmentsEditor(
            this, binding.attachmentList, binding.attachButton, binding.attachStatus, "task.attach",
            projectId = { projectId }, onChanged = {},
        )
        binding.deleteButton.setOnClickListener { confirmDelete() }
        binding.postponeButton.setOnClickListener { postpone() }
        binding.dueInput.setOnClickListener { pickDue() }
        binding.dueLayout.setStartIconOnClickListener { pickDue() }
        binding.dueTimeInput.setOnClickListener { pickDueTime() }
        binding.dueTimeLayout.setStartIconOnClickListener { pickDueTime() }
        binding.dueTimeLayout.setEndIconOnClickListener {
            dueTime = null
            onDueChanged()
        }
        binding.statusGroup.setOnCheckedChangeListener { _, _ -> updateDueViews() }
        binding.projectInput.setOnClickListener { pickProject() }
        binding.assigneeInput.setOnClickListener { pickAssignee() }
        binding.alarmRow.setOnClickListener { openAlarmSheet() }
        setupKeyEvents()

        // 뒤로 가기: 저장 안 한 변경이 있으면 [저장 / 나가기 / 계속 작성] 중에 고름
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (ready && currentForm() != initialForm) {
                    Dialogs.confirmLeave(this@TaskEditActivity, onSave = { save() }, onDiscard = { close() })
                } else {
                    close()
                }
            }
        })

        // 데이터가 준비되면 한 번 화면을 채우고, 이후 변경(팀원 추가 등)은 선택 목록에만 반영
        TeamRepository.data.observe(this) { data ->
            if (closing) return@observe
            if (!ready) {
                if (isDataReady(data)) setup(data)
            } else {
                onDataChanged(data)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (!ready || closing) return
        // 제목·설명 EditText는 스스로 복원되고, 나머지 선택값만 저장 (화면 회전 대비)
        outState.putString(KEY_PROJECT, projectId)
        outState.putString(KEY_ASSIGNEE, assigneeId)
        outState.putLong(KEY_DUE, dueDate.toEpochDay())
        outState.putString(KEY_DUE_TIME, dueTime?.toString())
        outState.putString(KEY_PRIORITY, checkedPriority().name)
        outState.putString(KEY_STATUS, checkedStatus().name)
        outState.putString(KEY_ALARM, alarm?.let { AlarmCodec.alarmsToJson(listOf(it)) }.orEmpty())
    }

    /** 수정 모드는 해당 프로젝트의 업무 목록이 도착해야 준비 완료 */
    private fun isDataReady(data: AppData): Boolean {
        if (data.loading) return false
        val pid = extraProjectId ?: return true
        if (data.project(pid) == null) return true // 없는 프로젝트 → setup에서 안내 후 닫음
        return taskId == null || data.tasks.containsKey(pid)
    }

    private fun setup(data: AppData) {
        ready = true
        val today = LocalDate.now()
        val id = taskId
        val pid = extraProjectId

        // 1) 어떤 업무·프로젝트인지 정함
        when {
            id != null -> {
                val task = (if (pid != null) data.task(pid, id) else null)
                    ?: data.allTasks.firstOrNull { it.id == id }
                if (task == null) {
                    toast(R.string.task_not_found)
                    close()
                    return
                }
                original = task
                projectLocked = true
                projectId = task.projectId
            }
            pid != null -> {
                if (data.project(pid) == null) {
                    toast(R.string.project_not_found)
                    close()
                    return
                }
                projectLocked = true
                projectId = pid
            }
            else -> {
                if (data.projects.isEmpty()) {
                    toast(R.string.error_no_project)
                    close()
                    return
                }
                projectLocked = false
                // 기본값: 아직 끝나지 않은 첫 프로젝트
                projectId = (data.projects.firstOrNull { !it.endDate.isBefore(today) } ?: data.projects.first()).id
            }
        }
        val project = data.project(projectId)
        if (project == null) {
            toast(R.string.project_not_found)
            close()
            return
        }

        // 2) 처음 값 (저장 안 한 변경 비교 기준)
        val task = original
        initialForm = if (task != null) {
            Form(
                task.projectId, task.title, task.description, task.assigneeId, task.dueDate, task.dueTime,
                task.priority, task.status, task.attachments.map { it.path },
            )
        } else {
            Form(
                projectId, intent.getStringExtra(EXTRA_TITLE).orEmpty(), "", intent.getStringExtra(EXTRA_ASSIGNEE),
                defaultDue(project, today), null, Priority.MEDIUM,
                TaskStatus.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_STATUS) } ?: TaskStatus.TODO,
            )
        }

        // 3) 화면에 채우기 (화면 회전 후라면 저장해 둔 선택값 우선)
        assigneeId = initialForm.assigneeId
        dueDate = initialForm.dueDate
        dueTime = initialForm.dueTime
        alarm = task?.let { AlarmStore.get(it.id) }
        var priority = initialForm.priority
        var status = initialForm.status
        val state = savedState
        if (state != null && state.containsKey(KEY_DUE)) {
            val savedProject = state.getString(KEY_PROJECT)
            if (!projectLocked && savedProject != null && data.project(savedProject) != null) projectId = savedProject
            assigneeId = state.getString(KEY_ASSIGNEE)
            dueDate = LocalDate.ofEpochDay(state.getLong(KEY_DUE))
            dueTime = state.getString(KEY_DUE_TIME)?.let { LocalTime.parse(it) }
            priority = Priority.entries.firstOrNull { it.name == state.getString(KEY_PRIORITY) } ?: priority
            status = TaskStatus.entries.firstOrNull { it.name == state.getString(KEY_STATUS) } ?: status
            alarm = AlarmCodec.alarmsFromJson(state.getString(KEY_ALARM)).firstOrNull()
        } else if (task != null) {
            binding.titleInput.setText(task.title)
            binding.descInput.setText(task.description)
        } else {
            // 채팅 메시지에서 "할 일로 만들기": 메시지 내용으로 제목을 채움
            binding.titleInput.setText(initialForm.title)
        }
        binding.priorityGroup.check(priorityButton(priority))
        binding.statusGroup.check(statusButton(status))

        binding.deleteButton.isVisible = task != null
        attachments.set(task?.attachments.orEmpty())
        binding.projectCard.isVisible = projectLocked
        binding.projectPickerLayout.isVisible = !projectLocked
        refreshProjects(data)
        refreshAssignee(data)
        updateDueViews()
    }

    /** 초기화 이후 데이터가 바뀌면: 삭제됐는지 확인하고 표시만 갱신 (입력 중인 값은 유지) */
    private fun onDataChanged(data: AppData) {
        if (data.loading) return
        if (data.project(projectId) == null) {
            toast(R.string.project_not_found)
            close()
            return
        }
        val task = original
        if (task != null && data.tasks.containsKey(projectId) && data.task(projectId, task.id) == null) {
            toast(R.string.task_not_found) // 다른 팀원이 삭제함
            close()
            return
        }
        refreshProjects(data)
        refreshAssignee(data)
        updateDueViews()
    }

    /** 새 업무 마감일 기본값: 3일 뒤 (프로젝트 기간 안으로 맞춤) */
    private fun defaultDue(project: Project, today: LocalDate): LocalDate {
        var due = today.plusDays(3)
        if (due.isAfter(project.endDate)) due = project.endDate
        if (due.isBefore(project.startDate)) due = project.startDate
        return due
    }

    private fun refreshProjects(data: AppData) {
        val project = data.project(projectId) ?: return
        if (projectLocked) {
            binding.projectName.text = project.name
            Items.tintIconBox(binding.projectIcon, colorOf(Labels.projectColor(project.colorIndex)))
        } else {
            binding.projectInput.setText(project.name)
        }
    }

    private fun refreshAssignee(data: AppData) {
        binding.assigneeInput.setText(Labels.assignee(this, data, projectId, assigneeId))
    }

    /** 프로젝트 고르기: 하나만 고르는 선택창 */
    private fun pickProject() {
        if (!ready) return
        val projects = TeamRepository.current.projects
        Dialogs.choose(this, R.string.label_project, projects.map { it.name }, projects.indexOfFirst { it.id == projectId }) { index ->
            val chosen = projects[index]
            if (chosen.id == projectId) return@choose
            projectId = chosen.id
            assigneeId = null // 프로젝트가 바뀌면 담당자는 다시 고름
            refreshProjects(TeamRepository.current)
            refreshAssignee(TeamRepository.current)
            updateDueViews()
        }
    }

    /** 담당자 고르기: 담당자 없음 + 선택한 프로젝트의 팀원 중 하나 */
    private fun pickAssignee() {
        if (!ready) return
        val data = TeamRepository.current
        val choices: List<Member?> = listOf<Member?>(null) + data.membersOf(projectId)
        val labels = choices.map { member ->
            when {
                member == null -> getString(R.string.none_assignee)
                member.id == data.myUid -> getString(R.string.name_with_me, member.name)
                else -> member.name
            }
        }
        Dialogs.choose(this, R.string.label_assignee, labels, choices.indexOfFirst { it?.id == assigneeId }) { index ->
            assigneeId = choices[index]?.id
            refreshAssignee(TeamRepository.current)
        }
    }

    /** 마감 시각(밀리초). 시간을 안 정했으면 그날 23:59 */
    private fun dueAt(): Long = AlarmRules.dueAt(dueDate, dueTime, ZoneId.systemDefault())

    /** 마감일·시간 표시 + 몇 주차인지 + 지연 안내 + 알람 줄 */
    private fun updateDueViews() {
        if (!ready || closing) return
        val today = LocalDate.now()
        // 올해 날짜는 연도를 빼고 짧게
        binding.dueInput.setText(if (dueDate.year == today.year) Fmt.date(this, dueDate) else Fmt.dateFull(this, dueDate))
        binding.dueTimeInput.setText(dueTime?.let { Fmt.time(this, it) }.orEmpty())
        binding.dueTimeLayout.isEndIconVisible = dueTime != null
        val project = TeamRepository.current.project(projectId)
        binding.weekHint.text = if (project == null) "" else
            getString(R.string.task_week_hint, WeekCalculator.weekOf(project, dueDate))

        // 저장된 업무를 수정 중이고, 완료가 아닌데 마감일이 지났으면 지연 안내
        val overdue = original != null && checkedStatus() != TaskStatus.DONE && dueDate.isBefore(today)
        binding.overdueBanner.isVisible = overdue
        if (overdue) {
            binding.overdueText.text = getString(R.string.task_overdue_banner, -TaskRules.dDay(dueDate, today))
        }

        val current = alarm
        binding.alarmValue.text = if (current == null) {
            getString(R.string.task_alarm_none)
        } else {
            getString(R.string.task_alarm_set, Notifier.formatDue(this, current.triggerAt, long = true), Notifier.alarmLabel(this, current))
        }
        binding.alarmValue.setTextColor(colorOf(if (current == null) R.color.text_secondary else R.color.primary))
    }

    private fun pickDue() {
        if (!ready) return
        Dialogs.pickDate(this, dueDate) { picked ->
            dueDate = picked
            onDueChanged()
        }
    }

    /** 마감 시간: 시간 선택창(TimePickerDialog) */
    private fun pickDueTime() {
        if (!ready) return
        Dialogs.pickTime(this, dueTime ?: LocalTime.of(18, 0)) { picked ->
            dueTime = picked
            onDueChanged()
        }
    }

    /** 마감이 바뀌면 "마감 N분 전" 알람도 따라 옮김. 옮긴 시각이 이미 지났으면 알람을 해제 */
    private fun onDueChanged() {
        val current = alarm
        if (current != null) {
            alarm = AlarmRules.rebase(current, dueAt(), System.currentTimeMillis())
            if (alarm == null) toast(R.string.task_alarm_lost)
        }
        updateDueViews()
    }

    // ───────── 알람 ─────────

    private fun openAlarmSheet() {
        if (!ready) return
        val now = System.currentTimeMillis()
        when {
            checkedStatus() == TaskStatus.DONE -> toast(R.string.task_alarm_done_hint)
            dueAt() <= now -> toast(R.string.task_alarm_past_hint)
            else -> {
                val project = TeamRepository.current.project(projectId)
                val title = binding.titleInput.text.toString().trim().ifEmpty { getString(R.string.task_new_title) }
                // 시트에 넘길 내용: 업무 정보 + (있으면) 지금 알람
                val draft = (alarm ?: TaskAlarm(AlarmSetActivity.DRAFT_ID, projectId, title, "", dueAt(), 0L, AlarmOption.CUSTOM)).copy(
                    taskId = taskId ?: AlarmSetActivity.DRAFT_ID,
                    projectId = projectId, title = title, projectName = project?.name.orEmpty(), dueAt = dueAt(),
                )
                alarmSheet.launch(AlarmSetActivity.intent(this, draft, hasAlarm = alarm != null))
            }
        }
    }

    /** 시트에서 돌아옴: picked == null 이면 알람 해제 */
    private fun onAlarmChosen(picked: TaskAlarm?) {
        alarm = picked
        // 이미 저장된 업무는 바로 예약하고, 새 업무는 저장할 때 예약
        original?.let { applyAlarm(it.id) }
        if (picked == null) {
            toast(R.string.alarm_removed)
        } else {
            toast(getString(R.string.alarm_saved, Notifier.formatDue(this, picked.triggerAt, long = true)))
        }
        updateDueViews()
    }

    /** 화면의 알람을 기기에 저장하고 예약 (없으면 예약 취소) */
    private fun applyAlarm(id: String) {
        val current = alarm
        val project = TeamRepository.current.project(projectId)
        if (current == null || checkedStatus() == TaskStatus.DONE || current.triggerAt <= System.currentTimeMillis()) {
            AlarmScheduler.clear(this, id)
        } else {
            val title = binding.titleInput.text.toString().trim()
            AlarmScheduler.set(
                this,
                current.copy(taskId = id, projectId = projectId, title = title, projectName = project?.name.orEmpty(), dueAt = dueAt()),
            )
        }
    }

    /** 다음 주로 미루기: 바로 저장되고 화면의 마감일도 바뀜 */
    private fun postpone() {
        val task = original ?: return
        val newDue = TeamRepository.postponeTask(task.copy(dueDate = dueDate), LocalDate.now(), failureToast())
        dueDate = newDue
        original = task.copy(dueDate = newDue)
        initialForm = initialForm.copy(dueDate = newDue) // 이미 저장됐으므로 변경으로 치지 않음
        onDueChanged()
        applyAlarm(task.id)
        toast(getString(R.string.msg_postponed, Fmt.date(this, newDue)))
    }

    private fun confirmDelete() {
        val task = original ?: return
        Dialogs.confirm(this, R.string.confirm_delete_task, null, R.string.action_delete) {
            TeamRepository.deleteTask(task, failureToast())
            AlarmScheduler.clear(this, task.id)
            toast(R.string.msg_deleted)
            close()
        }
    }

    private fun save() {
        if (!ready || closing) return
        val form = currentForm()
        if (form.title.isEmpty()) {
            binding.titleLayout.error = getString(R.string.error_task_title)
            binding.titleInput.requestFocus()
            return
        }
        binding.titleLayout.error = null

        // 새 업무는 id를 비워 두면 TeamRepository가 만들어 줌
        val sourceRoom = intent.getStringExtra(EXTRA_SOURCE_ROOM)
        val sourceMessage = intent.getStringExtra(EXTRA_SOURCE_MESSAGE)
        val base = original ?: Task(
            id = "", projectId = form.projectId, title = form.title, dueDate = form.dueDate,
            sourceRoomId = sourceRoom, sourceMessageId = sourceMessage,
        )
        val task = base.copy(
            projectId = form.projectId,
            title = form.title,
            description = form.description,
            assigneeId = form.assigneeId,
            dueDate = form.dueDate,
            dueTime = form.dueTime,
            priority = form.priority,
            status = form.status,
            attachments = attachments.attachments,
        )
        val savedId = TeamRepository.saveTask(task, failureToast())
        attachments.commit(form.projectId, "task", savedId)
        applyAlarm(savedId)
        // 메시지에서 만든 업무는 채팅방에 "업무 등록" 알림을 남김
        if (original == null && sourceRoom != null) {
            ChatRepository.send(sourceRoom, MessageType.TASK, form.title, refId = savedId, onFailure = failureToast())
        }
        // 방금 완료로 바꿨으면 효과음 · 진동
        if (form.status == TaskStatus.DONE && original?.status != TaskStatus.DONE) Feedback.taskDone(this)
        toast(R.string.msg_saved)
        close()
    }

    private fun currentForm() = Form(
        projectId = projectId,
        title = binding.titleInput.text.toString().trim(),
        description = binding.descInput.text.toString().trim(),
        assigneeId = assigneeId,
        dueDate = dueDate,
        dueTime = dueTime,
        priority = checkedPriority(),
        status = checkedStatus(),
        attachments = attachments.attachments.map { it.path },
    )

    // ───────── RadioGroup ↔ 값 (when) ─────────

    private fun checkedPriority(): Priority = when (binding.priorityGroup.checkedRadioButtonId) {
        R.id.priorityHigh -> Priority.HIGH
        R.id.priorityLow -> Priority.LOW
        else -> Priority.MEDIUM
    }

    private fun priorityButton(priority: Priority): Int = when (priority) {
        Priority.HIGH -> R.id.priorityHigh
        Priority.MEDIUM -> R.id.priorityMedium
        Priority.LOW -> R.id.priorityLow
    }

    private fun checkedStatus(): TaskStatus = when (binding.statusGroup.checkedRadioButtonId) {
        R.id.statusInProgress -> TaskStatus.IN_PROGRESS
        R.id.statusDone -> TaskStatus.DONE
        else -> TaskStatus.TODO
    }

    private fun statusButton(status: TaskStatus): Int = when (status) {
        TaskStatus.TODO -> R.id.statusTodo
        TaskStatus.IN_PROGRESS -> R.id.statusInProgress
        TaskStatus.DONE -> R.id.statusDone
    }

    /** 키 이벤트: 제목 칸에서 Enter(키보드 [다음]) → 설명 칸으로 이동 */
    private fun setupKeyEvents() {
        binding.titleInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                binding.descInput.requestFocus()
                true
            } else {
                false
            }
        }
        binding.titleInput.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                if (event.action == KeyEvent.ACTION_DOWN) binding.descInput.requestFocus()
                true
            } else {
                false
            }
        }
    }

    private fun close() {
        closing = true
        // 저장하지 않고 나가면 이번에 올린 첨부는 지움 (저장했으면 commit에서 비워 둠)
        if (::attachments.isInitialized) attachments.discard()
        finish()
    }

    /** 알림을 눌러 이 화면만 떠 있는 경우(아래에 홈이 없음): 닫을 때 홈을 띄워 줌 */
    override fun finish() {
        if (isTaskRoot && ::binding.isInitialized) startActivity(Intent(this, RootActivity::class.java))
        super.finish()
    }

    override fun onDestroy() {
        if (::attachments.isInitialized) attachments.release()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PROJECT_ID = "project_id"
        const val EXTRA_TASK_ID = "task_id"

        private const val KEY_PROJECT = "te_project"
        private const val KEY_ASSIGNEE = "te_assignee"
        private const val KEY_DUE = "te_due"
        private const val KEY_DUE_TIME = "te_due_time"
        private const val KEY_PRIORITY = "te_priority"
        private const val KEY_STATUS = "te_status"
        private const val KEY_ALARM = "te_alarm"

        const val EXTRA_STATUS = "status"
        const val EXTRA_TITLE = "title"
        const val EXTRA_SOURCE_ROOM = "source_room"
        const val EXTRA_SOURCE_MESSAGE = "source_message"
        const val EXTRA_ASSIGNEE = "assignee"

        /** 채팅 메시지 → 할 일 (시안 4: 롱프레스 · 담당자 지정). assigneeId: 멘션된 팀원이 있으면 미리 골라 둠 */
        fun startFromMessage(context: Context, projectId: String, title: String, roomId: String, messageId: String, assigneeId: String?) {
            context.startActivity(
                Intent(context, TaskEditActivity::class.java)
                    .putExtra(EXTRA_PROJECT_ID, projectId)
                    .putExtra(EXTRA_TITLE, title.take(120))
                    .putExtra(EXTRA_SOURCE_ROOM, roomId)
                    .putExtra(EXTRA_SOURCE_MESSAGE, messageId)
                    .putExtra(EXTRA_ASSIGNEE, assigneeId)
            )
        }

        /** 업무 화면을 여는 인텐트 (알림 · 알람에서도 씀) */
        fun intent(context: Context, projectId: String?, taskId: String?, status: TaskStatus? = null): Intent =
            Intent(context, TaskEditActivity::class.java)
                .putExtra(EXTRA_PROJECT_ID, projectId)
                .putExtra(EXTRA_TASK_ID, taskId)
                .putExtra(EXTRA_STATUS, status?.name)

        /**
         * taskId == null 이면 새 업무. projectId == null 이면 화면에서 프로젝트를 고름.
         * status: 보드의 열에서 추가할 때 그 열의 상태로 시작
         */
        fun start(context: Context, projectId: String?, taskId: String?, status: TaskStatus? = null) {
            context.startActivity(intent(context, projectId, taskId, status))
        }
    }
}
