package com.modo.app.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.ListPopupWindow
import android.widget.PopupMenu
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import com.google.firebase.firestore.ListenerRegistration
import com.modo.app.R
import com.modo.app.data.AppData
import com.modo.app.data.ChatMessage
import com.modo.app.data.ChatRepository
import com.modo.app.data.DataHub
import com.modo.app.data.Member
import com.modo.app.data.MessageType
import com.modo.app.databinding.ItemMsgEventBinding
import com.modo.app.databinding.ItemMsgMineBinding
import com.modo.app.databinding.ItemMsgOtherBinding
import com.modo.app.databinding.ViewChatRoomBinding
import com.modo.app.logic.MessageTask
import com.modo.app.ui.common.Fmt
import com.modo.app.ui.common.Labels
import com.modo.app.ui.common.colorOf
import com.modo.app.ui.common.failureToast
import com.modo.app.ui.common.toast
import com.modo.app.ui.files.FileActions
import com.modo.app.ui.meeting.MeetingFinderActivity
import com.modo.app.ui.task.TaskEditActivity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 채팅방 본문 동작 (채팅방 화면 · 프로젝트 화면 채팅 탭 공용).
 * - 최근 [PAGE]개 실시간 구독, 위에서 "이전 메시지 더 보기"
 * - 같은 사람이 같은 분에 보낸 말풍선은 묶어서 이름·시간을 한 번만
 * - Enter(하드웨어 키)·키보드 [보내기]로 전송, Shift+Enter는 줄바꿈 (키 이벤트)
 * - @ 를 입력하면 팀원 고르기 → 멘션 (상대 알림에 표시)
 * - 말풍선 길게 누르기: 복사 / 할 일로 만들기 / 삭제
 */
