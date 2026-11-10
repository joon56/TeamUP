package com.modo.app.ui.project.sections

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.modo.app.data.AppData
import com.modo.app.data.ChatRepository
import com.modo.app.data.Project
import com.modo.app.data.ProjectMode
import com.modo.app.databinding.ViewChatRoomBinding
import com.modo.app.ui.chat.ChatRoomController
import com.modo.app.ui.chat.NoticesActivity
import com.modo.app.ui.project.ProjectActivity
import com.modo.app.ui.project.ProjectTab

/**
 * 프로젝트 화면의 팀 채팅 탭. 소통 중심 모드(시안 4)에서는 첫 화면.
 * 고정 공지를 누르면 소통 중심 모드는 공지 탭으로, 나머지 모드는 공지 화면으로.
 */
class ChatSection(private val host: ProjectActivity, inflater: LayoutInflater, parent: ViewGroup, project: Project) : Section {

    private val b = ViewChatRoomBinding.inflate(inflater, parent, false)
    override val root: View get() = b.root
    private val controller = ChatRoomController(host, b, ChatRepository.roomIdForProject(project.id)) {
        val current = host.currentProject()
        if (current?.mode == ProjectMode.CHAT) host.showTab(ProjectTab.NOTICES)
        else current?.let { NoticesActivity.start(host, it.id) }
    }

    init {
        controller.start()
    }

    override fun render(data: AppData, project: Project) {
        controller.renderData(data)
        controller.markReadIfVisible()
    }

    override fun onResume() {
        controller.onResume()
    }

    override fun onDestroy() {
        controller.stop()
    }
}
