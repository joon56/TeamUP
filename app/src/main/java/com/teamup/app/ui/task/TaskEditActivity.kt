package com.teamup.app.ui.task

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.teamup.app.R
import com.teamup.app.data.AppData
import com.teamup.app.data.Member
import com.teamup.app.data.Priority
import com.teamup.app.data.Project
import com.teamup.app.data.Task
import com.teamup.app.data.TaskStatus
import com.teamup.app.data.TeamRepository
import com.teamup.app.databinding.ActivityTaskEditBinding
import com.teamup.app.logic.TaskRules
import com.teamup.app.logic.WeekCalculator
import com.teamup.app.ui.common.Dialogs
import com.teamup.app.ui.common.Fmt
import com.teamup.app.ui.common.Items
import com.teamup.app.ui.common.Labels
import com.teamup.app.ui.common.applySystemBarsPadding
import com.teamup.app.ui.common.colorOf
import com.teamup.app.ui.common.ensureSignedIn
import com.teamup.app.ui.common.failureToast
import com.teamup.app.ui.common.toast
import java.time.LocalDate

/**
 * 업무 추가 / 수정 화면.
 * - taskId 가 있으면 수정, 없으면 새 업무
 * - projectId 가 없으면(업무 탭에서 추가) 프로젝트를 드롭다운으로 고름
 */
class TaskEditActivity : AppCompatActivity() {

    /** 저장 안 한 변경이 있는지 비교하기 위한 입력값 묶음 */
    private data class Form(
        val projectId: String,
        val title: String,
        val description: String,
        val assigneeId: String?,
        val dueDate: LocalDate,
        val priority: Priority,
        val status: TaskStatus,
    )

    private lateinit var binding: ActivityTaskEditBinding
    private lateinit var initialForm: Form

    private val taskId: String? by lazy { intent.getStringExtra(EXTRA_TASK_ID) }
    private val extraProjectId: String? by lazy { intent.getStringExtra(EXTRA_PROJECT_ID) }

    private var savedState: Bundle? = null
    private var ready = false   // 데이터가 와서 화면 초기화를 마쳤는지
    private var closing = false // 저장·삭제·나가기로 닫는 중인지

    private var original: Task? = null // 수정 모드일 때 원래 업무
    private var projectLocked = true   // false 면 프로젝트 드롭다운 표시
    private var projectId = ""
    private var assigneeId: String? = null
    private var dueDate: LocalDate = LocalDate.now()
    private var projectChoices: List<Project> = emptyList()
    private var assigneeChoices: List<Member?> = emptyList() // null = 담당자 없음

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
        binding.deleteButton.setOnClickListener { confirmDelete() }
        binding.postponeButton.setOnClickListener { postpone() }
        binding.dueInput.setOnClickListener { pickDue() }
        binding.dueLayout.setStartIconOnClickListener { pickDue() }
        binding.statusGroup.setOnCheckedChangeListener { _, _ -> updateDueViews() }

        binding.projectPicker.setOnItemClickListener { _, _, position, _ ->
            val chosen = projectChoices.getOrNull(position) ?: return@setOnItemClickListener
            if (chosen.id != projectId) {
                projectId = chosen.id
                assigneeId = null // 프로젝트가 바뀌면 담당자는 다시 고름
                refreshAssignees(TeamRepository.current)
                updateDueViews()
            }
        }
        binding.assigneePicker.setOnItemClickListener { _, _, position, _ ->
            if (position in assigneeChoices.indices) assigneeId = assigneeChoices[position]?.id
        }
        setupKeyEvents()

        // 뒤로 가기: 저장 안 한 변경이 있으면 확인
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (ready && currentForm() != initialForm) {
                    Dialogs.confirm(
                        this@TaskEditActivity,
                        R.string.msg_discard_title, R.string.msg_discard_body, R.string.action_discard,
                    ) { close() }
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
        outState.putString(KEY_PRIORITY, checkedPriority().name)
        outState.putString(KEY_STATUS, checkedStatus().name)
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
            Form(task.projectId, task.title, task.description, task.assigneeId, task.dueDate, task.priority, task.status)
        } else {
            Form(projectId, "", "", null, defaultDue(project, today), Priority.MEDIUM, TaskStatus.TODO)
        }

        // 3) 화면에 채우기 (화면 회전 후라면 저장해 둔 선택값 우선)
        assigneeId = initialForm.assigneeId
        dueDate = initialForm.dueDate
        var priority = initialForm.priority
        var status = initialForm.status
        val state = savedState
        if (state != null && state.containsKey(KEY_DUE)) {
            val savedProject = state.getString(KEY_PROJECT)
            if (!projectLocked && savedProject != null && data.project(savedProject) != null) projectId = savedProject
            assigneeId = state.getString(KEY_ASSIGNEE)
            dueDate = LocalDate.ofEpochDay(state.getLong(KEY_DUE))
            priority = Priority.entries.firstOrNull { it.name == state.getString(KEY_PRIORITY) } ?: priority
            status = TaskStatus.entries.firstOrNull { it.name == state.getString(KEY_STATUS) } ?: status
        } else if (task != null) {
            binding.titleInput.setText(task.title)
            binding.descInput.setText(task.description)
        }
        binding.priorityGroup.check(priorityButton(priority))
        binding.statusGroup.check(statusButton(status))

