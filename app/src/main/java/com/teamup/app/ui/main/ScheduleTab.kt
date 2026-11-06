package com.teamup.app.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import com.teamup.app.R
import com.teamup.app.data.AppData
import com.teamup.app.databinding.TabScheduleBinding
import com.teamup.app.logic.Schedule
import com.teamup.app.logic.ScheduleGroup
import com.teamup.app.logic.ScheduleItem
import com.teamup.app.ui.common.Items
import com.teamup.app.ui.common.colorOf
import com.teamup.app.ui.project.ProjectDetailActivity
import com.teamup.app.ui.task.TaskEditActivity
import java.time.LocalDateTime

/** 일정 탭: 지연 / 오늘 / 다가오는 7일 / 이후 순서로 업무 마감·정기 회의·프로젝트 마감을 보여줌 */
class ScheduleTab(private val activity: MainActivity, private val b: TabScheduleBinding) {

    fun render(data: AppData) {
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        b.scheduleList.removeAllViews()

        if (data.loading) {
            Items.addEmpty(b.scheduleList, R.string.loading)
            return
        }

        val groups = Schedule.timeline(data, now) // 그룹 순서대로 정렬된 Map
        for ((group, items) in groups) {
            if (items.isEmpty()) continue
            val color = if (group == ScheduleGroup.OVERDUE) R.color.danger else R.color.text_primary
            addHeader(titleOf(group), color)
            for (item in items) {
                Items.addSchedule(b.scheduleList, item, today) { open(item) }
            }
        }
        if (groups.values.all { it.isEmpty() }) {
            Items.addEmpty(b.scheduleList, R.string.schedule_empty)
        }
    }

    // 일정 탭은 따로 기억할 상태가 없음 (스크롤 위치는 뷰가 스스로 저장)
    fun saveState(out: Bundle) {}

    fun restoreState(state: Bundle) {}

    @StringRes
    private fun titleOf(group: ScheduleGroup): Int = when (group) {
        ScheduleGroup.OVERDUE -> R.string.group_overdue
        ScheduleGroup.TODAY -> R.string.group_today
        ScheduleGroup.NEXT_7_DAYS -> R.string.group_next7
        ScheduleGroup.LATER -> R.string.group_later
    }

    private fun open(item: ScheduleItem) {
        when (item) {
            is ScheduleItem.TaskDue -> TaskEditActivity.start(activity, item.project.id, item.task.id)
            else -> ProjectDetailActivity.start(activity, item.project.id)
        }
    }

    /** 그룹 제목 (지연된 업무는 빨간색) */
    private fun addHeader(@StringRes text: Int, @ColorRes color: Int) {
        val header = LayoutInflater.from(activity)
            .inflate(R.layout.item_section_header, b.scheduleList, false) as TextView
        header.setText(text)
        header.setTextColor(activity.colorOf(color))
        b.scheduleList.addView(header)
    }
}
