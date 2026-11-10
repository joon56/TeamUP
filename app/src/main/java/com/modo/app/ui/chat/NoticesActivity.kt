package com.modo.app.ui.chat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.modo.app.R
import com.modo.app.data.DataHub
import com.modo.app.databinding.ActivityNoticesBinding
import com.modo.app.ui.common.applySystemBarsPadding
import com.modo.app.ui.common.ensureSignedIn

/** 공지 화면 (오늘·진행률·워크스페이스 모드에서 채팅방 위 고정 공지를 누르면) */
class NoticesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNoticesBinding
    private lateinit var controller: NoticeListController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ensureSignedIn()) return
        val projectId = intent.getStringExtra(EXTRA_PROJECT_ID) ?: return finish()
        enableEdgeToEdge()
        binding = ActivityNoticesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsPadding()
        binding.topBar.title.setText(R.string.notices_title)
        binding.topBar.back.setOnClickListener { finish() }

        controller = NoticeListController(this, binding.notices)
        DataHub.data.observe(this) { data ->
            val project = data.project(projectId)
            if (project == null) {
                if (!data.loading) finish()
                return@observe
            }
            controller.render(data, project)
        }
    }

    override fun onDestroy() {
        if (::controller.isInitialized) controller.stop()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_PROJECT_ID = "projectId"

        fun start(context: Context, projectId: String) {
            context.startActivity(Intent(context, NoticesActivity::class.java).putExtra(EXTRA_PROJECT_ID, projectId))
        }
    }
}
