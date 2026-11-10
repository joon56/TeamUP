package com.modo.app.ui.project

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.modo.app.R
import com.modo.app.data.ProjectMode

/** 프로젝트 화면의 탭(섹션) */
enum class ProjectTab(@DrawableRes val icon: Int) {
    OVERVIEW(R.drawable.ic_overview),
    BOARD(R.drawable.ic_board),
    SCHEDULE(R.drawable.ic_calendar),
    MINUTES(R.drawable.ic_minutes),
    CHAT(R.drawable.ic_forum),
    NOTICES(R.drawable.ic_campaign),
    MEMBERS(R.drawable.ic_group),
    MORE(R.drawable.ic_grid);

    /** 소통 중심 모드는 공지가 따로 있어서 "채팅", 나머지는 "채팅·공지" */
    @StringRes
    fun label(mode: ProjectMode): Int = when (this) {
        OVERVIEW -> R.string.tab_overview
        BOARD -> R.string.tab_board
        SCHEDULE -> R.string.tab_schedule
        MINUTES -> R.string.tab_minutes
        CHAT -> if (mode == ProjectMode.CHAT) R.string.tab_chat else R.string.tab_chat_notice
        NOTICES -> R.string.tab_notices
        MEMBERS -> R.string.tab_members
        MORE -> R.string.tab_more
    }

    companion object {
        /** 플로우 시안 1~4의 탭 구성 */
        fun tabsFor(mode: ProjectMode): List<ProjectTab> = when (mode) {
            ProjectMode.TODAY, ProjectMode.PROGRESS -> listOf(OVERVIEW, BOARD, SCHEDULE, MINUTES, CHAT, MEMBERS)
            ProjectMode.WORKSPACE -> listOf(BOARD, SCHEDULE, MINUTES, CHAT, MORE)
            ProjectMode.CHAT -> listOf(CHAT, NOTICES, BOARD, SCHEDULE, MINUTES, MEMBERS)
        }
    }
}
