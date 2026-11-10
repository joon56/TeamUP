package com.modo.app.ui.meeting

import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.modo.app.R
import com.modo.app.data.AppData
import com.modo.app.data.ChatRepository
import com.modo.app.data.DataHub
import com.modo.app.data.Member
import com.modo.app.data.MessageType
import com.modo.app.data.Prefs
import com.modo.app.data.Project
import com.modo.app.data.TeamRepository
import com.modo.app.databinding.ActivityMeetingFinderBinding
import com.modo.app.databinding.ItemMeetingRecBinding
import com.modo.app.logic.MeetingFinder
import com.modo.app.logic.MeetingSlot
import com.modo.app.logic.Recommendation
import com.modo.app.logic.WeekAvailability
import com.modo.app.ui.common.Fmt
import com.modo.app.ui.common.applySystemBarsPadding
import com.modo.app.ui.common.colorOf
import com.modo.app.ui.common.ensureSignedIn
import com.modo.app.ui.common.failureToast
import com.modo.app.ui.common.tintOf
import com.modo.app.ui.common.toast

/**
 * 회의 시간 찾기 (when2meet 방식): 누가 입력했는지 → 회의 길이 → 추천 TOP 3 → 히트맵.
 * 추천을 정기 회의로 지정하면 프로젝트 회의 요일·시간이 바뀌고 팀 채팅에 알림이 올라간다.
 */
class MeetingFinderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMeetingFinderBinding
    private lateinit var projectId: String

    /** 지금 보고 있는 회의 길이(분). 0이면 아직 프로젝트 값으로 맞추지 않음 */
    private var durationMin = 0
    private val durationIds = mutableListOf<Int>()
    private var selectedCell: Pair<Int, Int>? = null
    private var requested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ensureSignedIn()) return
        projectId = intent.getStringExtra(EXTRA_PROJECT_ID) ?: return finish()
        enableEdgeToEdge()
        binding = ActivityMeetingFinderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsPadding()

        binding.topBar.title.setText(R.string.finder_title)
        binding.topBar.back.setOnClickListener { finish() }
        durationMin = savedInstanceState?.getInt(KEY_DURATION) ?: 0
        requested = savedInstanceState?.getBoolean(KEY_REQUESTED) ?: false
        buildDurationChips()
        buildLegend()
        binding.heatGrid.onCellTap = { day, slot ->
            selectedCell = day to slot
            renderCell(DataHub.current)
        }
        DataHub.data.observe(this) { render(it) }
    }

    override fun onResume() {
        super.onResume()
        markSeen(DataHub.current)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_DURATION, durationMin)
        outState.putBoolean(KEY_REQUESTED, requested)
    }

    private fun buildDurationChips() {
        val gap = resources.getDimensionPixelSize(R.dimen.gap_xs)
        for ((index, minutes) in DURATIONS.withIndex()) {
            val chip = RadioButton(this, null, 0, R.style.Widget_Modo_ChipRadio).apply {
                id = View.generateViewId()
                text = getString(R.string.duration_format, minutes)
                isClickable = true
                isFocusable = true
                isSaveEnabled = false
            }
            val params = RadioGroup.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            if (index > 0) params.marginStart = gap
            binding.durationGroup.addView(chip, params)
            durationIds.add(chip.id)
        }
        binding.durationGroup.setOnCheckedChangeListener { _, checkedId ->
            val index = durationIds.indexOf(checkedId)
            if (index >= 0 && DURATIONS[index] != durationMin) {
                durationMin = DURATIONS[index]
                render(DataHub.current)
            }
        }
    }

    /** 히트맵 범례: 적음 ■■■■■ 많음 */
    private fun buildLegend() {
        val size = resources.getDimensionPixelSize(R.dimen.gap_m)
        val gap = resources.getDimensionPixelSize(R.dimen.gap_xs) / 2
        for (count in 0..LEGEND_STEPS) {
            val box = View(this)
            box.background = GradientDrawable().apply {
                cornerRadius = gap * 2f
                setColor(binding.heatGrid.colorFor(count, LEGEND_STEPS))
            }
            val params = LinearLayout.LayoutParams(size, size)
            params.marginStart = gap
            params.marginEnd = gap
            binding.legend.addView(box, params)
        }
    }

    private fun render(data: AppData) {
        val project = data.project(projectId)
        if (project == null) {
            if (!data.loading) finish()
            return
        }
        if (durationMin == 0) durationMin = project.meetingDurationMin.takeIf { it in DURATIONS } ?: DEFAULT_DURATION
        durationIds.getOrNull(DURATIONS.indexOf(durationMin))?.let { if (binding.durationGroup.checkedRadioButtonId != it) binding.durationGroup.check(it) }

        val members = data.membersOf(projectId)
        val people = MeetingFinder.people(members)
        renderStatus(data, members, people)

        val recommendations = MeetingFinder.recommend(people, MeetingFinder.lengthSlots(durationMin), top = 3)
        renderRecommendations(project, members, recommendations)
        binding.emptyText.isVisible = people.isEmpty()

        binding.heatGrid.setHeatmap(MeetingFinder.heatmap(people), people.size, recommendations.map { it.slot })
        renderCell(data)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) markSeen(data)
    }

    /** 몇 명이 입력했는지 + 팀원 칩(입력한 사람은 강조) + 내 시간 입력 / 입력 요청 */
    private fun renderStatus(data: AppData, members: List<Member>, people: Map<String, WeekAvailability>) {
        binding.status.text = getString(R.string.finder_status, members.size, people.size)
        binding.memberChips.removeAllViews()
        val gap = resources.getDimensionPixelSize(R.dimen.gap_s)
        for ((index, member) in members.withIndex()) {
            val entered = member.id in people
            val chip = CheckedTextView(this, null, 0, R.style.Widget_Modo_ChipRadio).apply {
                text = if (entered) "✓ ${member.name}" else member.name
                isChecked = entered
                checkMarkDrawable = null
            }
            chip.setOnClickListener {
                // 내 시간이나 앱을 안 쓰는 팀원 시간만 대신 입력
                if (member.uid == data.myUid || !member.isAppUser) AvailabilityActivity.startForMember(this, projectId, member.id)
                else toast(R.string.avail_only_self)
            }
            val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            if (index > 0) params.marginStart = gap
            binding.memberChips.addView(chip, params)
        }

        val me = data.me(projectId)
        binding.myButton.isVisible = me != null
        binding.myButton.setText(if (me != null && me.id in people) R.string.finder_edit_mine_done else R.string.finder_edit_mine)
        binding.myButton.setOnClickListener { me?.let { AvailabilityActivity.startForMember(this, projectId, it.id) } }

        // 요청할 사람(앱을 쓰는데 아직 입력 안 한 팀원)이 없으면 숨김
        val waiting = members.filter { it.isAppUser && it.uid != data.myUid && it.id !in people }
        binding.requestButton.isVisible = waiting.isNotEmpty()
        binding.requestButton.isEnabled = !requested
        binding.requestButton.setOnClickListener { requestInput(data, waiting) }
    }

    private fun renderRecommendations(project: Project, members: List<Member>, recommendations: List<Recommendation>) {
        binding.recList.removeAllViews()
        val names = members.associate { it.id to it.name }
        for ((index, rec) in recommendations.withIndex()) {
            val row = ItemMeetingRecBinding.inflate(layoutInflater, binding.recList, false)
            row.rank.text = getString(R.string.finder_rank, index + 1)
            if (index == 0) {
                row.rank.backgroundTintList = tintOf(R.color.primary)
                row.rank.setTextColor(colorOf(R.color.on_primary))
            } else {
                row.rank.setTextColor(colorOf(R.color.text_secondary))
            }
            val slot = rec.slot
            row.whenText.text = getString(
                R.string.finder_range, Fmt.weekday(this, slot.day),
                Fmt.time(this, WeekAvailability.slotTime(slot.start)), Fmt.time(this, WeekAvailability.slotTime(slot.end)),
            )
            row.count.text = getString(R.string.finder_available, rec.total, rec.availableIds.size)
            row.ratio.max = rec.total
            row.ratio.progress = rec.availableIds.size
            row.detail.text = if (rec.unavailableIds.isEmpty()) getString(R.string.finder_everyone)
            else getString(R.string.finder_unavailable, rec.unavailableIds.mapNotNull { names[it] }.joinToString(", "))
            val current = project.meetingDecided && project.meetingDay == slot.day &&
                WeekAvailability.slotOf(project.meetingTime) == slot.start && project.meetingDurationMin == durationMin
            row.setButton.isEnabled = !current
            row.setButton.setText(if (current) R.string.finder_current else R.string.finder_set_regular)
            row.setButton.setOnClickListener { confirmRegular(project, slot) }
            binding.recList.addView(row.root)
        }
    }

    /** 히트맵에서 누른 칸: 몇 명 중 몇 명, 누가 되는지 */
    private fun renderCell(data: AppData) {
        val cell = selectedCell
        if (cell == null) {
            binding.cellInfo.setText(R.string.finder_cell_hint)
            return
        }
        val (day, slot) = cell
        val members = data.membersOf(projectId)
        val people = MeetingFinder.people(members)
        val free = members.filter { people[it.id]?.isFree(day, slot) == true }
        val head = getString(R.string.finder_cell, Fmt.dayTime(this, day, WeekAvailability.slotTime(slot)), people.size, free.size)
        val tail = if (free.isEmpty()) getString(R.string.finder_cell_nobody)
        else getString(R.string.finder_cell_who, free.joinToString(", ") { it.name })
        binding.cellInfo.text = "$head\n$tail"
    }

    private fun confirmRegular(project: Project, slot: MeetingSlot) {
        val time = WeekAvailability.slotTime(slot.start)
        val whenText = Fmt.dayTime(this, slot.day, time)
        val minutes = durationMin
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.finder_confirm_title, whenText))
            .setMessage(getString(R.string.finder_confirm_body, minutes))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_ok) { _, _ ->
                TeamRepository.setMeeting(project.id, slot.day, time, minutes, failureToast())
                ChatRepository.send(
                    ChatRepository.roomIdForProject(project.id), MessageType.SYSTEM,
                    getString(R.string.finder_set_message, DataHub.current.myName, whenText, minutes), refId = REF_MEETING,
                )
                toast(R.string.finder_set_done)
            }
            .show()
    }

    /** 아직 입력 안 한 앱 사용자를 멘션해서 팀 채팅에 요청 (멘션 알림으로 받음, 누르면 이 화면) */
    private fun requestInput(data: AppData, waiting: List<Member>) {
        if (waiting.isEmpty()) return
        val text = getString(R.string.finder_request_message, data.myName, waiting.joinToString(" ") { "@${it.name}" })
        ChatRepository.send(
            ChatRepository.roomIdForProject(projectId), MessageType.SYSTEM, text,
            refId = REF_MEETING, mentions = waiting.mapNotNull { it.uid }, onFailure = failureToast(),
        )
        requested = true
        binding.requestButton.isEnabled = false
        toast(R.string.finder_requested)
    }

    /** 지금 1순위(프로젝트 회의 길이 기준)를 본 것으로 → 같은 추천으로는 알림이 다시 안 뜸 */
    private fun markSeen(data: AppData) {
        val project = data.project(projectId) ?: return
        val people = MeetingFinder.people(data.membersOf(projectId))
        val top = MeetingFinder.recommend(people, MeetingFinder.lengthSlots(project.meetingDurationMin), top = 1).firstOrNull()
        if (top != null) Prefs.setMeetingSeen(projectId, top.slot.key)
    }

    companion object {
        /** 회의 시간 관련 채팅 알림(요청·지정)의 refId: 누르면 이 화면으로 */
        const val REF_MEETING = "meeting"
        private const val EXTRA_PROJECT_ID = "project_id"
        private const val KEY_DURATION = "mf_duration"
        private const val KEY_REQUESTED = "mf_requested"
        private val DURATIONS = listOf(30, 60, 90, 120)
        private const val DEFAULT_DURATION = 60
        private const val LEGEND_STEPS = 4

        fun start(context: Context, projectId: String) {
            context.startActivity(Intent(context, MeetingFinderActivity::class.java).putExtra(EXTRA_PROJECT_ID, projectId))
        }
    }
}