class ChatRoomController(
    private val activity: AppCompatActivity,
    private val b: ViewChatRoomBinding,
    val roomId: String,
    private val onOpenNotices: () -> Unit,
) {
    private val projectId: String? = ChatRepository.projectIdOf(roomId)
    private var listener: ListenerRegistration? = null
    private var limit = PAGE
    private var messages: List<ChatMessage> = emptyList()
    private var firstLoad = true
    private val pendingMentions = mutableMapOf<String, String>() // uid → 이름
    private var mentionPopup: ListPopupWindow? = null
    private val zone = ZoneId.systemDefault()

    init {
        b.send.isEnabled = false // 빈 입력칸에서는 회색
        b.input.doAfterTextChanged { text ->
            b.send.isEnabled = !text.isNullOrBlank()
            maybeShowMentions(text?.toString().orEmpty(), b.input.selectionStart)
        }
        b.send.setOnClickListener { send() }
        b.input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                send()
                true
            } else {
                false
            }
        }
        b.input.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && !event.isShiftPressed) {
                if (event.action == KeyEvent.ACTION_UP) send()
                true
            } else {
                false
            }
        }
        b.loadMore.setOnClickListener {
            limit += PAGE
            listen()
        }
        b.pinnedBanner.setOnClickListener { onOpenNotices() }
        b.attach.isVisible = FileActions.isAvailable
        b.attach.setOnClickListener { FileActions.pickForChat(activity, roomId) }
    }

    fun start() {
        if (listener == null) listen()
    }

    fun stop() {
        listener?.remove()
        listener = null
        mentionPopup?.dismiss()
    }

    /** 화면이 다시 보이면 읽음 처리 */
    fun onResume() {
        ChatRepository.markRead(roomId)
    }

    /** 데이터가 바뀌어 다시 그릴 때: 화면이 보이는 중일 때만 읽음 처리 */
    fun markReadIfVisible() {
        if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) ChatRepository.markRead(roomId)
    }

    private fun listen() {
        listener?.remove()
        listener = ChatRepository.listenMessages(roomId, limit.toLong()) { list -> onMessages(list) }
    }

    private fun isNearBottom(): Boolean {
        val child = b.msgScroll.getChildAt(0) ?: return true
        return child.bottom - (b.msgScroll.height + b.msgScroll.scrollY) < 200
    }

    private fun scrollToBottom() {
        b.msgScroll.post { b.msgScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun onMessages(list: List<ChatMessage>) {
        val atBottom = isNearBottom()
        val newFromMe = list.size >= messages.size && list.lastOrNull()?.let { it.senderUid == DataHub.uid && it.id != messages.lastOrNull()?.id } == true
        messages = list
        renderMessages()
        b.loadMore.isVisible = list.size >= limit
        if (firstLoad || atBottom || newFromMe) scrollToBottom()
        firstLoad = false
        // 화면이 보이는 동안 온 메시지는 바로 읽음
        if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) ChatRepository.markRead(roomId)
    }

    /** 고정 공지 띠 (팀방만) */
    fun renderData(data: AppData) {
        val pid = projectId ?: return
        val notices = data.noticesOf(pid)
        val pinned = notices.firstOrNull { it.pinned }
        b.pinnedBanner.isVisible = notices.isNotEmpty()
        b.pinnedText.text = pinned?.let { it.title.ifBlank { it.body } }
            ?: notices.firstOrNull()?.let { it.title.ifBlank { it.body } }.orEmpty()
        b.noticeCount.text = activity.getString(R.string.chat_open_notices, notices.size)
    }

    private fun day(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    private fun minuteKey(millis: Long): Long = millis / 60_000

    private fun renderMessages() {
        b.msgList.removeAllViews()
        b.emptyText.isVisible = messages.isEmpty()
        val me = DataHub.uid
        for ((index, message) in messages.withIndex()) {
            val prev = messages.getOrNull(index - 1)
            val next = messages.getOrNull(index + 1)
            val today = day(message.createdAt)
            if (prev == null || day(prev.createdAt) != today) addDay(today)
            if (message.type !in BUBBLES) {
                addEvent(message)
                continue
            }
            val joinPrev = prev != null && prev.type in BUBBLES && prev.senderUid == message.senderUid &&
                message.createdAt - prev.createdAt < GROUP_MS && day(prev.createdAt) == today
            val joinNext = next != null && next.type in BUBBLES && next.senderUid == message.senderUid &&
                minuteKey(next.createdAt) == minuteKey(message.createdAt)
            if (message.senderUid == me) addMine(message, showTime = !joinNext)
            else addOther(message, showHeader = !joinPrev, showTime = !joinNext)
        }
    }

    private fun addDay(date: LocalDate) {
        val row = ItemMsgEventBinding.inflate(LayoutInflater.from(activity), b.msgList, false)
        row.text.text = date.format(DateTimeFormatter.ofPattern(activity.getString(R.string.chat_pattern_day), activity.resources.configuration.locales[0]))
        b.msgList.addView(row.root)
    }

    private fun addEvent(message: ChatMessage) {
        val row = ItemMsgEventBinding.inflate(LayoutInflater.from(activity), b.msgList, false)
        val (icon, text) = when (message.type) {
            MessageType.NOTICE -> R.drawable.ic_campaign to activity.getString(R.string.chat_last_notice, message.text)
            MessageType.POLL -> R.drawable.ic_poll to activity.getString(R.string.chat_last_poll, message.text)
            MessageType.TASK -> R.drawable.ic_add_task to activity.getString(R.string.chat_task_created, message.text)
            MessageType.SYSTEM -> (if (message.refId == MeetingFinderActivity.REF_MEETING) R.drawable.ic_event_available else 0) to message.text
            else -> 0 to message.text
        }
        row.icon.isVisible = icon != 0
        if (icon != 0) row.icon.setImageResource(icon)
        row.text.text = if (message.senderName.isNotBlank() && message.type != MessageType.SYSTEM)
            activity.getString(R.string.chat_sender_prefix, message.senderName, text) else text
        row.card.setOnClickListener {
            when (message.type) {
                MessageType.NOTICE, MessageType.POLL -> onOpenNotices()
                MessageType.TASK -> projectId?.let { pid -> message.refId?.let { TaskEditActivity.start(activity, pid, it) } }
                MessageType.SYSTEM -> if (message.refId == MeetingFinderActivity.REF_MEETING) projectId?.let { MeetingFinderActivity.start(activity, it) }
                else -> Unit
            }
        }
        b.msgList.addView(row.root)
    }

    private fun bindBody(message: ChatMessage, text: android.widget.TextView, image: android.widget.ImageView) {
        val file = message.file
        when {
            message.type == MessageType.IMAGE && file != null -> {
                image.isVisible = true
                FileActions.loadThumbnail(activity, file, image)
                text.isVisible = message.text.isNotBlank()
                text.text = message.text
            }
            message.type == MessageType.FILE && file != null -> {
                image.isVisible = false
                text.text = activity.getString(R.string.chat_last_file, file.name) + " · " + FileActions.formatSize(file.size)
            }
            else -> {
                image.isVisible = false
                text.text = highlightMentions(message.text)
            }
        }
    }

    private fun addMine(message: ChatMessage, showTime: Boolean) {
        val row = ItemMsgMineBinding.inflate(LayoutInflater.from(activity), b.msgList, false)
        bindBody(message, row.text, row.image)
        row.time.isVisible = showTime
        row.time.text = Fmt.time(activity, Instant.ofEpochMilli(message.createdAt).atZone(zone).toLocalTime())
        attachActions(row.bubble, message)
        b.msgList.addView(row.root)
    }

    private fun addOther(message: ChatMessage, showHeader: Boolean, showTime: Boolean) {
        val row = ItemMsgOtherBinding.inflate(LayoutInflater.from(activity), b.msgList, false)
        val member = memberOf(message.senderUid)
        val name = member?.name ?: message.senderName.ifBlank { activity.getString(R.string.chat_unknown_person) }
        val color = Labels.avatarColor(activity, member?.avatarColor ?: -1, name)
        row.avatar.visibility = if (showHeader) View.VISIBLE else View.INVISIBLE
        row.avatar.text = name.take(1)
        row.avatar.backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 0x40))
        row.avatar.setTextColor(color)
        row.name.isVisible = showHeader
        row.name.text = name
        bindBody(message, row.text, row.image)
        row.time.isVisible = showTime
        row.time.text = Fmt.time(activity, Instant.ofEpochMilli(message.createdAt).atZone(zone).toLocalTime())
        if (!showHeader) (row.root.layoutParams as? android.widget.LinearLayout.LayoutParams)?.topMargin = 2
        attachActions(row.bubble, message)
        b.msgList.addView(row.root)
    }

    private fun memberOf(uid: String): Member? {
        val pid = projectId ?: return null
        return DataHub.current.membersOf(pid).firstOrNull { it.uid == uid }
    }

    /** "@이름" 을 굵게 */
    private fun highlightMentions(text: String): CharSequence {
        if ('@' !in text) return text
        val span = SpannableString(text)
        Regex("@\\S+").findAll(text).forEach { match ->
            span.setSpan(StyleSpan(Typeface.BOLD), match.range.first, match.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return span
    }

    private fun attachActions(bubble: View, message: ChatMessage) {
        val file = message.file
        if (file != null) bubble.setOnClickListener { FileActions.open(activity, file) }
        bubble.setOnLongClickListener { view ->
            val popup = PopupMenu(activity, view)
            if (message.text.isNotBlank()) popup.menu.add(0, MENU_COPY, 0, R.string.chat_copy)
            if (projectId != null && message.text.isNotBlank()) popup.menu.add(0, MENU_TASK, 1, R.string.chat_to_task)
            if (message.senderUid == DataHub.uid) popup.menu.add(0, MENU_DELETE, 2, R.string.chat_delete)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_COPY -> {
                        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("MODO", message.text))
                        activity.toast(R.string.chat_copied)
                    }
                    MENU_TASK -> projectId?.let { pid ->
                        // 멘션된 팀원: 제목 앞 "@이름"은 떼고 첫 사람을 담당자로 미리 고름
                        val mentioned = message.mentions.mapNotNull { uid -> DataHub.current.membersOf(pid).firstOrNull { it.uid == uid } }
                        val title = MessageTask.title(message.text, mentioned.map { it.name })
                        TaskEditActivity.startFromMessage(activity, pid, title, roomId, message.id, mentioned.firstOrNull()?.id)
                    }
                    MENU_DELETE -> ChatRepository.deleteMessage(message, activity.failureToast())
                }
                true
            }
            popup.show()
            true
        }
    }

    // ───────────────────────── 보내기 · 멘션 ─────────────────────────

    private fun send() {
        val text = b.input.text.toString().trim()
        if (text.isEmpty()) return
        val mentions = pendingMentions.filter { (_, name) -> "@$name" in text }.keys.toList()
        ChatRepository.send(roomId, MessageType.TEXT, text, mentions = mentions, onFailure = activity.failureToast())
        b.input.setText("")
        pendingMentions.clear()
        scrollToBottom()
    }

    /** 커서 앞 단어가 "@..." 이면 팀원 목록을 띄움 (팀방만) */
    private fun maybeShowMentions(text: String, cursor: Int) {
        val pid = projectId ?: return
        if (cursor <= 0 || cursor > text.length) {
            mentionPopup?.dismiss()
            return
        }
        val start = text.lastIndexOf('@', cursor - 1)
        val token = if (start >= 0) text.substring(start, cursor) else ""
        val validStart = start >= 0 && (start == 0 || text[start - 1].isWhitespace()) && ' ' !in token
        if (!validStart) {
            mentionPopup?.dismiss()
            return
        }
        val query = token.drop(1)
        val candidates = DataHub.current.membersOf(pid)
            .filter { it.isAppUser && it.uid != DataHub.uid && it.name.contains(query, ignoreCase = true) }
        if (candidates.isEmpty()) {
            mentionPopup?.dismiss()
            return
        }
        val popup = mentionPopup ?: ListPopupWindow(activity).also { mentionPopup = it }
        popup.anchorView = b.input
        popup.setAdapter(ArrayAdapter(activity, android.R.layout.simple_list_item_1, candidates.map { it.handle }))
        popup.width = (activity.resources.displayMetrics.widthPixels * 0.6f).toInt()
        popup.setOnItemClickListener { _, _, position, _ ->
            val member = candidates[position]
            val current = b.input.text.toString()
            val end = b.input.selectionStart.coerceAtMost(current.length)
            val replaced = current.substring(0, start) + "@${member.name} " + current.substring(end)
            pendingMentions[member.uid!!] = member.name
            b.input.setText(replaced)
            b.input.setSelection((start + member.name.length + 2).coerceAtMost(replaced.length))
            popup.dismiss()
        }
        popup.show()
    }

    companion object {
        private const val PAGE = 150
        private const val GROUP_MS = 3 * 60_000L
        private val BUBBLES = setOf(MessageType.TEXT, MessageType.IMAGE, MessageType.FILE)
        private const val MENU_COPY = 1
        private const val MENU_TASK = 2
        private const val MENU_DELETE = 3
    }
}
