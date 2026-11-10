package com.modo.app.ui.nav

import android.content.res.ColorStateList
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import com.modo.app.R
import com.modo.app.data.AppData
import com.modo.app.data.Prefs
import com.modo.app.data.Project
import com.modo.app.databinding.ViewAppBarBinding
import com.modo.app.logic.NotificationFeed
import com.modo.app.ui.chat.ChatListActivity
import com.modo.app.ui.common.Fmt
import com.modo.app.ui.common.Labels
import com.modo.app.ui.common.colorOf
import com.modo.app.ui.notify.NotificationsActivity
import java.time.LocalDate

/**
 * 모든 화면 공통 상단 바.
 * 왼쪽 전환 버튼 → [SwitcherSheet], 오른쪽 🔔 → 알림, 💬 → 채팅 목록 (회의 피드백 1·4)
 */
class AppBar(private val activity: AppCompatActivity, val b: ViewAppBarBinding) {

    private var projectId: String? = null

    init {
        b.switcher.setOnClickListener { SwitcherSheet.show(activity, projectId) }
        b.bell.setOnClickListener { NotificationsActivity.start(activity) }
        b.chat.setOnClickListener { ChatListActivity.start(activity) }
    }

    fun showRoot() {
        projectId = null
        b.switcherLogo.isVisible = true
        b.switcherIcon.isVisible = false
        b.switcherTitle.setText(R.string.app_name)
    }

    fun showProject(project: Project) {
        projectId = project.id
        val color = activity.colorOf(Labels.projectColor(project.colorIndex))
        b.switcherLogo.isVisible = false
        b.switcherIcon.isVisible = true
        b.switcherIcon.backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 0x38))
        b.switcherIcon.imageTintList = ColorStateList.valueOf(color)
        b.switcherTitle.text = project.name
    }

    /** 다른 화면에서 쓰는 뒤로 가기 버튼 */
    fun showBack(onBack: () -> Unit) {
        b.back.isVisible = true
        b.back.setOnClickListener { onBack() }
    }

    fun render(data: AppData) {
        val today = LocalDate.now()
        val items = NotificationFeed.build(data, today, Fmt.startOfDayMillis(today), System.currentTimeMillis(), Prefs::meetingSeen)
        setBadge(b.bellBadge, NotificationFeed.unseenCount(items, data.profile?.notifReadAt ?: 0L))
        setBadge(b.chatBadge, data.unreadChats)
    }

    private fun setBadge(view: TextView, count: Int) {
        view.isVisible = count > 0
        view.text = if (count > 99) "99+" else count.toString()
    }
}
