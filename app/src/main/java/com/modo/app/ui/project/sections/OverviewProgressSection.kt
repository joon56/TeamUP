package com.modo.app.ui.project.sections

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.modo.app.R
import com.modo.app.data.AppData
import com.modo.app.data.Project
import com.modo.app.data.TaskStatus
import com.modo.app.databinding.ItemActivityBinding
import com.modo.app.databinding.SectionOverviewProgressBinding
import com.modo.app.logic.ActivityEvent
import com.modo.app.logic.ActivityFeed
import com.modo.app.logic.ProgressCalculator
import com.modo.app.logic.Schedule
import com.modo.app.logic.WeekCalculator
import com.modo.app.ui.common.Fmt
import com.modo.app.ui.common.Items
import com.modo.app.ui.common.Labels
import com.modo.app.ui.common.colorOf
import com.modo.app.ui.member.MemberDetailActivity
import com.modo.app.ui.project.ProjectActivity
import java.time.LocalDate
import java.time.LocalDateTime

/** 시안 2 "진행률 중심": 이 프로젝트가 어디까지 왔는지 + 팀원별 현황 + 최근 활동 */
class OverviewProgressSection(private val host: ProjectActivity, inflater: LayoutInflater, parent: ViewGroup) : Section {

    private val b = SectionOverviewProgressBinding.inflate(inflater, parent, false)
    override val root: View get() = b.root

    override fun render(data: AppData, project: Project) {
        val today = LocalDate.now()
        val tasks = data.tasksOf(project.id)
        val color = host.colorOf(Labels.projectColor(project.colorIndex))

        val week = WeekCalculator.weekOf(project, today)
        val weekRate = ProgressCalculator.weekRate(project, tasks, week)
        b.ring.setIndicatorColor(color)
        b.ring.progress = weekRate ?: 0
        b.ringRate.text = if (weekRate == null) "–" else host.getString(R.string.percent, weekRate)
        val overall = ProgressCalculator.rate(tasks)
        b.overallText.text = host.getString(R.string.progress_hero_overall, Fmt.percent(host, overall))
        b.overallBar.setIndicatorColor(color)
        b.overallBar.progress = overall ?: 0
        b.remainingText.text = host.getString(R.string.progress_remaining, tasks.count { it.status != TaskStatus.DONE })
        val next = Schedule.nextMeeting(project, LocalDateTime.now())
        b.meetingText.text = when {
            !project.meetingDecided -> host.getString(R.string.meeting_undecided)
            next == null -> host.getString(R.string.overview_no_meeting)
            else -> host.getString(
                R.string.next_meeting_format,
                "${Fmt.relativeDay(host, next.toLocalDate(), today)} ${Fmt.time(host, next.toLocalTime())}",
            )
        }
        val range = WeekCalculator.rangeOf(project, week)
        b.weekText.text = host.getString(
            R.string.overview_week_range, week,
            host.getString(R.string.range_format, Fmt.dateShort(host, range.start), Fmt.dateShort(host, range.endInclusive)),
        )

        b.memberList.removeAllViews()
        for (stat in ProgressCalculator.memberStats(data.membersOf(project.id), tasks)) {
            Items.addMember(
                b.memberList, stat.member, stat, isMe = stat.member.uid == data.myUid,
                isLeader = stat.member.uid != null && stat.member.uid == project.ownerUid,
            ) { MemberDetailActivity.start(host, project.id, stat.member.id) }
        }

        b.activityList.removeAllViews()
        val events = ActivityFeed.build(data, project.id, limit = 8)
        if (events.isEmpty()) {
            val empty = TextView(host).apply {
                setText(R.string.activity_empty)
                setTextAppearance(R.style.TextAppearance_Modo_BodySecondary)
                val pad = resources.getDimensionPixelSize(R.dimen.gap_s)
                setPadding(0, pad, 0, pad)
            }
            b.activityList.addView(empty)
        }
        for (event in events) addActivity(event, color)
    }

    private fun addActivity(event: ActivityEvent, color: Int) {
        val row = ItemActivityBinding.inflate(LayoutInflater.from(host), b.activityList, false)
        val (icon, text) = when (event) {
            is ActivityEvent.NoticePosted -> R.drawable.ic_campaign to host.getString(
                R.string.activity_notice, event.notice.authorName, event.notice.title.ifBlank { event.notice.body },
            )
            is ActivityEvent.MinutesWritten -> R.drawable.ic_minutes to host.getString(R.string.activity_minutes, event.minutes.title)
            is ActivityEvent.TaskCompleted -> R.drawable.ic_task_done to (
                event.assigneeName?.let { host.getString(R.string.activity_task_done, it, event.task.title) }
                    ?: host.getString(R.string.activity_task_done_noname, event.task.title)
                )
            is ActivityEvent.MemberJoined -> R.drawable.ic_person_add to host.getString(R.string.activity_joined, event.member.name)
        }
        row.icon.setImageResource(icon)
        row.icon.backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 0x30))
        row.icon.imageTintList = ColorStateList.valueOf(color)
        row.text.text = text
        row.time.text = Fmt.relativeTime(host, event.at)
        b.activityList.addView(row.root)
    }
}
