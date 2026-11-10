package com.modo.app.data

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

/**
 * 저장소들이 받아 온 조각(프로필·프로젝트·업무·채팅방 …)을 모아 [AppData] 하나로 내보내는 곳.
 * 화면은 [data]를 observe 해서 다시 그린다.
 * 리스너 여러 개가 거의 동시에 바뀌어도 화면은 한 번만 다시 그리도록 publish를 한 프레임에 모은다.
 */
object DataHub {

    private val _data = MutableLiveData(AppData())
    val data: LiveData<AppData> = _data

    /** 현재 스냅샷 (리스너 밖에서 바로 읽을 때) */
    val current: AppData get() = _data.value ?: AppData()

    var uid = ""
        private set
    private var fallbackName = ""

    var profile: UserProfile? = null
    var projects: List<Project> = emptyList()
    var projectsLoaded = false
    val members = mutableMapOf<String, List<Member>>()
    val tasks = mutableMapOf<String, List<Task>>()
    val notices = mutableMapOf<String, List<Notice>>()
    val minutes = mutableMapOf<String, List<Minutes>>()
    val files = mutableMapOf<String, List<FileMeta>>()
    val projectInvites = mutableMapOf<String, List<Invite>>()
    var receivedInvites: List<Invite> = emptyList()
    var rooms: List<ChatRoom> = emptyList()
    var roomsLoaded = false
    var mentions: List<ChatMessage> = emptyList()

    private val handler = Handler(Looper.getMainLooper())
    private var scheduled = false
    private val publishRunnable = Runnable {
        scheduled = false
        _data.value = snapshot()
    }

    fun begin(userId: String, name: String) {
        reset()
        uid = userId
        fallbackName = name
        publishNow()
    }

    /** 프로젝트가 목록에서 빠지면 그 프로젝트의 조각을 지움 */
    fun dropProject(projectId: String) {
        members.remove(projectId)
        tasks.remove(projectId)
        notices.remove(projectId)
        minutes.remove(projectId)
        files.remove(projectId)
        projectInvites.remove(projectId)
    }

    fun reset() {
        handler.removeCallbacks(publishRunnable)
        scheduled = false
        uid = ""
        fallbackName = ""
        profile = null
        projects = emptyList()
        projectsLoaded = false
        members.clear()
        tasks.clear()
        notices.clear()
        minutes.clear()
        files.clear()
        projectInvites.clear()
        receivedInvites = emptyList()
        rooms = emptyList()
        roomsLoaded = false
        mentions = emptyList()
        _data.value = AppData()
    }

    /** 다음 프레임에 한 번 내보냄 */
    fun publish() {
        if (scheduled) return
        scheduled = true
        handler.post(publishRunnable)
    }

    fun publishNow() {
        handler.removeCallbacks(publishRunnable)
        scheduled = false
        _data.value = snapshot()
    }

    private fun snapshot() = AppData(
        myUid = uid,
        myName = profile?.name?.takeIf { it.isNotBlank() } ?: fallbackName,
        profile = profile,
        projects = projects,
        members = members.toMap(),
        tasks = tasks.toMap(),
        notices = notices.toMap(),
        minutes = minutes.toMap(),
        files = files.toMap(),
        projectInvites = projectInvites.toMap(),
        receivedInvites = receivedInvites,
        rooms = rooms,
        mentions = mentions,
        loading = !projectsLoaded,
    )
}
