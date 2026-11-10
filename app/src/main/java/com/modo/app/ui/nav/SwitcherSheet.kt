package com.modo.app.ui.nav

import android.app.Dialog
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import com.modo.app.R
import com.modo.app.data.DataHub
import com.modo.app.data.Project
import com.modo.app.databinding.DialogSwitcherBinding
import com.modo.app.databinding.ItemSwitcherRowBinding
import com.modo.app.logic.ProgressCalculator
import com.modo.app.logic.WeekCalculator
import com.modo.app.ui.common.Dialogs
import com.modo.app.ui.common.Labels
import com.modo.app.ui.common.colorOf
import com.modo.app.ui.project.ProjectEditActivity
import java.time.LocalDate

/**
 * 상단 바 왼쪽을 누르면 위에서 내려오는 시트 (넷플릭스 프로필 고르기처럼 "공간"을 바꿈).
 * 전체 홈 / 진행 중 프로젝트 / 끝난 프로젝트(접힘) / 새 프로젝트 · 코드로 참여
 */
object SwitcherSheet {

    fun show(activity: AppCompatActivity, currentProjectId: String?) {
        val dialog = Dialog(activity, R.style.ThemeOverlay_Modo_TopSheet)
        val b = DialogSwitcherBinding.inflate(LayoutInflater.from(activity))
        dialog.setContentView(b.root)
        dialog.window?.apply {
            setGravity(Gravity.TOP)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }

        val data = DataHub.current
        val today = LocalDate.now()

        // 내 프로필
        val profile = data.profile
        val name = data.myName
        val avatarColor = Labels.avatarColor(activity, profile?.avatarColor ?: -1, name)
        b.avatar.text = name.take(1).ifEmpty { "?" }
        b.avatar.backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(avatarColor, 0x40))
        b.avatar.setTextColor(avatarColor)
        b.name.text = name
        b.handle.text = profile?.handle?.takeIf { profile.tag.isNotEmpty() } ?: activity.getString(R.string.me_tag_pending)
        b.close.setOnClickListener { dialog.dismiss() }

        // 전체 홈
        addRow(b.list, activity, R.drawable.ic_home, activity.colorOf(R.color.primary),
            activity.getString(R.string.switcher_all_home), activity.getString(R.string.switcher_all_home_desc),
            selected = currentProjectId == null, unread = 0) {
            dialog.dismiss()
            if (currentProjectId != null) Nav.openRoot(activity)
        }

        val ongoing = data.projects.filter { !it.endDate.isBefore(today) }
        val finished = data.projects.filter { it.endDate.isBefore(today) }

        addHeader(b.list, activity.getString(R.string.switcher_ongoing))
        if (ongoing.isEmpty()) addHeader(b.list, activity.getString(R.string.switcher_empty), small = true)
        for (project in ongoing) addProjectRow(b.list, activity, project, currentProjectId, today, dialog)

        if (finished.isNotEmpty()) {
            val header = addHeader(b.list, activity.getString(R.string.switcher_finished, finished.size), clickable = true)
            val holder = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; isVisible = false }
            b.list.addView(holder)
            for (project in finished) addProjectRow(holder, activity, project, currentProjectId, today, dialog)
            header.setOnClickListener { holder.isVisible = !holder.isVisible }
        }

        b.newProject.setOnClickListener {
            dialog.dismiss()
            ProjectEditActivity.start(activity, null)
        }
        b.join.setOnClickListener {
            dialog.dismiss()
            Dialogs.showJoin(activity) { projectId -> Nav.openProject(activity, projectId) }
        }

        // 목록이 길면 화면의 70%까지만
        b.scroll.post {
            val max = (activity.resources.displayMetrics.heightPixels * 0.62f).toInt()
            if (b.scroll.height > max) b.scroll.layoutParams = b.scroll.layoutParams.apply { height = max }
        }
        dialog.show()
    }

    private fun addProjectRow(
        container: LinearLayout,
        activity: AppCompatActivity,
        project: Project,
        currentProjectId: String?,
        today: LocalDate,
        dialog: Dialog,
    ) {
        val data = DataHub.current
        val color = activity.colorOf(Labels.projectColor(project.colorIndex))
        val tasks = data.tasksOf(project.id)
        val finished = project.endDate.isBefore(today)
        val rate = if (finished) ProgressCalculator.rate(tasks)
        else ProgressCalculator.weekRate(project, tasks, WeekCalculator.weekOf(project, today))
        val progress = when {
            finished -> activity.getString(R.string.project_finished)
            rate == null -> activity.getString(R.string.this_week_none)
            else -> activity.getString(R.string.this_week_rate, rate)
        }
        val subtitle = activity.getString(R.string.dot_join, Labels.mode(activity, project.mode), progress)
        val unread = data.room("p_${project.id}")?.unreadFor(data.myUid) ?: 0
        addRow(container, activity, R.drawable.ic_folder, color, project.name, subtitle, project.id == currentProjectId, unread) {
            dialog.dismiss()
            if (project.id != currentProjectId) Nav.openProject(activity, project.id)
        }
    }

    private fun addRow(
        container: LinearLayout,
        activity: AppCompatActivity,
        icon: Int,
        color: Int,
        title: String,
        subtitle: String,
        selected: Boolean,
        unread: Int,
        onClick: () -> Unit,
    ) {
        val row = ItemSwitcherRowBinding.inflate(LayoutInflater.from(activity), container, false)
        row.icon.setImageResource(icon)
        row.icon.backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 0x38))
        row.icon.imageTintList = ColorStateList.valueOf(color)
        row.title.text = title
        row.subtitle.text = subtitle
        row.check.isVisible = selected
        if (selected) row.root.setBackgroundResource(R.drawable.bg_day_today)
        row.unread.isVisible = unread > 0
        row.unread.text = if (unread > 99) "99+" else unread.toString()
        row.unread.backgroundTintList = ColorStateList.valueOf(activity.colorOf(R.color.danger))
        row.unread.setTextColor(activity.colorOf(R.color.on_primary))
        row.root.setOnClickListener { onClick() }
        container.addView(row.root)
    }

    private fun addHeader(container: LinearLayout, text: String, small: Boolean = false, clickable: Boolean = false): TextView {
        val context = container.context
        val view = TextView(context).apply {
            this.text = text
            setTextAppearance(if (small) R.style.TextAppearance_Modo_Small else R.style.TextAppearance_Modo_Label)
            val pad = (8 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad * 2, pad, pad)
            if (clickable) {
                isClickable = true
                isFocusable = true
                setBackgroundResource(R.drawable.bg_switcher)
                setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_expand_more, 0)
                compoundDrawableTintList = ColorStateList.valueOf(context.colorOf(R.color.text_secondary))
            }
        }
        container.addView(view)
        return view
    }
}
