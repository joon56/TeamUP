package com.modo.app.ui.chat

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.modo.app.R
import com.modo.app.data.AppData
import com.modo.app.data.ChatRepository
import com.modo.app.data.ChatRoom
import com.modo.app.data.DataHub
import com.modo.app.data.LastMessage
import com.modo.app.data.Member
import com.modo.app.data.MessageType
import com.modo.app.data.RoomType
import com.modo.app.databinding.ActivityChatListBinding
import com.modo.app.databinding.ItemChatRoomBinding
import com.modo.app.ui.common.Fmt
import com.modo.app.ui.common.Items
import com.modo.app.ui.common.Labels
import com.modo.app.ui.common.applySystemBarsPadding
import com.modo.app.ui.common.colorOf
import com.modo.app.ui.common.ensureSignedIn
import com.modo.app.ui.common.failureToast

/**
 * 채팅 목록 (회의 피드백 4: 카카오톡처럼).
 * 내가 속한 프로젝트 팀방 + 1:1 대화를 최근 대화순으로, 안 읽은 수와 함께.
 */
class ChatListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatListBinding
    private var filter = R.id.filterAll

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ensureSignedIn()) return
        enableEdgeToEdge()
        binding = ActivityChatListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsPadding()

        binding.topBar.title.setText(R.string.chat_title)
        binding.topBar.back.setOnClickListener { finish() }
        binding.topBar.action1.isVisible = true
        binding.topBar.action1.setImageResource(R.drawable.ic_add)
        binding.topBar.action1.contentDescription = getString(R.string.chat_new)
        binding.topBar.action1.setOnClickListener { pickPerson() }

        filter = savedInstanceState?.getInt(KEY_FILTER) ?: R.id.filterAll
        binding.filter.check(filter)
        binding.filter.setOnCheckedChangeListener { _, checkedId ->
            filter = checkedId
            render(DataHub.current)
        }
        DataHub.data.observe(this) { render(it) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::binding.isInitialized) outState.putInt(KEY_FILTER, filter)
    }

    private fun render(data: AppData) {
        binding.list.removeAllViews()
        // 아직 방 문서가 없는 프로젝트(방금 합류 등)는 목록에 안 보이므로 프로젝트가 있는 방만
        val rooms = data.rooms.filter { room ->
            when (filter) {
                R.id.filterTeam -> room.type == RoomType.PROJECT
                R.id.filterDirect -> room.type == RoomType.DIRECT
                else -> true
            } && (room.type == RoomType.DIRECT || data.project(room.projectId) != null)
        }
        if (rooms.isEmpty()) {
            val wrap = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                val pad = resources.getDimensionPixelSize(R.dimen.screen_padding)
                setPadding(pad, pad, pad, pad)
            }
            Items.addEmpty(wrap, R.string.chat_empty)
            binding.list.addView(wrap)
            return
        }
        rooms.forEach { addRoom(it, data) }
    }

    private fun addRoom(room: ChatRoom, data: AppData) {
        val row = ItemChatRoomBinding.inflate(LayoutInflater.from(this), binding.list, false)
        val project = data.project(room.projectId)
        if (room.type == RoomType.PROJECT && project != null) {
            val color = colorOf(Labels.projectColor(project.colorIndex))
            row.icon.isVisible = true
            row.avatar.isVisible = false
            row.icon.backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 0x38))
            row.icon.imageTintList = ColorStateList.valueOf(color)
            row.title.text = project.name
            row.memberCount.text = room.memberUids.size.toString()
            row.memberCount.isVisible = true
        } else {
            val other = room.otherUid(data.myUid)
            val name = other?.let { room.names[it] } ?: getString(R.string.chat_unknown_person)
            val color = Labels.avatarColor(this, avatarColorOf(other, data), name)
            row.icon.isVisible = false
            row.avatar.isVisible = true
            row.avatar.text = name.take(1)
            row.avatar.backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 0x40))
            row.avatar.setTextColor(color)
            row.title.text = name
            row.memberCount.isVisible = false
        }
        row.preview.text = room.lastMessage?.let { preview(it, room, data) } ?: getString(R.string.chat_room_empty)
        val time = room.lastMessage?.at ?: room.updatedAt
        row.time.text = if (time > 0) Fmt.relativeTime(this, time) else ""
        val unread = room.unreadFor(data.myUid)
        row.unread.isVisible = unread > 0
        row.unread.text = if (unread > 99) "99+" else unread.toString()
        row.root.setOnClickListener { ChatRoomActivity.start(this, room.id) }
        binding.list.addView(row.root)
    }

    /** 1:1 상대의 아바타 색은 같은 프로젝트 팀원 문서에서 찾음 */
    private fun avatarColorOf(uid: String?, data: AppData): Int =
        data.members.values.flatten().firstOrNull { it.uid == uid }?.avatarColor ?: -1

    private fun preview(last: LastMessage, room: ChatRoom, data: AppData): String {
        val body = when (last.type) {
            MessageType.IMAGE -> getString(R.string.chat_last_image)
            MessageType.FILE -> getString(R.string.chat_last_file, last.text)
            MessageType.NOTICE -> getString(R.string.chat_last_notice, last.text)
            MessageType.POLL -> getString(R.string.chat_last_poll, last.text)
            MessageType.TASK -> getString(R.string.chat_last_task, last.text)
            else -> last.text
        }
        // 팀방은 보낸 사람 이름을 앞에 (내가 보낸 건 생략)
        return if (room.type == RoomType.PROJECT && last.senderUid != data.myUid && last.senderName.isNotBlank())
            getString(R.string.chat_sender_prefix, last.senderName, body) else body
    }

    /** 새 1:1 대화: 내 프로젝트들의 앱 사용자 팀원 중에서 고름 */
    private fun pickPerson() {
        val data = DataHub.current
        val people: List<Member> = data.members.values.flatten()
            .filter { it.isAppUser && it.uid != data.myUid }
            .distinctBy { it.uid }
            .sortedBy { it.name }
        if (people.isEmpty()) {
            MaterialAlertDialogBuilder(this).setMessage(R.string.chat_no_people).setPositiveButton(R.string.action_ok, null).show()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.chat_pick_person)
            .setItems(people.map { it.handle }.toTypedArray()) { _, which ->
                val person = people[which]
                val roomId = ChatRepository.ensureDirectRoom(person.uid!!, person.name, failureToast())
                ChatRoomActivity.start(this, roomId)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    companion object {
        private const val KEY_FILTER = "chat_filter"

        fun start(context: Context) {
            context.startActivity(Intent(context, ChatListActivity::class.java))
        }
    }
}
