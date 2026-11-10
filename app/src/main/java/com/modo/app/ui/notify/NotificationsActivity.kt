package com.modo.app.ui.notify

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import com.modo.app.R
import com.modo.app.data.AppData
import com.modo.app.data.ChatRepository
import com.modo.app.data.DataHub
import com.modo.app.data.InviteRepository
import com.modo.app.data.Prefs
import com.modo.app.data.ProfileRepository
import com.modo.app.databinding.ActivityNotificationsBinding
import com.modo.app.databinding.ItemNotificationBinding
import com.modo.app.logic.NotificationFeed
import com.modo.app.logic.NotificationItem
import com.modo.app.logic.WeekAvailability
import com.modo.app.ui.chat.ChatRoomActivity
import com.modo.app.ui.common.Fmt
import com.modo.app.ui.common.Items
import com.modo.app.ui.common.Labels
import com.modo.app.ui.common.applySystemBarsPadding
import com.modo.app.ui.common.colorOf
import com.modo.app.ui.common.ensureSignedIn
import com.modo.app.ui.common.failureToast
import com.modo.app.ui.common.toast
import com.modo.app.ui.meeting.MeetingFinderActivity
import com.modo.app.ui.nav.Nav
import com.modo.app.ui.project.ProjectTab
import com.modo.app.ui.task.TaskEditActivity
import java.time.LocalDate

/**
 * 알림 화면 (상단 🔔). 서버 푸시 없이 받아 온 데이터에서 만든다.
 * 받은 초대는 여기서 수락·거절. 화면을 열면 읽음 처리돼 벨 배지가 사라진다.
 */
class NotificationsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationsBinding
    private val busyInvites = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ensureSignedIn()) return
        enableEdgeToEdge()
        binding = ActivityNotificationsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsPadding()
        binding.topBar.title.setText(R.string.notifications_title)
        binding.topBar.back.setOnClickListener { finish() }

        DataHub.data.observe(this) { render(it) }
    }

    override fun onPause() {
        super.onPause()
        // 보고 나가면 지금까지의 알림은 읽은 것으로
        ProfileRepository.markNotificationsRead()
    }

    private fun render(data: AppData) {
        val today = LocalDate.now()
        val items = NotificationFeed.build(data, today, Fmt.startOfDayMillis(today), System.currentTimeMillis(), Prefs::meetingSeen)
        val readAt = data.profile?.notifReadAt ?: 0L
        binding.list.removeAllViews()
        if (items.isEmpty()) {
            Items.addHeader(binding.list, getString(R.string.notifications_title))
            Items.addEmpty(binding.list, R.string.notifications_empty)
            return
        }
        val invites = items.filterIsInstance<NotificationItem.InviteItem>()
        if (invites.isNotEmpty()) {
            Items.addHeader(binding.list, getString(R.string.notif_group_invites))
            invites.forEach { add(it, data, readAt) }
        }
        val rest = items.filter { it !is NotificationItem.InviteItem }
        if (rest.isNotEmpty()) {
            Items.addHeader(binding.list, getString(R.string.notif_group_recent))
            rest.forEach { add(it, data, readAt) }
        }
    }

    private fun add(item: NotificationItem, data: AppData, readAt: Long) {
        val row = ItemNotificationBinding.inflate(LayoutInflater.from(this), binding.list, false)
        val unseen = item.at > readAt
        when (item) {
            is NotificationItem.InviteItem -> {
                val invite = item.invite
                setIcon(row, R.drawable.ic_group_add, R.color.primary)
                row.title.text = getString(R.string.notif_invite, invite.fromName, invite.projectName)
                row.subtitle.isVisible = false
                row.time.text = Fmt.relativeTime(this, invite.createdAt)
                row.actions.isVisible = true
                val busy = invite.id in busyInvites
                row.accept.isEnabled = !busy
                row.decline.isEnabled = !busy
                row.accept.setOnClickListener {
                    busyInvites.add(invite.id)
                    render(DataHub.current)
                    InviteRepository.accept(invite, getString(R.string.role_member)) { error ->
                        busyInvites.remove(invite.id)
                        if (error == null) {
                            toast(getString(R.string.notif_accepted, invite.projectName))
                            Nav.openProject(this, invite.projectId)
                        } else {
                            failureToast()(error)
                            render(DataHub.current)
                        }
                    }
                }
                row.decline.setOnClickListener { InviteRepository.decline(invite, failureToast()) }
            }
            is NotificationItem.DeadlineItem -> {
                setIcon(row, if (item.overdue) R.drawable.ic_warning else R.drawable.ic_timer, if (item.overdue || item.dDay == 0) R.color.danger else R.color.warning)
                row.title.text = item.task.title
                row.subtitle.text = getString(
                    R.string.dot_join, item.project.name,
                    when {
                        item.overdue -> getString(R.string.notif_deadline_overdue, -item.dDay)
                        item.dDay == 0 -> getString(R.string.notif_deadline_today)
                        else -> getString(R.string.notif_deadline_soon, item.dDay)
                    },
                )
                row.time.text = Fmt.dDay(this, item.task.dueDate, LocalDate.now())
                row.root.setOnClickListener { TaskEditActivity.start(this, item.task.projectId, item.task.id) }
            }
            is NotificationItem.NoticeItem -> {
                setIcon(row, R.drawable.ic_campaign, R.color.info)
                row.title.text = getString(R.string.notif_notice, item.notice.title.ifBlank { item.notice.body })
                row.subtitle.text = getString(R.string.dot_join, item.project.name, item.notice.authorName)
                row.time.text = Fmt.relativeTime(this, item.notice.createdAt)
                row.root.setOnClickListener { Nav.openProject(this, item.project.id, ProjectTab.NOTICES.name) }
            }
            is NotificationItem.MentionItem -> {
                val message = item.message
                setIcon(row, R.drawable.ic_mention, R.color.success)
                row.title.text = getString(R.string.notif_mention, message.senderName)
                val roomTitle = ChatRepository.projectIdOf(message.roomId)?.let { data.project(it)?.name }
                    ?: data.room(message.roomId)?.names?.get(message.senderUid)
                row.subtitle.text = listOfNotNull(roomTitle, message.text).joinToString(" · ")
                row.time.text = Fmt.relativeTime(this, message.createdAt)
                // 회의 시간 입력 요청은 바로 회의 시간 찾기로
                val meetingProject = ChatRepository.projectIdOf(message.roomId).takeIf { message.refId == MeetingFinderActivity.REF_MEETING }
                row.root.setOnClickListener {
                    if (meetingProject != null) MeetingFinderActivity.start(this, meetingProject) else ChatRoomActivity.start(this, message.roomId)
                }
            }
            is NotificationItem.MeetingItem -> {
                val slot = item.recommendation.slot
                setIcon(row, R.drawable.ic_event_available, R.color.primary)
                row.title.text = getString(R.string.notif_meeting, item.project.name)
                row.subtitle.text = getString(
                    R.string.notif_meeting_sub, Fmt.dayTime(this, slot.day, WeekAvailability.slotTime(slot.start)),
                    item.recommendation.availableIds.size, item.people,
                )
                row.time.text = if (item.at > 0) Fmt.relativeTime(this, item.at) else ""
                row.root.setOnClickListener { MeetingFinderActivity.start(this, item.project.id) }
            }
        }
        // 아직 안 본 알림은 테두리를 강조
        if (unseen) row.root.setBackgroundResource(R.drawable.bg_card_unseen)
        binding.list.addView(row.root)
    }

    private fun setIcon(row: ItemNotificationBinding, icon: Int, colorRes: Int) {
        val color = colorOf(colorRes)
        row.icon.setImageResource(icon)
        row.icon.backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 0x30))
        row.icon.imageTintList = ColorStateList.valueOf(color)
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, NotificationsActivity::class.java))
        }
    }
}
