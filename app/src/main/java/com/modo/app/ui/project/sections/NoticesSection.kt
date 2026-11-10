package com.modo.app.ui.project.sections

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.modo.app.data.AppData
import com.modo.app.data.Project
import com.modo.app.databinding.ViewNoticeListBinding
import com.modo.app.ui.chat.NoticeListController
import com.modo.app.ui.project.ProjectActivity

/** 소통 중심 모드(시안 4)의 공지 탭: 고정 공지 · 투표 */
class NoticesSection(host: ProjectActivity, inflater: LayoutInflater, parent: ViewGroup) : Section {

    private val b = ViewNoticeListBinding.inflate(inflater, parent, false)
    override val root: View get() = b.root
    private val controller = NoticeListController(host, b)

    override fun render(data: AppData, project: Project) {
        controller.render(data, project)
    }

    override fun onDestroy() {
        controller.stop()
    }
}
