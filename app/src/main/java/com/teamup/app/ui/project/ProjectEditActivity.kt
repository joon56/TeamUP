package com.teamup.app.ui.project

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
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.teamup.app.R
import com.teamup.app.data.Project
import com.teamup.app.data.TeamRepository
import com.teamup.app.databinding.ActivityProjectEditBinding
import com.teamup.app.ui.common.Dialogs
import com.teamup.app.ui.common.Fmt
import com.teamup.app.ui.common.Labels
import com.teamup.app.ui.common.applySystemBarsPadding
import com.teamup.app.ui.common.ensureSignedIn
import com.teamup.app.ui.common.failureToast
import com.teamup.app.ui.common.tintOf
import com.teamup.app.ui.common.toast
import java.time.LocalDate
import java.time.LocalTime

/** 프로젝트 만들기(projectId == null) / 수정 */
class ProjectEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProjectEditBinding

    /** null 이면 새 프로젝트 */
    private var projectId: String? = null

    // 폼 상태 (EditText가 아닌 값들은 직접 들고 있다가 화면 회전 시 저장)
    private var startDate: LocalDate = LocalDate.now()
    private var endDate: LocalDate = LocalDate.now().plusDays(28)
    private var colorIndex = 0
    private var meetingDay = LocalDate.now().dayOfWeek.value
    private var meetingTime: LocalTime = LocalTime.of(19, 0)

    /** 처음 불러온 상태. 뒤로 가기 때 이것과 다르면 "저장하지 않고 나갈까요?" */
    private var initialSnapshot: String? = null
    private var formReady = false

    // 코드에서 만든 RadioButton id (순서 = 색 번호 / 요일-1)
    private val colorButtonIds = mutableListOf<Int>()
    private val dayButtonIds = mutableListOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ensureSignedIn()) return
        enableEdgeToEdge()
        binding = ActivityProjectEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsPadding()

        projectId = intent.getStringExtra(EXTRA_PROJECT_ID)

        // 상단 바: 뒤로 / 제목 / 저장
        binding.topBar.title.setText(if (projectId == null) R.string.project_new_title else R.string.project_edit_title)
        binding.topBar.back.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.topBar.textAction.isVisible = true
        binding.topBar.textAction.setText(R.string.action_save)
        binding.topBar.textAction.setOnClickListener { save() }

        buildColorSwatches()
        buildDayChips()

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

        // 화면 회전: 저장해 둔 값 복원 (이름·설명 EditText는 시스템이 자동 복원)
        if (savedInstanceState != null) restoreState(savedInstanceState)
        val restored = savedInstanceState != null && initialSnapshot != null

        if (restored) {
            formReady = true
            renderFields()
        } else if (projectId == null) {
            // 새 프로젝트 기본값: 오늘 시작, 4주 뒤 마감, 오늘 요일 19:00 회의
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
        outState.putInt(KEY_DAY, meetingDay)
        outState.putString(KEY_TIME, meetingTime.toString())
        outState.putString(KEY_SNAPSHOT, initialSnapshot)
    }

    private fun restoreState(state: Bundle) {
        state.getString(KEY_START)?.let { startDate = LocalDate.parse(it) }
        state.getString(KEY_END)?.let { endDate = LocalDate.parse(it) }
        colorIndex = state.getInt(KEY_COLOR, colorIndex)
        meetingDay = state.getInt(KEY_DAY, meetingDay)
        state.getString(KEY_TIME)?.let { meetingTime = LocalTime.parse(it) }
        initialSnapshot = state.getString(KEY_SNAPSHOT)
    }

    private fun fillFrom(project: Project) {
        binding.nameInput.setText(project.name)
        binding.descInput.setText(project.description)
        startDate = project.startDate
        endDate = project.endDate
        colorIndex = project.colorIndex.coerceIn(Labels.PROJECT_COLORS.indices)
        meetingDay = project.meetingDay
        meetingTime = project.meetingTime
        renderFields()
    }

    /** 날짜·시간 칸 글자와 색·요일 선택 상태를 현재 값으로 */
    private fun renderFields() {
        binding.startInput.setText(Fmt.dateFull(this, startDate))
        binding.endInput.setText(Fmt.dateFull(this, endDate))
        binding.timeInput.setText(Fmt.time(this, meetingTime))
        binding.colorGroup.check(colorButtonIds[colorIndex.coerceIn(colorButtonIds.indices)])
        binding.dayGroup.check(dayButtonIds[(meetingDay - 1).coerceIn(dayButtonIds.indices)])
    }

    /** 색상 동그라미 8개를 RadioGroup에 추가 */
    private fun buildColorSwatches() {
        val size = resources.getDimensionPixelSize(R.dimen.color_swatch)
        val gap = resources.getDimensionPixelSize(R.dimen.gap_s)
        for (i in Labels.PROJECT_COLORS.indices) {
            // 기본 스타일 없이 만들고(동그라미만 보이게) 클릭 가능하게 직접 설정
            val swatch = RadioButton(this, null, 0, 0)
            swatch.id = View.generateViewId()
            swatch.buttonDrawable = null
            swatch.setBackgroundResource(R.drawable.bg_swatch_dot)
            swatch.backgroundTintList = tintOf(Labels.projectColor(i))
            // 선택 링은 foreground로 그려서 배경 색 틴트에 덮이지 않게 함
            swatch.foreground = ContextCompat.getDrawable(this, R.drawable.fg_swatch_ring)
            swatch.contentDescription = getString(R.string.desc_color, i + 1)
            swatch.isClickable = true
            swatch.isFocusable = true
            swatch.isSaveEnabled = false // 선택 상태는 colorIndex로 직접 복원
            val params = RadioGroup.LayoutParams(size, size)
            if (i < Labels.PROJECT_COLORS.lastIndex) params.marginEnd = gap
            binding.colorGroup.addView(swatch, params)
            colorButtonIds.add(swatch.id)
        }
        binding.colorGroup.setOnCheckedChangeListener { _, checkedId ->
            val index = colorButtonIds.indexOf(checkedId)
            if (index >= 0) colorIndex = index
        }
    }

    /** 월~일 요일 칩 7개를 RadioGroup에 추가 (같은 너비로 나눔) */
    private fun buildDayChips() {
        val names = resources.getStringArray(R.array.weekdays_short)
        val gap = resources.getDimensionPixelSize(R.dimen.gap_xs)
        for ((index, name) in names.withIndex()) {
            val chip = RadioButton(this, null, 0, R.style.Widget_TeamUp_ChipRadio)
            chip.id = View.generateViewId()
            chip.text = name
            chip.isClickable = true
            chip.isFocusable = true
            chip.isSaveEnabled = false
            chip.setPadding(0, chip.paddingTop, 0, chip.paddingBottom)
            val params = RadioGroup.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            if (index > 0) params.marginStart = gap
            binding.dayGroup.addView(chip, params)
            dayButtonIds.add(chip.id)
        }
        binding.dayGroup.setOnCheckedChangeListener { _, checkedId ->
            val index = dayButtonIds.indexOf(checkedId)
            if (index >= 0) meetingDay = index + 1 // 1=월 ... 7=일
        }
    }

    /** 폼 전체를 한 줄 문자열로 (변경 여부 비교용) */
    private fun snapshot(): String = listOf(
        binding.nameInput.text.toString().trim(),
        binding.descInput.text.toString().trim(),
        startDate, endDate, colorIndex, meetingDay, meetingTime,
    ).joinToString("|")

    private fun save() {
        if (!formReady) return
        val name = binding.nameInput.text.toString().trim()
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

        val id = projectId
        if (id == null) {
            val draft = Project(
                id = "",
                name = name,
                description = description,
                startDate = startDate,
                endDate = endDate,
                colorIndex = colorIndex,
                meetingDay = meetingDay,
                meetingTime = meetingTime,
            )
            val newId = TeamRepository.createProject(draft, getString(R.string.role_leader), failureToast())
            ProjectDetailActivity.start(this, newId)
        } else {
            val original = TeamRepository.current.project(id)
            if (original == null) {
                toast(R.string.project_not_found)
            } else {
                TeamRepository.updateProject(
                    original.copy(
                        name = name,
                        description = description,
                        startDate = startDate,
                        endDate = endDate,
                        colorIndex = colorIndex,
                        meetingDay = meetingDay,
                        meetingTime = meetingTime,
                    ),
                    failureToast(),
                )
            }
        }
        finish()
    }

    companion object {
        const val EXTRA_PROJECT_ID = "project_id"

        private const val KEY_START = "start"
        private const val KEY_END = "end"
        private const val KEY_COLOR = "color"
        private const val KEY_DAY = "day"
        private const val KEY_TIME = "time"
        private const val KEY_SNAPSHOT = "snapshot"

        /** projectId == null 이면 새 프로젝트 */
        fun start(context: Context, projectId: String?) {
            context.startActivity(Intent(context, ProjectEditActivity::class.java).putExtra(EXTRA_PROJECT_ID, projectId))
        }
    }
}
