package com.modo.app.ui.root

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.navigation.NavigationBarView
import com.modo.app.R
import com.modo.app.data.AppData
import com.modo.app.data.DataHub
import com.modo.app.data.Prefs
import com.modo.app.databinding.ActivityRootBinding
import com.modo.app.logic.TaskFilter
import com.modo.app.notify.AlarmScheduler
import com.modo.app.notify.Notifier
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

    /** 종료 안내 토스트가 떠 있는 동안 true. 이때 뒤로 가기를 한 번 더 누르면 앱을 닫음 */
    private var exitArmed = false

    /** 알림 권한 요청 (Android 13+): 시스템 허용 창을 띄우고 결과를 받음 */
    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) toast(R.string.perm_notif_denied)
        if (::meTab.isInitialized) meTab.renderSettings()
    }

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
        binding.bottomNav.selectedItemId = R.id.nav_home
        showTab(R.id.nav_home)
        // 처음 켠 경우에만: 알림을 눌러 들어왔으면 그 알림이 가리키는 탭으로 (회전 뒤에는 onRestoreInstanceState가 되돌림)
        if (savedInstanceState == null) openTabFrom(intent)

        DataHub.data.observe(this) { data -> render(data) }

        // 뒤로 가기: 다른 탭이면 홈으로, 홈이면 안내 토스트가 떠 있는 동안 한 번 더 눌러야 종료
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    binding.bottomNav.selectedItemId != R.id.nav_home -> selectTab(R.id.nav_home)
                    exitArmed -> finish()
                    else -> showExitToast()
                }
            }
        })

        // 기기를 껐다 켰거나 앱이 강제 종료된 뒤에도 알람이 울리도록 예약을 다시 맞춤
        AlarmScheduler.rescheduleAll(this)
        if (savedInstanceState == null) askNotificationPermission()
    }

    /** 이미 떠 있는 상태에서 알림을 눌러 다시 불렸을 때 (새 인텐트가 여기로 옴) */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openTabFrom(intent)
    }

    /** 인텐트에 담긴 탭 번호(정수)를 꺼내 그 탭을 엶. 없으면 기본값 -1 */
    private fun openTabFrom(intent: Intent) {
        when (intent.getIntExtra(EXTRA_TAB, -1)) {
            TAB_HOME -> selectTab(R.id.nav_home)
            TAB_TASKS -> selectTab(R.id.nav_tasks)
            TAB_SCHEDULE -> selectTab(R.id.nav_schedule)
            TAB_ME -> selectTab(R.id.nav_me)
        }
    }

    /**
     * 화면이 다시 만들어진 뒤(예: 회전) 저장해 둔 상태를 되살림.
     * onStart 다음에 불리고, 저장된 상태가 있을 때만 불린다 (onCreate의 savedInstanceState와 같은 Bundle).
     */
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        val saved = savedInstanceState.getInt(KEY_TAB, R.id.nav_home)
        selectTab(saved)
        showTab(saved)
    }

    /** 다른 화면에 갔다가 돌아왔을 때(멈췄다가 다시 시작): 날짜가 바뀌었을 수 있으니 D-day · 오늘 표시를 다시 그림 */
    override fun onRestart() {
        super.onRestart()
        if (::binding.isInitialized) render(DataHub.current)
    }

    override fun onResume() {
        super.onResume()
        // 기기 설정에서 알림 권한을 바꾸고 돌아왔을 수 있으므로 설정 표시를 맞춤
        if (::meTab.isInitialized) meTab.renderSettings()
        Notifier.pruneSummary(this) // 알림을 밀어서 다 지웠으면 묶음 대표 알림도 정리
    }

    // ───────── "한 번 더 누르면 종료" 토스트 ─────────

    private fun showExitToast() {
        val toast = Toast.makeText(this, R.string.msg_press_back_again, Toast.LENGTH_SHORT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            armWhileShown(toast)
        } else {
            // API 30 미만: 토스트가 뜨고 사라지는 때를 알 수 없으므로, 지금부터 2초로 어림
            exitArmed = true
            binding.root.postDelayed({ exitArmed = false }, 2000)
        }
        toast.show()
    }

    /** API 30부터는 토스트가 뜨는 순간과 사라지는 순간을 콜백으로 알 수 있음 → 떠 있는 동안에만 "한 번 더"를 받아 줌 */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun armWhileShown(toast: Toast) {
        toast.addCallback(object : Toast.Callback() {
            override fun onToastShown() {
                exitArmed = true
            }

            override fun onToastHidden() {
                exitArmed = false
            }
        })
    }

    // ───────── 알림 권한 ─────────

    /** 처음 한 번: 왜 필요한지 먼저 알려 주고 시스템 허용 창을 띄움 (Android 13 미만은 허용이 필요 없음) */
    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || Prefs.notifPermissionAsked || Notifier.canPost(this)) return
        Prefs.notifPermissionAsked = true
        AlertDialog.Builder(this)
            .setTitle(R.string.perm_notif_title)
            .setMessage(R.string.perm_notif_body)
            .setNegativeButton(R.string.perm_notif_later, null)
            .setPositiveButton(R.string.settings_notif_allow) { _, _ -> requestNotificationPermission() }
            .show()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun requestNotificationPermission() {
        notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
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
        private const val EXTRA_TAB = "tab"

        const val TAB_HOME = 0
        const val TAB_TASKS = 1
        const val TAB_SCHEDULE = 2
        const val TAB_ME = 3

        /** 홈을 특정 탭으로 여는 인텐트 (알림에서 씀). 이미 떠 있으면 새로 만들지 않고 그 화면을 다시 씀 */
        fun intent(context: Context, tab: Int): Intent =
            Intent(context, RootActivity::class.java)
                .putExtra(EXTRA_TAB, tab)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
