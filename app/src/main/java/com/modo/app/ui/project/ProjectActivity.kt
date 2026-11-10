package com.modo.app.ui.project

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.google.android.material.tabs.TabLayout
import com.modo.app.R
import com.modo.app.data.AppData
import com.modo.app.data.DataHub
import com.modo.app.data.Project
import com.modo.app.data.ProjectMode
import com.modo.app.data.TaskStatus
import com.modo.app.databinding.ActivityProjectBinding
import com.modo.app.logic.TaskRules
import com.modo.app.ui.common.Fmt
import com.modo.app.ui.common.colorOf
import com.modo.app.ui.common.ensureSignedIn
import com.modo.app.ui.common.toast
import com.modo.app.ui.nav.AppBar
import com.modo.app.ui.project.sections.BoardSection
import com.modo.app.ui.project.sections.ChatSection
import com.modo.app.ui.project.sections.NoticesSection
import com.modo.app.ui.project.sections.MembersSection
import com.modo.app.ui.project.sections.MinutesSection
import com.modo.app.ui.project.sections.MoreSection
import com.modo.app.ui.project.sections.OverviewProgressSection
import com.modo.app.ui.project.sections.OverviewTodaySection
import com.modo.app.ui.project.sections.ProjectScheduleSection
import com.modo.app.ui.project.sections.Section
import com.modo.app.ui.task.TaskEditActivity
import java.time.LocalDate

/**
 * 개별 프로젝트 홈 = 플로우 시안별 "화면 모드" (회의 피드백 1·8).
 * - 오늘 중심 / 진행률 중심: 상단 탭 6개, 첫 화면 개요
 * - 워크스페이스: 하단 탭 5개, 첫 화면 보드
 * - 소통 중심: 위 띠 + 칩 툴바, 첫 화면 팀 채팅
 * 모드는 프로젝트 문서에 저장되고, 바뀌면 화면 구성을 즉시 다시 만든다.
 */
class ProjectActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProjectBinding
    private lateinit var appBar: AppBar
    private lateinit var projectId: String

    private var mode: ProjectMode? = null
    private var tabs: List<ProjectTab> = emptyList()
    private var current: ProjectTab? = null
    private var pendingTab: ProjectTab? = null
    private val sections = mutableMapOf<ProjectTab, Section>()
    private var lastData = AppData()
    private var syncingSelection = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ensureSignedIn()) return
        projectId = intent.getStringExtra(EXTRA_PROJECT_ID) ?: return finish()
        enableEdgeToEdge()
        binding = ActivityProjectBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // 하단 탭(워크스페이스 모드)이 있으면 아래 여백은 BottomNavigationView가 처리
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottom = if (binding.bottomNav.isVisible) 0 else maxOf(bars.bottom, ime.bottom)
            view.setPadding(bars.left, bars.top, bars.right, bottom)
            insets
        }

        appBar = AppBar(this, binding.appBar)
        binding.appBar.more.isVisible = true
        binding.appBar.more.setOnClickListener { view ->
            DataHub.current.project(projectId)?.let { ProjectActions.showMenu(this, view, it, DataHub.current.myUid) { finish() } }
        }

        pendingTab = (savedInstanceState?.getString(KEY_TAB) ?: intent.getStringExtra(EXTRA_TAB))
            ?.let { name -> ProjectTab.entries.firstOrNull { it.name == name } }

        binding.topTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (!syncingSelection) (tab.tag as? ProjectTab)?.let { showTab(it) }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        binding.bottomNav.setOnItemSelectedListener { item ->
            if (!syncingSelection) tabs.getOrNull(bottomIndex(item.itemId))?.let { showTab(it) }
            true
        }
        binding.toolbar.setOnCheckedChangeListener { group, checkedId ->
            if (syncingSelection || checkedId == View.NO_ID) return@setOnCheckedChangeListener
            (group.findViewById<View>(checkedId)?.tag as? ProjectTab)?.let { showTab(it) }
        }

        DataHub.data.observe(this) { render(it) }

        // 뒤로 가기: 첫 탭이 아니면 첫 탭으로, 첫 탭이면 루트로
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val first = tabs.firstOrNull()
                if (first != null && current != first) showTab(first) else finish()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        current?.let { sections[it]?.onResume() }
    }

    fun currentProject(): Project? = if (::projectId.isInitialized) DataHub.current.project(projectId) else null

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        current?.let { outState.putString(KEY_TAB, it.name) }
    }

    override fun onDestroy() {
        sections.values.forEach { it.onDestroy() }
        super.onDestroy()
    }

    private fun bottomIndex(itemId: Int): Int = when (itemId) {
        R.id.pnav_board -> 0
        R.id.pnav_schedule -> 1
        R.id.pnav_minutes -> 2
        R.id.pnav_chat -> 3
        else -> 4
    }

    private fun render(data: AppData) {
        lastData = data
        val project = data.project(projectId)
        if (project == null) {
            // 목록이 다 온 뒤에도 없으면 삭제·나가기·내보내기 된 것
            if (!data.loading) {
                toast(R.string.project_not_found)
                finish()
            }
            return
        }
        appBar.showProject(project)
        appBar.render(data)
        if (project.mode != mode) applyMode(project.mode)
        renderBand(data, project)
        current?.let { sections[it]?.render(data, project) }
    }

    /** 모드에 맞게 탭 구성을 다시 만든다 */
    private fun applyMode(newMode: ProjectMode) {
        val keep = current ?: pendingTab
        mode = newMode
        tabs = ProjectTab.tabsFor(newMode)
        sections.values.forEach { it.onDestroy() }
        sections.clear()
        binding.content.removeAllViews()
        current = null

        syncingSelection = true
        binding.topTabs.isVisible = newMode == ProjectMode.TODAY || newMode == ProjectMode.PROGRESS
        binding.topDivider.isVisible = binding.topTabs.isVisible
        binding.bottomNav.isVisible = newMode == ProjectMode.WORKSPACE
        binding.bottomDivider.isVisible = binding.bottomNav.isVisible
        binding.toolbarScroll.isVisible = newMode == ProjectMode.CHAT
        binding.chatBand.isVisible = newMode == ProjectMode.CHAT

        binding.topTabs.removeAllTabs()
        binding.toolbar.removeAllViews()
        when (newMode) {
            ProjectMode.TODAY, ProjectMode.PROGRESS -> for (tab in tabs) {
                binding.topTabs.addTab(binding.topTabs.newTab().setText(tab.label(newMode)).setTag(tab))
            }
            ProjectMode.WORKSPACE -> Unit // 메뉴(project_nav.xml) 순서 = tabs 순서
            ProjectMode.CHAT -> for (tab in tabs) addToolbarChip(tab, newMode)
        }
        syncingSelection = false
        ViewCompat.requestApplyInsets(binding.root)

        showTab(keep?.takeIf { it in tabs } ?: tabs.first())
    }

    private fun addToolbarChip(tab: ProjectTab, mode: ProjectMode) {
        val chip = RadioButton(this, null, 0, R.style.Widget_Modo_ChipRadio)
        chip.id = View.generateViewId()
        chip.tag = tab
        chip.setText(tab.label(mode))
        // 기본 스타일 없이 만든 RadioButton은 직접 클릭 가능하게 해야 함
        chip.isClickable = true
        chip.isFocusable = true
        chip.isSaveEnabled = false
        val iconSize = resources.getDimensionPixelSize(R.dimen.icon_s)
        androidx.core.content.ContextCompat.getDrawable(this, tab.icon)?.let { icon ->
            icon.setBounds(0, 0, iconSize, iconSize)
            chip.setCompoundDrawablesRelative(icon, null, null, null)
        }
        chip.compoundDrawablePadding = resources.getDimensionPixelSize(R.dimen.gap_xs)
        chip.compoundDrawableTintList = getColorStateList(R.color.selector_chip_text)
        val params = RadioGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.marginEnd = resources.getDimensionPixelSize(R.dimen.gap_s)
        binding.toolbar.addView(chip, params)
    }

    /** 탭 열기: 섹션은 처음 열 때 만들어 붙이고, 선택 표시(탭·하단 탭·칩)를 맞춤 */
    fun showTab(tab: ProjectTab) {
        if (tab !in tabs) return
        val section = sections.getOrPut(tab) { createSection(tab).also { binding.content.addView(it.root) } }
        sections.forEach { (key, value) -> value.root.isVisible = key == tab }
        current = tab
        DataHub.current.project(projectId)?.let { section.render(lastData, it) }

        syncingSelection = true
        val index = tabs.indexOf(tab)
        when (mode) {
            ProjectMode.TODAY, ProjectMode.PROGRESS -> binding.topTabs.getTabAt(index)?.select()
            ProjectMode.WORKSPACE -> binding.bottomNav.menu.getItem(index).isChecked = true
            ProjectMode.CHAT -> binding.toolbar.findViewWithTag<RadioButton>(tab)?.let { chip ->
                binding.toolbar.check(chip.id)
                // 고른 칩이 화면 밖에 걸쳐 있으면 보이도록 가로 스크롤
                binding.toolbarScroll.post {
                    val scroll = binding.toolbarScroll
                    val left = binding.toolbar.left + chip.left - scroll.paddingStart
                    val right = binding.toolbar.left + chip.right + scroll.paddingEnd - scroll.width
                    when {
                        scroll.scrollX > left -> scroll.smoothScrollTo(left, 0)
                        scroll.scrollX < right -> scroll.smoothScrollTo(right, 0)
                    }
                }
            }
            null -> Unit
        }
        syncingSelection = false
    }

    private fun createSection(tab: ProjectTab): Section {
        val inflater = layoutInflater
        val parent = binding.content
        return when (tab) {
            ProjectTab.OVERVIEW ->
                if (mode == ProjectMode.PROGRESS) OverviewProgressSection(this, inflater, parent)
                else OverviewTodaySection(this, inflater, parent)
            ProjectTab.BOARD -> BoardSection(this, inflater, parent)
            ProjectTab.SCHEDULE -> ProjectScheduleSection(this, inflater, parent)
            ProjectTab.MEMBERS -> MembersSection(this, inflater, parent)
            ProjectTab.MORE -> MoreSection(this, inflater, parent)
            ProjectTab.MINUTES -> MinutesSection(this, inflater, parent)
            ProjectTab.CHAT -> ChatSection(this, inflater, parent, DataHub.current.project(projectId)!!)
            ProjectTab.NOTICES -> NoticesSection(this, inflater, parent)
        }
    }

    /** 소통 중심 모드 위 띠: 오늘 내 할 일 수 · 가장 가까운 마감 */
    private fun renderBand(data: AppData, project: Project) {
        if (project.mode != ProjectMode.CHAT) return
        val today = LocalDate.now()
        val tasks = data.tasksOf(project.id)
        val mineToday = tasks.count { it.assigneeId == data.myUid && it.status != TaskStatus.DONE && !it.dueDate.isAfter(today) }
        binding.bandToday.text = getString(R.string.chat_band_today, mineToday)
        binding.bandToday.setOnClickListener { showTab(ProjectTab.BOARD) }
        val nearest = tasks.filter { it.status != TaskStatus.DONE && !it.dueDate.isBefore(today) }.minByOrNull { it.dueDate }
        if (nearest == null) {
            binding.bandDday.setText(R.string.chat_band_none)
            binding.bandDday.setTextColor(colorOf(R.color.text_tertiary))
            binding.bandDday.setOnClickListener(null)
        } else {
            binding.bandDday.text = getString(R.string.chat_band_dday, Fmt.dDay(this, nearest.dueDate, today), nearest.title)
            val urgent = TaskRules.dDay(nearest.dueDate, today) <= 1
            binding.bandDday.setTextColor(colorOf(if (urgent) R.color.danger else R.color.warning))
            binding.bandDday.setOnClickListener { TaskEditActivity.start(this, project.id, nearest.id) }
        }
    }

    companion object {
        private const val EXTRA_PROJECT_ID = "projectId"
        private const val EXTRA_TAB = "tab"
        private const val KEY_TAB = "project_tab"

        fun start(context: Context, projectId: String, tab: String? = null) {
            context.startActivity(
                Intent(context, ProjectActivity::class.java)
                    .putExtra(EXTRA_PROJECT_ID, projectId)
                    .putExtra(EXTRA_TAB, tab)
            )
        }
    }
}
