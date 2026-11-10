package com.modo.app.ui.project.sections

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.modo.app.R
import com.modo.app.data.AppData
import com.modo.app.data.Minutes
import com.modo.app.data.Project
import com.modo.app.databinding.ItemMinutesBinding
import com.modo.app.databinding.SectionMinutesBinding
import com.modo.app.logic.MinutesSearch
import com.modo.app.ui.common.Fmt
import com.modo.app.ui.common.Items
import com.modo.app.ui.common.colorOf
import com.modo.app.ui.common.tintOf
import com.modo.app.ui.minutes.MinutesEditActivity
import com.modo.app.ui.project.ProjectActivity

/** 프로젝트 회의록 탭: 검색 + 최신 회의순 카드 + 작성 버튼 */
class MinutesSection(private val host: ProjectActivity, inflater: LayoutInflater, parent: ViewGroup) : Section {

    private val b = SectionMinutesBinding.inflate(inflater, parent, false)
    override val root: View get() = b.root
    private var lastData: AppData? = null
    private var projectId: String? = null

    init {
        b.write.setOnClickListener { projectId?.let { MinutesEditActivity.start(host, it) } }
        b.searchInput.doAfterTextChanged { lastData?.let { renderList(it) } }
    }

    override fun render(data: AppData, project: Project) {
        lastData = data
        projectId = project.id
        renderList(data)
    }

    private fun renderList(data: AppData) {
        val pid = projectId ?: return
        val all = data.minutesOf(pid)
        val query = b.searchInput.text.toString()
        val shown = all.filter { MinutesSearch.matches(it, query) }
        b.searchLayout.isVisible = all.isNotEmpty()
        b.list.removeAllViews()
        when {
            all.isEmpty() -> Items.addEmpty(b.list, R.string.minutes_empty)
            shown.isEmpty() -> Items.addEmpty(b.list, R.string.minutes_no_match)
            else -> shown.forEach { addCard(it) }
        }
    }

    private fun addCard(minutes: Minutes) {
        val row = ItemMinutesBinding.inflate(LayoutInflater.from(host), b.list, false)
        row.title.text = minutes.title
        row.meta.text = host.getString(
            R.string.minutes_meta, Fmt.date(host, minutes.meetingDate), minutes.attendeeIds.size, minutes.createdByName.ifBlank { "-" },
        )
        // 미리보기: AI 요약 → 첫 결정 사항 → 메모 → 받아쓰기
        val summary = minutes.summary
        val preview = summary?.overview?.takeIf { it.isNotBlank() }
            ?: summary?.decisions?.firstOrNull()
            ?: minutes.body.ifBlank { minutes.transcript }
        row.preview.isVisible = preview.isNotBlank()
        row.preview.text = preview.replace('\n', ' ')

        row.badges.removeAllViews()
        if (summary != null) addBadge(row.badges, host.getString(R.string.minutes_badge_ai), R.color.primary_container, R.color.on_primary_container)
        val actions = summary?.actions.orEmpty()
        if (actions.isNotEmpty()) addBadge(row.badges, host.getString(R.string.minutes_badge_actions, actions.size), R.color.surface_high, R.color.text_secondary)
        if (minutes.transcript.isNotBlank()) addBadge(row.badges, host.getString(R.string.minutes_badge_transcript), R.color.surface_high, R.color.text_secondary)
        row.badges.isVisible = row.badges.childCount > 0

        row.card.setOnClickListener { MinutesEditActivity.start(host, minutes.projectId, minutes.id) }
        b.list.addView(row.root)
    }

    private fun addBadge(container: LinearLayout, text: String, background: Int, textColor: Int) {
        val badge = TextView(host, null, 0, R.style.Widget_Modo_Pill)
        badge.text = text
        badge.backgroundTintList = host.tintOf(background)
        badge.setTextColor(host.colorOf(textColor))
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        if (container.childCount > 0) params.marginStart = host.resources.getDimensionPixelSize(R.dimen.gap_xs)
        container.addView(badge, params)
    }
}
