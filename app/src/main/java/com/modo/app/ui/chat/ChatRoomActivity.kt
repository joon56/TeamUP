package com.modo.app.ui.chat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.modo.app.R
import com.modo.app.data.AppData
import com.modo.app.data.ChatRepository
import com.modo.app.data.DataHub
import com.modo.app.databinding.ActivityChatRoomBinding
import com.modo.app.ui.common.applySystemBarsPadding
import com.modo.app.ui.common.ensureSignedIn
import com.modo.app.ui.nav.Nav
import com.modo.app.ui.project.ProjectTab
import com.modo.app.ui.root.RootActivity

/** 채팅방 화면 (채팅 목록 · 알림 · 팀원 상세에서 열림). 본문 동작은 [ChatRoomController] */
class ChatRoomActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatRoomBinding
    private lateinit var controller: ChatRoomController
    private lateinit var roomId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ensureSignedIn()) return
        roomId = intent.getStringExtra(EXTRA_ROOM_ID) ?: return finish()
        enableEdgeToEdge()
        binding = ActivityChatRoomBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsPadding()
        binding.topBar.back.setOnClickListener { finish() }

        val projectId = ChatRepository.projectIdOf(roomId)
        controller = ChatRoomController(this, binding.room, roomId) {
            projectId?.let { NoticesActivity.start(this, it) }
        }
        // 팀방이면 프로젝트로 가는 버튼
        if (projectId != null) {
            binding.topBar.action1.isVisible = true
            binding.topBar.action1.setImageResource(R.drawable.ic_folder)
            binding.topBar.action1.contentDescription = getString(R.string.tab_overview)
            binding.topBar.action1.setOnClickListener { Nav.openProject(this, projectId, ProjectTab.BOARD.name) }
        }

        DataHub.data.observe(this) { render(it) }
        controller.start()
    }

    private fun render(data: AppData) {
        val room = data.room(roomId)
        val projectId = ChatRepository.projectIdOf(roomId)
        binding.topBar.title.text = when {
            projectId != null -> data.project(projectId)?.name.orEmpty()
            room != null -> room.otherUid(data.myUid)?.let { room.names[it] }.orEmpty()
            else -> ""
        }
        controller.renderData(data)
    }

    override fun onResume() {
        super.onResume()
        if (::controller.isInitialized) controller.onResume()
    }

    /** 알림을 눌러 이 화면만 떠 있는 경우(아래에 홈이 없음): 닫을 때 홈을 띄워 줌 */
    override fun finish() {
        if (isTaskRoot && ::controller.isInitialized) startActivity(Intent(this, RootActivity::class.java))
        super.finish()
    }

    override fun onDestroy() {
        if (::controller.isInitialized) controller.stop()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_ROOM_ID = "roomId"

        /** 명시적 인텐트: 열 화면(클래스)을 직접 지정하고 방 id를 부가 데이터로 담음 */
        fun intent(context: Context, roomId: String): Intent =
            Intent(context, ChatRoomActivity::class.java).putExtra(EXTRA_ROOM_ID, roomId)

        fun start(context: Context, roomId: String) {
            context.startActivity(intent(context, roomId))
        }
    }
}
