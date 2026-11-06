package com.teamup.app.ui.main

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.navigation.NavigationBarView
import com.teamup.app.R
import com.teamup.app.data.AppData
import com.teamup.app.data.TeamRepository
import com.teamup.app.databinding.ActivityMainBinding
import com.teamup.app.logic.TaskRules
import com.teamup.app.ui.common.applySystemBarsPadding
import com.teamup.app.ui.common.ensureSignedIn
import com.teamup.app.ui.common.toast
import java.time.LocalDate

/** 하단 탭(홈·업무·일정·설정)을 가진 메인 화면 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var homeTab: HomeTab
    private lateinit var tasksTab: TasksTab
    private lateinit var scheduleTab: ScheduleTab
    private lateinit var settingsTab: SettingsTab
    private var lastBackPressed = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ensureSignedIn()) return
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsPadding(bottom = false) // 아래 여백은 BottomNavigationView가 처리

        homeTab = HomeTab(this, binding.home)
        tasksTab = TasksTab(this, binding.tasks)
        scheduleTab = ScheduleTab(this, binding.schedule)
        settingsTab = SettingsTab(this, binding.settings)

        // 가로 모드: 세로 공간이 좁으므로 탭 이름을 숨기고 아이콘만 (높이는 values-land/dimens.xml)
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            binding.bottomNav.labelVisibilityMode = NavigationBarView.LABEL_VISIBILITY_UNLABELED
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            showTab(item.itemId)
            true
        }
        // 화면 회전 등으로 다시 만들어지면 보던 탭과 필터 복원
        if (savedInstanceState != null) {
            homeTab.restoreState(savedInstanceState)
            tasksTab.restoreState(savedInstanceState)
        }
        val selected = savedInstanceState?.getInt(KEY_TAB) ?: R.id.nav_home
        binding.bottomNav.selectedItemId = selected
        showTab(selected)

        TeamRepository.data.observe(this) { data -> render(data) }

        // 뒤로 가기: 다른 탭이면 홈으로, 홈이면 2초 안에 한 번 더 눌러야 종료
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.bottomNav.selectedItemId != R.id.nav_home) {
                    selectTab(R.id.nav_home)
                    return
                }
                val now = System.currentTimeMillis()
                if (now - lastBackPressed < 2000) {
                    finish()
                } else {
                    lastBackPressed = now
                    toast(R.string.msg_press_back_again)
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        // 날짜가 바뀌었을 수 있으니(D-day, 인사말) 다시 그림
        if (::binding.isInitialized) render(TeamRepository.current)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (!::binding.isInitialized) return
        outState.putInt(KEY_TAB, binding.bottomNav.selectedItemId)
        homeTab.saveState(outState)
        tasksTab.saveState(outState)
    }

    fun selectTab(itemId: Int) {
        binding.bottomNav.selectedItemId = itemId
    }

    private fun showTab(itemId: Int) {
        binding.home.root.isVisible = itemId == R.id.nav_home
        binding.tasks.root.isVisible = itemId == R.id.nav_tasks
        binding.schedule.root.isVisible = itemId == R.id.nav_schedule
        binding.settings.root.isVisible = itemId == R.id.nav_settings
    }

    private fun render(data: AppData) {
        homeTab.render(data)
        tasksTab.render(data)
        scheduleTab.render(data)
        settingsTab.render(data)

        // 일정 탭 배지: 지연된 업무 수
        val today = LocalDate.now()
        val overdue = data.allTasks.count { TaskRules.isOverdue(it, today) }
        val badge = binding.bottomNav.getOrCreateBadge(R.id.nav_schedule)
        badge.isVisible = overdue > 0
        badge.number = overdue
    }

    companion object {
        private const val KEY_TAB = "selected_tab"
    }
}
