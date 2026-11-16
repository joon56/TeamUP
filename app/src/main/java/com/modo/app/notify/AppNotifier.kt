package com.modo.app.notify

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.Observer
import com.modo.app.R
import com.modo.app.data.AlarmStore
import com.modo.app.data.AppData
import com.modo.app.data.AuthService
import com.modo.app.data.ChatRepository
import com.modo.app.data.DataHub
import com.modo.app.data.FileStore
import com.modo.app.data.LastMessage
import com.modo.app.data.MessageType
import com.modo.app.data.Prefs
import com.modo.app.data.Session
import com.modo.app.data.TaskStatus
import com.modo.app.logic.AlarmCodec
import com.modo.app.logic.AlarmRules
import com.modo.app.logic.AlertRules
import com.modo.app.logic.DigestTask
import com.modo.app.logic.RoomTip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.ZoneId

/**
 * 앱이 살아 있는 동안 데이터([DataHub])를 지켜보다가 알림을 띄우는 곳.
 * - 새 채팅 메시지 → 배너
 * - 나에게 새로 배정된 업무 → 알림
 * - 내 업무 목록을 기기에 저장 (아침 마감 알림용) · 업무가 바뀌면 알람도 맞춤
 *
 * 서버가 보내는 푸시(FCM)가 아니라 앱이 직접 띄우는 알림이라, 앱이 완전히 꺼져 있으면 채팅 배너는 오지 않는다.
 */
object AppNotifier {

