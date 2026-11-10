package com.modo.app.ui.chat

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.PopupMenu
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.firebase.firestore.ListenerRegistration
import com.modo.app.R
import com.modo.app.data.AppData
import com.modo.app.data.Notice
import com.modo.app.data.Project
import com.modo.app.data.TeamRepository
import com.modo.app.data.Vote
import com.modo.app.databinding.ItemNoticeBinding
import com.modo.app.databinding.ItemPollOptionBinding
import com.modo.app.databinding.ViewNoticeListBinding
import com.modo.app.ui.common.Dialogs
import com.modo.app.ui.common.Fmt
import com.modo.app.ui.common.Items
import com.modo.app.ui.common.colorOf
import com.modo.app.ui.common.failureToast

/**
 * 공지 목록 (공지 화면 · 소통 중심 모드 공지 탭 공용).
 * 고정 공지를 위에, 투표는 선택지를 눌러 바로 투표하고 결과(표 수·비율)를 실시간으로 본다.
 */
class NoticeListController(
    private val activity: AppCompatActivity,
    private val b: ViewNoticeListBinding,
) {
    private var project: Project? = null
    private var data = AppData()
    private val voteListeners = mutableMapOf<String, ListenerRegistration>()
    private val votes = mutableMapOf<String, List<Vote>>()

    init {
        b.write.setOnClickListener { project?.let { NoticeEditActivity.start(activity, it.id) } }
    }

    fun render(data: AppData, project: Project) {
        this.data = data
        this.project = project
        val notices = data.noticesOf(project.id).sortedWith(compareByDescending<Notice> { it.pinned }.thenByDescending { it.createdAt })
        syncVoteListeners(notices)
        b.list.removeAllViews()
        if (notices.isEmpty()) {
            Items.addEmpty(b.list, R.string.notices_empty)
            return
        }
        notices.forEach { addNotice(it, project) }
    }

    /** 투표가 있는 공지마다 투표 결과 구독 (없어진 공지는 해제) */
    private fun syncVoteListeners(notices: List<Notice>) {
        val withPoll = notices.filter { it.poll != null }.associateBy { it.id }
        for (id in voteListeners.keys.toList()) {
            if (id !in withPoll) {
                voteListeners.remove(id)?.remove()
                votes.remove(id)
            }
        }
        for ((id, notice) in withPoll) {
            if (id in voteListeners) continue
            voteListeners[id] = TeamRepository.listenVotes(notice) { list ->
                votes[id] = list
                project?.let { render(data, it) }
            }
        }
    }

    fun stop() {
        voteListeners.values.forEach { it.remove() }
        voteListeners.clear()
    }

    private fun addNotice(notice: Notice, project: Project) {
        val row = ItemNoticeBinding.inflate(LayoutInflater.from(activity), b.list, false)
        row.pinIcon.isVisible = notice.pinned
        row.pollIcon.isVisible = notice.poll != null && !notice.pinned
        row.title.text = notice.title.ifBlank { notice.poll?.question ?: notice.body.lineSequence().firstOrNull().orEmpty() }
        row.body.text = notice.body
        row.body.isVisible = notice.body.isNotBlank() && notice.title.isNotBlank()
        val parts = mutableListOf(notice.authorName, Fmt.relativeTime(activity, notice.createdAt))
        val poll = notice.poll
        if (poll != null) {
            val list = votes[notice.id].orEmpty()
            parts.add(activity.getString(R.string.notice_voters, list.size))
            renderPoll(row.poll, notice, list)
        }
        row.meta.text = parts.joinToString(" · ")

        val canDelete = notice.authorUid == data.myUid || project.ownerUid == data.myUid
        row.more.setOnClickListener { view ->
            val popup = PopupMenu(activity, view)
            popup.menu.add(0, 1, 0, if (notice.pinned) R.string.notice_unpin else R.string.notice_pin_action)
            if (canDelete) popup.menu.add(0, 2, 1, R.string.action_delete)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> TeamRepository.setNoticePinned(notice, !notice.pinned, activity.failureToast())
                    2 -> Dialogs.confirm(activity, R.string.confirm_delete_notice, null, R.string.action_delete) {
                        TeamRepository.deleteNotice(notice, activity.failureToast())
                    }
                }
                true
            }
            popup.show()
        }
        b.list.addView(row.root)
    }

    /** 선택지마다: 내가 골랐는지 · 표 수 · 전체 대비 비율 */
    private fun renderPoll(container: LinearLayout, notice: Notice, list: List<Vote>) {
        val poll = notice.poll ?: return
        container.isVisible = true
        container.removeAllViews()
        val mine = list.firstOrNull { it.uid == data.myUid }?.optionIds.orEmpty()
        val voters = list.size
        for (option in poll.options) {
            val count = list.count { option.id in it.optionIds }
            val row = ItemPollOptionBinding.inflate(LayoutInflater.from(activity), container, false)
            val chosen = option.id in mine
            row.mark.setImageResource(if (chosen) R.drawable.ic_check_circle else R.drawable.ic_circle_outline)
            row.mark.imageTintList = ColorStateList.valueOf(activity.colorOf(if (chosen) R.color.primary else R.color.text_tertiary))
            row.text.text = option.text
            row.count.text = activity.getString(R.string.notice_votes, count)
            row.bar.progress = if (voters == 0) 0 else count * 100 / voters
            row.root.setOnClickListener {
                val next = when {
                    poll.multiple -> if (chosen) mine - option.id else mine + option.id
                    chosen -> emptyList()
                    else -> listOf(option.id)
                }
                TeamRepository.vote(notice, next, activity.failureToast())
            }
            container.addView(row.root)
        }
    }
}
