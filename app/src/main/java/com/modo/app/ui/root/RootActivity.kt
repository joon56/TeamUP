package com.modo.app.ui.root

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.navigation.NavigationBarView
import com.modo.app.R
import com.modo.app.data.AppData
import com.modo.app.data.DataHub
import com.modo.app.databinding.ActivityRootBinding
import com.modo.app.logic.TaskFilter
import com.modo.app.ui.common.applySystemBarsPadding
import com.modo.app.ui.common.ensureSignedIn
import com.modo.app.ui.common.toast
import com.modo.app.ui.nav.AppBar

/**
 * 루트(전체 홈). 앱을 새로 켜면 항상 여기서 시작한다 (회의 피드백 1).
 * 상단 바 왼쪽(전환 시트)으로 프로젝트에 들어가고, 하단 탭은 전 프로젝트를 통합해서 보여준다.
 */
class RootActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRootBinding
    private lateinit var appBar: AppBar
    private lateinit var homeTab: HomeTab
    private lateinit var tasksTab: TasksTab
    private lateinit var scheduleTab: ScheduleTab
    private lateinit var meTab: MyInfoTab
    private var lastBackPressed = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ensureSignedIn()) return
        enableEdgeToEdge()
        binding = ActivityRootBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsPadding(bottom = false) // 아래 여백은 BottomNavigationView가 처리

        appBar = AppBar(this, binding.appBar)
        appBar.showRoot()
        homeTab = HomeTab(this, binding.home)
        tasksTab = TasksTab(this, binding.tasks)
        scheduleTab = ScheduleTab(this, binding.schedule)
        meTab = MyInfoTab(this, binding.me)

        // 가로 모드: 세로 공간이 좁으므로 탭 이름을 숨기고 아이콘만
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            binding.bottomNav.labelVisibilityMode = NavigationBarView.LABEL_VISIBILITY_UNLABELED
        }
        binding.bottomNav.setOnItemSelectedListener { item ->
            showTab(item.itemId)
            true
        }
        if (savedInstanceState != null) {
            homeTab.restoreState(savedInstanceState)
            tasksTab.restoreState(savedInstanceState)
            scheduleTab.restoreState(savedInstanceState)
        }
        val selected = savedInstanceState?.getInt(KEY_TAB) ?: R.id.nav_home
        binding.bottomNav.selectedItemId = selected
        showTab(selected)

        DataHub.data.observe(this) { data -> render(data) }

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
        // 날짜가 바뀌었을 수 있으니(D-day, 오늘 표시) 다시 그림
        if (::binding.isInitialized) render(DataHub.current)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (!::binding.isInitialized) return
        outState.putInt(KEY_TAB, binding.bottomNav.selectedItemId)
        homeTab.saveState(outState)
        tasksTab.saveState(outState)
        scheduleTab.saveState(outState)
    }

    fun selectTab(itemId: Int) {
        binding.bottomNav.selectedItemId = itemId
    }

    /** 홈 "마감 임박 더 보기" → 할 일 탭의 해당 분류 */
    fun openTasks(filter: TaskFilter) {
        tasksTab.setFilter(filter)
        selectTab(R.id.nav_tasks)
    }

    private fun showTab(itemId: Int) {
        binding.home.root.isVisible = itemId == R.id.nav_home
        binding.tasks.root.isVisible = itemId == R.id.nav_tasks
        binding.schedule.root.isVisible = itemId == R.id.nav_schedule
        binding.me.root.isVisible = itemId == R.id.nav_me
    }

    private fun render(data: AppData) {
        appBar.render(data)
        homeTab.render(data)
        tasksTab.render(data)
        scheduleTab.render(data)
        meTab.render(data)
    }

    companion object {
        private const val KEY_TAB = "root_tab"
    }
}
