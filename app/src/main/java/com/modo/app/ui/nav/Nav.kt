package com.modo.app.ui.nav

import android.app.Activity
import android.content.Intent
import com.modo.app.ui.project.ProjectActivity
import com.modo.app.ui.root.RootActivity

/**
 * 화면 체계(회의 피드백 1): 루트(전체 홈) 위에 프로젝트 화면이 하나만 쌓인다.
 * - 루트에서 프로젝트 선택 → 루트 위에 프로젝트
 * - 프로젝트 A에서 B로 전환 → A를 닫고 B (뒤로 가기는 항상 루트로)
 * - 전체 홈 → 루트 위의 화면을 모두 닫음
 */
object Nav {

    fun openProject(from: Activity, projectId: String, tab: String? = null) {
        ProjectActivity.start(from, projectId, tab)
        if (from is ProjectActivity) from.finish()
    }

    fun openRoot(from: Activity) {
        from.startActivity(
            Intent(from, RootActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        if (from !is RootActivity) from.finish()
    }
}
