package com.modo.app.ui.search

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.modo.app.R
import com.modo.app.data.AppData
import com.modo.app.data.TaskStatus
import com.modo.app.data.TeamRepository
import com.modo.app.databinding.ActivitySearchBinding
import com.modo.app.logic.TaskRules
import com.modo.app.ui.common.Items
import com.modo.app.ui.common.applySystemBarsPadding
import com.modo.app.ui.common.ensureSignedIn
import com.modo.app.ui.common.failureToast
import com.modo.app.ui.project.ProjectActions
import com.modo.app.ui.project.ProjectActivity
import com.modo.app.ui.task.TaskEditActivity
import java.time.LocalDate

/** 업무·프로젝트 검색 (홈 상단 돋보기) */
class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding
    private var data = AppData()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ensureSignedIn()) return
        enableEdgeToEdge()
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsPadding()

        binding.back.setOnClickListener { finish() }
        binding.clear.setOnClickListener { binding.query.setText("") }

        // 입력할 때마다 결과를 바로 갱신
        binding.query.doAfterTextChanged { text ->
            binding.clear.isVisible = !text.isNullOrEmpty()
            render()
        }
        // 키 이벤트 ①: 키보드의 [검색] 버튼
        binding.query.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                submitSearch()
                true
            } else {
                false
            }
        }
        // 키 이벤트 ②: (실물/에뮬레이터 키보드) Enter 키
        binding.query.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP) {
                submitSearch()
                true
            } else {
                false
            }
        }

        // 데이터가 바뀌면(다른 팀원이 수정 등) 검색 결과도 다시 계산
        TeamRepository.data.observe(this) { newData ->
            data = newData
            render()
        }

        binding.query.requestFocus()
        if (savedInstanceState == null) {
            WindowCompat.getInsetsController(window, binding.query).show(WindowInsetsCompat.Type.ime())
        }
    }

    /** Enter·검색 버튼: 결과를 확정하고 키보드를 내려 목록을 넓게 보여줌 */
    private fun submitSearch() {
        render()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.query.windowToken, 0)
        binding.query.clearFocus()
    }

    private fun render() {
        val container = binding.results
        container.removeAllViews()

        val query = binding.query.text.toString().trim()
        if (query.isEmpty()) {
            Items.addEmpty(container, R.string.search_prompt)
            return
        }

        val today = LocalDate.now()
        val projects = data.projects.filter {
            it.name.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true)
        }
        val tasks = TaskRules.sortForList(
            data.allTasks.filter {
                it.title.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true)
            }
        )

        if (projects.isEmpty() && tasks.isEmpty()) {
            Items.addEmpty(container, R.string.search_empty)
            return
        }

        if (projects.isNotEmpty()) {
            Items.addHeader(container, getString(R.string.search_projects))
            for (project in projects) {
                Items.addProject(
                    container, project, data, today,
                    onClick = { ProjectActivity.start(this, project.id) },
                    onMore = { anchor -> ProjectActions.showMenu(this, anchor, project, data.myUid) },
                )
            }
        }

        if (tasks.isNotEmpty()) {
            Items.addHeader(container, getString(R.string.search_tasks))
            for (task in tasks) {
                Items.addTask(
                    container, task, data, today, showProject = true,
                    onToggle = { checked ->
                        TeamRepository.setTaskStatus(
                            task, if (checked) TaskStatus.DONE else TaskStatus.TODO, failureToast(),
                        )
                    },
                    onClick = { TaskEditActivity.start(this, task.projectId, task.id) },
                )
            }
        }
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, SearchActivity::class.java))
        }
    }
}