        binding.deleteButton.isVisible = task != null
        binding.projectCard.isVisible = projectLocked
        binding.projectPickerLayout.isVisible = !projectLocked
        refreshProjects(data)
        refreshAssignees(data)
        updateDueViews()
    }

    /** 초기화 이후 데이터가 바뀌면: 삭제됐는지 확인하고 선택 목록만 갱신 (입력 중인 값은 유지) */
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
        refreshAssignees(data)
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
            return
        }
        projectChoices = data.projects
        binding.projectPicker.setSimpleItems(projectChoices.map { it.name }.toTypedArray())
        binding.projectPicker.setText(project.name, false)
    }

    /** 담당자 목록 = 담당자 없음 + 선택한 프로젝트의 팀원 */
    private fun refreshAssignees(data: AppData) {
        assigneeChoices = listOf<Member?>(null) + data.membersOf(projectId)
        val labels = assigneeChoices.map { member ->
            when {
                member == null -> getString(R.string.none_assignee)
                member.id == data.myUid -> getString(R.string.name_with_me, member.name)
                else -> member.name
            }
        }
        binding.assigneePicker.setSimpleItems(labels.toTypedArray())
        binding.assigneePicker.setText(Labels.assignee(this, data, projectId, assigneeId), false)
    }

    /** 마감일 표시 + 몇 주차인지 + 지연 안내 */
    private fun updateDueViews() {
        if (!ready || closing) return
        binding.dueInput.setText(Fmt.dateFull(this, dueDate))
        val project = TeamRepository.current.project(projectId)
        binding.weekHint.text = if (project == null) "" else
            getString(R.string.task_week_hint, WeekCalculator.weekOf(project, dueDate))

        // 저장된 업무를 수정 중이고, 완료가 아닌데 마감일이 지났으면 지연 안내
        val today = LocalDate.now()
        val overdue = original != null && checkedStatus() != TaskStatus.DONE && dueDate.isBefore(today)
        binding.overdueBanner.isVisible = overdue
        if (overdue) {
            binding.overdueText.text = getString(R.string.task_overdue_banner, -TaskRules.dDay(dueDate, today))
        }
    }

    private fun pickDue() {
        if (!ready) return
        Dialogs.pickDate(this, dueDate) { picked ->
            dueDate = picked
            updateDueViews()
        }
    }

    /** 다음 주로 미루기: 바로 저장되고 화면의 마감일도 바뀜 */
    private fun postpone() {
        val task = original ?: return
        val newDue = TeamRepository.postponeTask(task.copy(dueDate = dueDate), LocalDate.now(), failureToast())
        dueDate = newDue
        original = task.copy(dueDate = newDue)
        initialForm = initialForm.copy(dueDate = newDue) // 이미 저장됐으므로 변경으로 치지 않음
        updateDueViews()
        toast(getString(R.string.msg_postponed, Fmt.date(this, newDue)))
    }

    private fun confirmDelete() {
        val task = original ?: return
        Dialogs.confirm(this, R.string.confirm_delete_task, null, R.string.action_delete) {
            TeamRepository.deleteTask(task, failureToast())
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
        val base = original ?: Task(id = "", projectId = form.projectId, title = form.title, dueDate = form.dueDate)
        val task = base.copy(
            projectId = form.projectId,
            title = form.title,
            description = form.description,
            assigneeId = form.assigneeId,
            dueDate = form.dueDate,
            priority = form.priority,
            status = form.status,
        )
        TeamRepository.saveTask(task, failureToast())
        toast(R.string.msg_saved)
        close()
    }

    private fun currentForm() = Form(
        projectId = projectId,
        title = binding.titleInput.text.toString().trim(),
        description = binding.descInput.text.toString().trim(),
        assigneeId = assigneeId,
        dueDate = dueDate,
        priority = checkedPriority(),
        status = checkedStatus(),
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
        finish()
    }

    companion object {
        const val EXTRA_PROJECT_ID = "project_id"
        const val EXTRA_TASK_ID = "task_id"

        private const val KEY_PROJECT = "te_project"
        private const val KEY_ASSIGNEE = "te_assignee"
        private const val KEY_DUE = "te_due"
        private const val KEY_PRIORITY = "te_priority"
        private const val KEY_STATUS = "te_status"

        /** taskId == null 이면 새 업무. projectId == null 이면 화면에서 프로젝트를 고름 */
        fun start(context: Context, projectId: String?, taskId: String?) {
            context.startActivity(
                Intent(context, TaskEditActivity::class.java)
                    .putExtra(EXTRA_PROJECT_ID, projectId)
                    .putExtra(EXTRA_TASK_ID, taskId)
            )
        }
    }
}
