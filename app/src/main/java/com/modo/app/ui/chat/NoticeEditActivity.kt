package com.modo.app.ui.chat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.modo.app.R
import com.modo.app.data.DataHub
import com.modo.app.data.Poll
import com.modo.app.data.PollOption
import com.modo.app.data.TeamRepository
import com.modo.app.databinding.ActivityNoticeEditBinding
import com.modo.app.ui.common.applySystemBarsPadding
import com.modo.app.ui.common.ensureSignedIn
import com.modo.app.ui.common.failureToast
import com.modo.app.ui.common.toast

/** 공지 작성: 고정 여부와 투표(선택지 2개 이상, 여러 개 선택 가능)를 함께. 올리면 팀 채팅방에도 알림 */
class NoticeEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNoticeEditBinding
    private lateinit var projectId: String
    private val optionInputs = mutableListOf<EditText>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ensureSignedIn()) return
        projectId = intent.getStringExtra(EXTRA_PROJECT_ID) ?: return finish()
        enableEdgeToEdge()
        binding = ActivityNoticeEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsPadding()

        binding.topBar.title.setText(R.string.notice_write)
        binding.topBar.back.setOnClickListener { finish() }
        binding.topBar.textAction.isVisible = true
        binding.topBar.textAction.setText(R.string.action_add)
        binding.topBar.textAction.setOnClickListener { save() }

        binding.pollSwitch.setOnCheckedChangeListener { _, checked -> binding.pollBox.isVisible = checked }
        binding.addOption.setOnClickListener { addOption() }
        repeat(2) { addOption() }
    }

    private fun addOption() {
        val layout = TextInputLayout(this) // 기본 textInputStyle = Widget.Modo.Input (둥근 입력칸)
        layout.hint = getString(R.string.notice_poll_option, optionInputs.size + 1)
        val input = TextInputEditText(layout.context)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        input.maxLines = 1
        layout.addView(input)
        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        params.topMargin = resources.getDimensionPixelSize(R.dimen.gap_s)
        binding.options.addView(layout, params)
        optionInputs.add(input)
    }

    private fun save() {
        val project = DataHub.current.project(projectId) ?: return finish()
        val title = binding.titleInput.text.toString().trim()
        val body = binding.bodyInput.text.toString().trim()
        val withPoll = binding.pollSwitch.isChecked
        binding.bodyLayout.error = null
        binding.pollError.isVisible = false

        var poll: Poll? = null
        if (withPoll) {
            val question = binding.questionInput.text.toString().trim()
            val options = optionInputs.map { it.text.toString().trim() }.filter { it.isNotEmpty() }
            if (options.size < 2) {
                binding.pollError.isVisible = true
                return
            }
            poll = Poll(
                question = question.ifBlank { title.ifBlank { getString(R.string.notice_add_poll) } },
                options = options.mapIndexed { index, text -> PollOption("o$index", text) },
                multiple = binding.multiple.isChecked,
            )
        } else if (body.isEmpty()) {
            binding.bodyLayout.error = getString(R.string.error_notice_body)
            return
        }
        TeamRepository.postNotice(project, title, body, binding.pinSwitch.isChecked, poll, failureToast())
        toast(R.string.msg_saved)
        finish()
    }

    companion object {
        private const val EXTRA_PROJECT_ID = "projectId"

        fun start(context: Context, projectId: String) {
            context.startActivity(Intent(context, NoticeEditActivity::class.java).putExtra(EXTRA_PROJECT_ID, projectId))
        }
    }
}
