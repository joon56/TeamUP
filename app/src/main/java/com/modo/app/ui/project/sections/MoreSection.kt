package com.modo.app.ui.project.sections

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.modo.app.R
import com.modo.app.data.AppData
import com.modo.app.data.Project
import com.modo.app.data.TaskStatus
import com.modo.app.databinding.ItemMenuRowBinding
import com.modo.app.databinding.SectionMoreBinding
import com.modo.app.logic.ProgressCalculator
import com.modo.app.logic.Schedule
import com.modo.app.logic.WeekCalculator
import com.modo.app.ui.common.Fmt
import com.modo.app.ui.common.Labels
import com.modo.app.ui.common.colorOf
import com.modo.app.ui.files.FilesActivity
import com.modo.app.ui.member.InviteActivity
import com.modo.app.ui.project.ProjectActions
import com.modo.app.ui.project.ProjectActivity
import com.modo.app.ui.project.ProjectEditActivity
import java.time.LocalDate
import java.time.LocalDateTime

/** 시안 3 "워크스페이스"의 더보기: 개요 요약 · 팀원 · 파일함 · 화면 모드 · 초대 코드 · 설정 */
class MoreSection(private val host: ProjectActivity, inflater: LayoutInflater, parent: ViewGroup) : Section {

    private val b = SectionMoreBinding.inflate(inflater, parent, false)
    override val root: View get() = b.root
    private var project: Project? = null

    init {
        b.invite.setOnClickListener { project?.let { InviteActivity.start(host, it.id) } }
        addMenu(R.drawable.ic_files, R.string.more_files) { project?.let { FilesActivity.start(host, it.id) } }
        addMenu(R.drawable.ic_mode, R.string.more_mode) { project?.let { ProjectActions.chooseMode(host, it) } }
        addMenu(R.drawable.ic_share, R.string.more_share_code) { project?.let { ProjectActions.shareInvite(host, it) } }
        addMenu(R.drawable.ic_settings, R.string.more_settings) { project?.let { ProjectEditActivity.start(host, it.id) } }
    }

    private fun addMenu(icon: Int, title: Int, onClick: () -> Unit) {
        val row = ItemMenuRowBinding.inflate(LayoutInflater.from(host), b.menuList, false)
        row.icon.setImageResource(icon)
        row.title.setText(title)
        row.value.text = ""
        row.root.setOnClickListener { onClick() }
        b.menuList.addView(row.root)
    }

    override fun render(data: AppData, project: Project) {
        this.project = project
        val today = LocalDate.now()
        val tasks = data.tasksOf(project.id)
        val color = host.colorOf(Labels.projectColor(project.colorIndex))
        val weekRate = ProgressCalculator.weekRate(project, tasks, WeekCalculator.weekOf(project, today))
        b.ring.setIndicatorColor(color)
        b.ring.progress = weekRate ?: 0
        b.ringRate.text = if (weekRate == null) "–" else host.getString(R.string.percent, weekRate)
        b.summary1.text = host.getString(
            R.string.dot_join, host.getString(R.string.progress_this_week),
            host.getString(R.string.progress_hero_overall, Fmt.percent(host, ProgressCalculator.rate(tasks))),
        )
        b.summary2.text = host.getString(R.string.progress_remaining, tasks.count { it.status != TaskStatus.DONE })
        val next = Schedule.nextMeeting(project, LocalDateTime.now())
        b.summary3.text = when {
            !project.meetingDecided -> host.getString(R.string.meeting_undecided)
            next == null -> host.getString(R.string.overview_no_meeting)
            else -> host.getString(
                R.string.next_meeting_format,
                "${Fmt.relativeDay(host, next.toLocalDate(), today)} ${Fmt.time(host, next.toLocalTime())}",
            )
        }
        b.memberCount.text = host.getString(R.string.members_count, data.membersOf(project.id).size)
        b.memberList.removeAllViews()
        MembersSection.addMembers(b.memberList, data, project, host)
        // 화면 모드 줄에 현재 모드 표시
        (b.menuList.getChildAt(1)?.let { ItemMenuRowBinding.bind(it) })?.value?.text = Labels.mode(host, project.mode)
    }
}
