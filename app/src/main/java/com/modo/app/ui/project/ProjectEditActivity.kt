package com.modo.app.ui.project

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.modo.app.R
import com.modo.app.data.Project
import com.modo.app.data.ProjectMode
import com.modo.app.data.TeamRepository
import com.modo.app.databinding.ActivityProjectEditBinding
import com.modo.app.ui.common.Dialogs
import com.modo.app.ui.common.Fmt
import com.modo.app.ui.common.Labels
import com.modo.app.ui.common.Swatches
import com.modo.app.ui.common.applySystemBarsPadding
import com.modo.app.ui.common.ensureSignedIn
import com.modo.app.ui.common.failureToast
import com.modo.app.ui.common.toast
import java.time.LocalDate
import java.time.LocalTime

/** 프로젝트 만들기(projectId == null) / 설정 */
class ProjectEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProjectEditBinding

    /** null 이면 새 프로젝트 */
    private var projectId: String? = null

    // 폼 상태 (EditText가 아닌 값들은 직접 들고 있다가 화면 회전 시 저장)
    private var startDate: LocalDate = LocalDate.now()
    private var endDate: LocalDate = LocalDate.now().plusDays(28)
    private var colorIndex = 0
    private var mode = ProjectMode.TODAY
    private var meetingDecided = true
    private var meetingDay = LocalDate.now().dayOfWeek.value
    private var meetingTime: LocalTime = LocalTime.of(19, 0)
    private var durationMin = 60

    /** 처음 불러온 상태. 뒤로 가기 때 이것과 다르면 "저장하지 않고 나갈까요?" */
    private var initialSnapshot: String? = null
    private var formReady = false

    // 코드에서 만든 RadioButton id (순서 = 요일-1 / 모드 / 길이)
    private val dayButtonIds = mutableListOf<Int>()
    private val modeButtonIds = mutableListOf<Int>()
    private val durationButtonIds = mutableListOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ensureSignedIn()) return
        enableEdgeToEdge()
        binding = ActivityProjectEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsPadding()

        projectId = intent.getStringExtra(EXTRA_PROJECT_ID)

        // 상단 바: 뒤로 / 제목 / 저장
        binding.topBar.title.setText(if (projectId == null) R.string.project_new_title else R.string.more_settings)
        binding.topBar.back.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.topBar.textAction.isVisible = true
        binding.topBar.textAction.setText(R.string.action_save)
        binding.topBar.textAction.setOnClickListener { save() }

        // 화면 회전: 저장해 둔 값 복원 (이름·설명 EditText는 시스템이 자동 복원)
        if (savedInstanceState != null) restoreState(savedInstanceState)

        Swatches.build(binding.colorGroup, colorIndex) { colorIndex = it }
        buildModeChips()
        buildDayChips()
        buildDurationChips()

        binding.meetingModeGroup.setOnCheckedChangeListener { _, checkedId ->
            meetingDecided = checkedId != R.id.meetingCollect
            renderFields()
        }
        binding.startInput.setOnClickListener {
            Dialogs.pickDate(this, startDate) { picked ->
                startDate = picked
                if (endDate.isBefore(startDate)) endDate = startDate // 마감일이 시작일보다 빠르면 맞춰 줌
                renderFields()
            }
        }
        binding.endInput.setOnClickListener {
            Dialogs.pickDate(this, endDate) { picked ->
                endDate = picked
                binding.endLayout.error = null
                renderFields()
            }
        }
        binding.timeInput.setOnClickListener {
            Dialogs.pickTime(this, meetingTime) { picked ->
                meetingTime = picked
                renderFields()
            }
        }

        // 뒤로 가기 이벤트: 바뀐 내용이 있으면 확인 팝업
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (formReady && snapshot() != initialSnapshot) {
                    Dialogs.confirm(
                        this@ProjectEditActivity,
                        R.string.msg_discard_title, R.string.msg_discard_body, R.string.action_discard,
                    ) { finish() }
                } else {
                    finish()
                }
            }
        })

        val restored = savedInstanceState != null && initialSnapshot != null
        if (restored) {
            formReady = true
            renderFields()
        } else if (projectId == null) {
            // 새 프로젝트 기본값: 오늘 시작, 4주 뒤 마감, 오늘 요일 19:00 회의, 오늘 중심 모드
            renderFields()
            initialSnapshot = snapshot()
            formReady = true
        } else {
            // 수정: 데이터가 준비되면 한 번만 채움
            TeamRepository.data.observe(this) { data ->
                if (formReady || data.loading) return@observe
                val project = data.project(projectId)
                if (project == null) {
                    toast(R.string.project_not_found)
                    finish()
                    return@observe
                }
                fillFrom(project)
                initialSnapshot = snapshot()
                formReady = true
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (!::binding.isInitialized) return
        outState.putString(KEY_START, startDate.toString())
        outState.putString(KEY_END, endDate.toString())
        outState.putInt(KEY_COLOR, colorIndex)
        outState.putString(KEY_MODE, mode.name)
        outState.putBoolean(KEY_DECIDED, meetingDecided)
        outState.putInt(KEY_DAY, meetingDay)
        outState.putString(KEY_TIME, meetingTime.toString())
        outState.putInt(KEY_DURATION, durationMin)
        outState.putString(KEY_SNAPSHOT, initialSnapshot)
    }

    private fun restoreState(state: Bundle) {
        state.getString(KEY_START)?.let { startDate = LocalDate.parse(it) }
        state.getString(KEY_END)?.let { endDate = LocalDate.parse(it) }
        colorIndex = state.getInt(KEY_COLOR, colorIndex)
        mode = ProjectMode.entries.firstOrNull { it.name == state.getString(KEY_MODE) } ?: mode
        meetingDecided = state.getBoolean(KEY_DECIDED, meetingDecided)
        meetingDay = state.getInt(KEY_DAY, meetingDay)
        state.getString(KEY_TIME)?.let { meetingTime = LocalTime.parse(it) }
        durationMin = state.getInt(KEY_DURATION, durationMin)
        initialSnapshot = state.getString(KEY_SNAPSHOT)
    }

    private fun fillFrom(project: Project) {
        binding.nameInput.setText(project.name)
        binding.subjectInput.setText(project.subject)
        binding.descInput.setText(project.description)
        startDate = project.startDate
        endDate = project.endDate
        colorIndex = project.colorIndex.coerceIn(Labels.PROJECT_COLORS.indices)
        mode = project.mode
        meetingDecided = project.meetingDecided
        meetingDay = project.meetingDay
        meetingTime = project.meetingTime
        durationMin = project.meetingDurationMin
        renderFields()
    }

    /** 날짜·시간 칸 글자와 색·모드·요일·길이 선택 상태를 현재 값으로 */
    private fun renderFields() {
        binding.startInput.setText(Fmt.dateFull(this, startDate))
        binding.endInput.setText(Fmt.dateFull(this, endDate))
        binding.timeInput.setText(Fmt.time(this, meetingTime))
        (binding.colorGroup.getChildAt(colorIndex) as? RadioButton)?.let { binding.colorGroup.check(it.id) }
        binding.modeGroup.check(modeButtonIds[mode.ordinal])
        binding.modeDesc.text = Labels.modeDescription(this, mode)
        binding.meetingModeGroup.check(if (meetingDecided) R.id.meetingDirect else R.id.meetingCollect)
        binding.meetingDirectBox.isVisible = meetingDecided
        binding.meetingCollectHelp.isVisible = !meetingDecided
        binding.dayGroup.check(dayButtonIds[(meetingDay - 1).coerceIn(dayButtonIds.indices)])
        DURATIONS.indexOf(durationMin).takeIf { it >= 0 }?.let { binding.durationGroup.check(durationButtonIds[it]) }
    }

    private fun chip(text: String): RadioButton = RadioButton(this, null, 0, R.style.Widget_Modo_ChipRadio).apply {
        id = View.generateViewId()
        this.text = text
        isClickable = true
        isFocusable = true
        isSaveEnabled = false
    }

    /** 화면 모드 칩 4개 (오늘 중심 · 진행률 중심 · 워크스페이스 · 소통 중심) */
    private fun buildModeChips() {
        val gap = resources.getDimensionPixelSize(R.dimen.gap_s)
        for (m in ProjectMode.entries) {
            val c = chip(Labels.mode(this, m))
            val params = RadioGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            params.marginEnd = gap
            binding.modeGroup.addView(c, params)
            modeButtonIds.add(c.id)
        }
        binding.modeGroup.setOnCheckedChangeListener { _, checkedId ->
            val index = modeButtonIds.indexOf(checkedId)
            if (index >= 0) {
                mode = ProjectMode.entries[index]
                binding.modeDesc.text = Labels.modeDescription(this, mode)
            }
        }
    }

    /** 월~일 요일 칩 7개를 RadioGroup에 추가 (같은 너비로 나눔) */
    private fun buildDayChips() {
        val names = resources.getStringArray(R.array.weekdays_short)
        val gap = resources.getDimensionPixelSize(R.dimen.gap_xs)
        for ((index, name) in names.withIndex()) {
            val c = chip(name)
            c.setPadding(0, c.paddingTop, 0, c.paddingBottom)
            val params = RadioGroup.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            if (index > 0) params.marginStart = gap
            binding.dayGroup.addView(c, params)
            dayButtonIds.add(c.id)
        }
        binding.dayGroup.setOnCheckedChangeListener { _, checkedId ->
            val index = dayButtonIds.indexOf(checkedId)
            if (index >= 0) meetingDay = index + 1 // 1=월 ... 7=일
        }
    }

    private fun buildDurationChips() {
        val gap = resources.getDimensionPixelSize(R.dimen.gap_xs)
        for ((index, minutes) in DURATIONS.withIndex()) {
            val c = chip(getString(R.string.duration_format, minutes))
            val params = RadioGroup.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            if (index > 0) params.marginStart = gap
            binding.durationGroup.addView(c, params)
            durationButtonIds.add(c.id)
        }
        binding.durationGroup.setOnCheckedChangeListener { _, checkedId ->
            val index = durationButtonIds.indexOf(checkedId)
            if (index >= 0) durationMin = DURATIONS[index]
        }
    }

    /** 폼 전체를 한 줄 문자열로 (변경 여부 비교용) */
    private fun snapshot(): String = listOf(
        binding.nameInput.text.toString().trim(),
        binding.subjectInput.text.toString().trim(),
        binding.descInput.text.toString().trim(),
        startDate, endDate, colorIndex, mode, meetingDecided, meetingDay, meetingTime, durationMin,
    ).joinToString("|")

    private fun save() {
        if (!formReady) return
        val name = binding.nameInput.text.toString().trim()
        val subject = binding.subjectInput.text.toString().trim()
        val description = binding.descInput.text.toString().trim()

        binding.nameLayout.error = null
        binding.endLayout.error = null
        var valid = true
        if (name.isEmpty()) {
            binding.nameLayout.error = getString(R.string.error_project_name)
            valid = false
        }
        if (endDate.isBefore(startDate)) {
            binding.endLayout.error = getString(R.string.error_period)
            valid = false
        }
        if (!valid) return

        // 회의 시간을 모으는 중이면 주차는 시작일 요일 기준
        val day = if (meetingDecided) meetingDay else startDate.dayOfWeek.value
        val id = projectId
        if (id == null) {
            val draft = Project(
                id = "", name = name, description = description, subject = subject,
                startDate = startDate, endDate = endDate, colorIndex = colorIndex,
                meetingDay = day, meetingTime = meetingTime, meetingDurationMin = durationMin,
                meetingDecided = meetingDecided, mode = mode,
            )
            val newId = TeamRepository.createProject(draft, getString(R.string.role_leader), failureToast())
            ProjectActivity.start(this, newId)
        } else {
            val original = TeamRepository.current.project(id)
            if (original == null) {
                toast(R.string.project_not_found)
            } else {
                TeamRepository.updateProject(
                    original.copy(
                        name = name, description = description, subject = subject,
                        startDate = startDate, endDate = endDate, colorIndex = colorIndex,
                        meetingDay = day, meetingTime = meetingTime, meetingDurationMin = durationMin,
                        meetingDecided = meetingDecided, mode = mode,
                    ),
                    failureToast(),
                )
            }
        }
        finish()
    }

    companion object {
        const val EXTRA_PROJECT_ID = "project_id"
        private val DURATIONS = listOf(30, 60, 90, 120)

        private const val KEY_START = "start"
        private const val KEY_END = "end"
        private const val KEY_COLOR = "color"
        private const val KEY_MODE = "mode"
        private const val KEY_DECIDED = "decided"
        private const val KEY_DAY = "day"
        private const val KEY_TIME = "time"
        private const val KEY_DURATION = "duration"
        private const val KEY_SNAPSHOT = "snapshot"

        /** projectId == null 이면 새 프로젝트 */
        fun start(context: Context, projectId: String?) {
            context.startActivity(Intent(context, ProjectEditActivity::class.java).putExtra(EXTRA_PROJECT_ID, projectId))
        }
    }
}