    private const val TAG = "AppNotifier"
    private const val MAX_LINES = 6
    private const val PICTURE_MAX_PX = 1024
    private const val OWN_WRITE_MS = 30_000L
    private const val ROOM_WAIT_MS = 6_000L
    private const val REPLY_SHOWN_MS = 1_500L

    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())

    /** 채팅 화면들이 등록해 두는 질문: "지금 보여 주고 있는 방 id" (화면이 안 보이면 null) */
    private val roomViewers = mutableListOf<() -> String?>()

    fun addRoomViewer(viewer: () -> String?) {
        roomViewers.add(viewer)
    }

    fun removeRoomViewer(viewer: () -> String?) {
        roomViewers.remove(viewer)
    }

    private var uid = ""
    private val seen = mutableMapOf<String, Long>()                           // 채팅방 → 마지막으로 본 메시지 시각
    private val lines = mutableMapOf<String, MutableList<Notifier.ChatLine>>() // 채팅방 → 배너에 쌓인 메시지
    private val assigned = mutableMapOf<String, Set<String>>()               // 프로젝트 → 내가 맡은 업무 id
    private val knownProjects = mutableSetOf<String>()
    private var lastDigestJson: String? = null

    fun start(context: Context) {
        app = context.applicationContext
        Notifier.createChannels(app)
        DataHub.data.observeForever(Observer { onData(it) })
    }

    private fun onData(data: AppData) {
        if (data.myUid != uid) reset(data.myUid)
        if (data.myUid.isEmpty()) return
        checkChats(data)
        checkAssignments(data)
        saveDigest(data)
        syncAlarms(data)
    }

    /** 로그인한 사람이 바뀌면 기억해 둔 것을 비움 */
    private fun reset(newUid: String) {
        uid = newUid
        seen.clear()
        lines.clear()
        assigned.clear()
        knownProjects.clear()
        lastDigestJson = null
    }

    /** 로그아웃: 알림 · 알람 · 저장해 둔 업무 목록을 모두 지움 (다음 사람에게 보이지 않게) */
    fun onSignedOut(context: Context) {
        AlarmRingService.silence(context)
        AlarmScheduler.clearAll(context)
        Notifier.cancelAll(context)
    }

    // ───────────────────────── 채팅 배너 ─────────────────────────

    private fun checkChats(data: AppData) {
        val tips = data.rooms.mapNotNull { room ->
            room.lastMessage?.let { RoomTip(room.id, it.at, it.senderUid, room.unreadFor(data.myUid)) }
        }
        if (Prefs.alertOn(Prefs.Alert.CHAT)) {
            // 지금 열어 보고 있는 방의 새 메시지는 배너를 띄우지 않음
            val openRoom = roomViewers.firstNotNullOfOrNull { it() }
            for (tip in AlertRules.roomsToAlert(seen, tips, data.myUid, openRoom, System.currentTimeMillis())) {
                showChat(data, tip.roomId)
            }
        }
        for (tip in tips) {
            seen[tip.roomId] = tip.lastAt
            // 방을 열었거나 다른 기기에서 읽었으면 배너를 치움
            if (tip.unread == 0 && lines.remove(tip.roomId) != null) Notifier.cancelChat(app, tip.roomId)
        }
    }

    private fun showChat(data: AppData, roomId: String) {
        val room = data.room(roomId) ?: return
        val last = room.lastMessage ?: return
        val isGroup = ChatRepository.projectIdOf(roomId) != null
        val title = roomTitle(data, roomId)
        val list = lines.getOrPut(roomId) { mutableListOf() }
        list.add(Notifier.ChatLine(last.senderName, previewText(last), last.at))
        while (list.size > MAX_LINES) list.removeAt(0)
        Notifier.chat(app, roomId, title, isGroup, list.toList())
        if (last.type == MessageType.IMAGE) loadPicture(roomId, title, isGroup, last.at)
    }

    /** 팀방은 프로젝트 이름, 1:1방은 상대 이름 */
    private fun roomTitle(data: AppData, roomId: String): String {
        data.project(ChatRepository.projectIdOf(roomId))?.let { return it.name }
        val room = data.room(roomId)
        return room?.otherUid(data.myUid)?.let { room.names[it] } ?: room?.lastMessage?.senderName.orEmpty()
    }

    private fun previewText(last: LastMessage): String = when (last.type) {
        MessageType.IMAGE -> app.getString(R.string.chat_last_image)
        MessageType.FILE -> app.getString(R.string.chat_last_file, last.text)
        MessageType.NOTICE -> app.getString(R.string.chat_last_notice, last.text)
        MessageType.POLL -> app.getString(R.string.chat_last_poll, last.text)
        MessageType.TASK -> app.getString(R.string.chat_last_task, last.text)
        else -> last.text
    }

    /** 사진 메시지: 사진을 받아서, 펼치면 크게 보이는 알림(BigPictureStyle)으로 바꿔 줌 */
    private fun loadPicture(roomId: String, title: String, isGroup: Boolean, at: Long) {
        ChatRepository.latestMessage(roomId) { message ->
            val file = message?.file?.takeIf { it.isImage } ?: return@latestMessage
            scope.launch {
                try {
                    val picture = withContext(Dispatchers.IO) { decodeSmall(FileStore.download(file.path)) } ?: return@launch
                    val list = lines[roomId] ?: return@launch          // 그 사이에 읽었으면 그만둠
                    if (list.lastOrNull()?.at != at) return@launch      // 그 사이에 새 메시지가 왔으면 그만둠
                    Notifier.chat(app, roomId, title, isGroup, list.toList(), picture, quiet = true)
                } catch (e: Exception) {
                    Log.w(TAG, "picture", e)
                }
            }
        }
    }

    /** 알림에 넣기 알맞게 줄여서 읽음 (큰 사진을 그대로 넣으면 알림이 실패함) */
    private fun decodeSmall(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / sample > PICTURE_MAX_PX || bounds.outHeight / sample > PICTURE_MAX_PX) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** 알림의 [읽음] 버튼 */
    fun markRead(context: Context, roomId: String, onDone: () -> Unit) {
        lines.remove(roomId)
        Notifier.cancelChat(context, roomId)
        if (!ensureSession()) return onDone()
        whenRoomLoaded(roomId, System.currentTimeMillis() + ROOM_WAIT_MS) { loaded ->
            if (loaded) ChatRepository.markRead(roomId)
            onDone()
        }
    }

    /** 알림 버튼은 앱이 꺼졌다가 다시 켜진 직후에도 눌릴 수 있으므로 데이터 구독을 먼저 켬 (이미 켜져 있으면 그대로) */
    private fun ensureSession(): Boolean {
        val myUid = AuthService.currentUid ?: return false
        Session.start(myUid, AuthService.fallbackName, AuthService.currentEmail)
        return true
    }

    /**
     * 알림의 [답장] 버튼. 앱이 방금 다시 켜진 상태라 채팅방 정보를 아직 못 받았으면 잠시 기다렸다가 보낸다.
     * @param onDone 보냈거나 못 보내고 끝났을 때
     */
    fun reply(context: Context, roomId: String, text: String, onDone: () -> Unit) {
        if (!ensureSession()) {
            Notifier.cancelChat(context, roomId)
            return onDone()
        }
        whenRoomLoaded(roomId, System.currentTimeMillis() + ROOM_WAIT_MS) { loaded ->
            if (!loaded) {
                Toast.makeText(context, R.string.notif_reply_failed, Toast.LENGTH_SHORT).show()
                lines.remove(roomId)
                Notifier.cancelChat(context, roomId)
                return@whenRoomLoaded onDone()
            }
            ChatRepository.send(roomId, MessageType.TEXT, text) { e -> Log.w(TAG, "reply", e) }
            ChatRepository.markRead(roomId)
            // 알림을 고쳐 줘야 입력칸의 "보내는 중" 표시가 끝남 → 보낸 글을 잠깐 보여 준 뒤 알림을 치움
            val data = DataHub.current
            val shown = lines.remove(roomId).orEmpty() + Notifier.ChatLine(data.myName, text, System.currentTimeMillis(), mine = true)
            Notifier.chat(context, roomId, roomTitle(data, roomId), ChatRepository.projectIdOf(roomId) != null, shown, quiet = true)
            handler.postDelayed({
                Notifier.cancelChat(context, roomId)
                onDone()
            }, REPLY_SHOWN_MS)
        }
    }

    private fun whenRoomLoaded(roomId: String, deadline: Long, then: (Boolean) -> Unit) {
        when {
            DataHub.rooms.any { it.id == roomId } -> then(true)
            System.currentTimeMillis() >= deadline -> then(false)
            else -> handler.postDelayed({ whenRoomLoaded(roomId, deadline, then) }, 300)
        }
    }

    // ───────────────────────── 업무 배정 ─────────────────────────

    private fun checkAssignments(data: AppData) {
        val now = System.currentTimeMillis()
        DataHub.ownTaskWrites.values.removeAll { now - it > OWN_WRITE_MS }
        for ((projectId, tasks) in data.tasks) {
            val mine = tasks.filter { it.assigneeId == data.myUid }
            val ids = mine.map { it.id }.toSet()
            val fresh = AlertRules.newlyAssigned(assigned[projectId], ids)
            assigned[projectId] = ids
            if (fresh.isEmpty() || !Prefs.alertOn(Prefs.Alert.ASSIGN)) continue
            val projectName = data.project(projectId)?.name.orEmpty()
            for (task in mine) {
                // 내가 방금 저장한 업무(스스로 맡은 것)와 이미 끝난 업무는 알리지 않음
                if (task.id !in fresh || task.status == TaskStatus.DONE || task.id in DataHub.ownTaskWrites) continue
                val due = Notifier.formatDue(app, AlarmRules.dueAt(task.dueDate, task.dueTime, ZoneId.systemDefault()))
                Notifier.assigned(app, projectId, task.id, task.title, projectName, due, task.description)
            }
        }
        assigned.keys.retainAll(data.tasks.keys)
    }

    // ───────────────────────── 아침 마감 알림에 쓸 목록 저장 ─────────────────────────

    private fun saveDigest(data: AppData) {
        if (data.loading) return
        val tasks = data.allTasks
            .filter { it.assigneeId == data.myUid && it.status != TaskStatus.DONE }
            .sortedBy { it.id }
            .map { DigestTask(it.id, it.projectId, it.title, data.project(it.projectId)?.name.orEmpty(), it.dueDate, it.dueTime) }
        val json = AlarmCodec.digestToJson(tasks)
        if (json == lastDigestJson) return
        lastDigestJson = json
        Prefs.digestJson = json
    }

    // ───────────────────────── 업무가 바뀌면 알람도 맞춤 ─────────────────────────

    private fun syncAlarms(data: AppData) {
        if (data.loading) return
        knownProjects.addAll(data.projects.map { it.id })
        val now = System.currentTimeMillis()
        for (alarm in AlarmStore.all()) {
            if (alarm.taskId == AlarmRingService.ringingTaskId) continue
            val project = data.project(alarm.projectId)
            if (project == null) {
                // 이번에 본 적 있는 프로젝트가 사라졌으면(삭제 · 나가기) 알람도 지움
                if (alarm.projectId in knownProjects) AlarmScheduler.clear(app, alarm.taskId)
                continue
            }
            if (!data.tasks.containsKey(alarm.projectId)) continue // 업무 목록을 아직 못 받음
            val task = data.task(alarm.projectId, alarm.taskId)
            if (task == null || task.status == TaskStatus.DONE) {
                AlarmScheduler.clear(app, alarm.taskId)
                Notifier.cancelAlarmNotices(app, alarm.taskId)
                continue
            }
            val dueAt = AlarmRules.dueAt(task.dueDate, task.dueTime, ZoneId.systemDefault())
            var updated = alarm
            if (dueAt != alarm.dueAt) {
                val moved = AlarmRules.rebase(alarm, dueAt, now)
                if (moved == null) {
                    AlarmScheduler.clear(app, alarm.taskId)
                    continue
                }
                updated = moved
            }
            if (task.title != updated.title || project.name != updated.projectName) {
                updated = updated.copy(title = task.title, projectName = project.name)
            }
            if (updated != alarm) AlarmScheduler.set(app, updated)
        }
    }
}
