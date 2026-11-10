package com.modo.app.ui.project.sections

import android.view.View
import com.modo.app.data.AppData
import com.modo.app.data.Project

/**
 * 프로젝트 화면의 한 탭 화면. 모드(시안)마다 같은 섹션을 다른 자리에 배치한다.
 * 처음 열 때 만들어 content에 붙이고, 보일 때만 render 한다.
 */
interface Section {
    val root: View

    fun render(data: AppData, project: Project)

    /** 화면이 다시 보일 때 (채팅 읽음 처리 등) */
    fun onResume() {}

    fun onDestroy() {}
}
