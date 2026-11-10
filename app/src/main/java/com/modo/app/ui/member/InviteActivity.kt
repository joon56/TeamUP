package com.modo.app.ui.member

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import com.modo.app.R
import com.modo.app.data.DataHub
import com.modo.app.data.InviteRepository
import com.modo.app.data.InviteStatus
import com.modo.app.data.ProfileRepository
import com.modo.app.data.PublicProfile
import com.modo.app.data.TeamRepository
import com.modo.app.databinding.ActivityInviteBinding
import com.modo.app.ui.common.Dialogs
import com.modo.app.ui.common.Labels
import com.modo.app.ui.common.applySystemBarsPadding
import com.modo.app.ui.common.colorOf
import com.modo.app.ui.common.ensureSignedIn
import com.modo.app.ui.common.errorMessage
import com.modo.app.ui.common.failureToast
import com.modo.app.ui.common.toast
import com.modo.app.ui.project.ProjectActions

/**
 * 팀원 초대 (회의 피드백 5).
 * 이름만 넣으면 바로 추가되던 방식 대신, 태그(이름#1234)나 가입 이메일로 정확한 사람을 찾아
 * 프로필을 확인하고 초대 → 상대가 알림에서 수락해야 팀원이 된다.
 * 앱을 못 쓰는 팀원(아이폰 등)은 아래 보조 메뉴로 이름만 추가할 수 있다.
 */
class InviteActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInviteBinding
    private lateinit var projectId: String
    private var found: PublicProfile? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ensureSignedIn()) return
        projectId = intent.getStringExtra(EXTRA_PROJECT_ID) ?: return finish()
        enableEdgeToEdge()
        binding = ActivityInviteBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsPadding()

        binding.topBar.title.setText(R.string.invite_title)
        binding.topBar.back.setOnClickListener { finish() }

        binding.find.setOnClickListener { search() }
        // 키 이벤트: 키보드의 검색(Enter) 키로도 찾기
        binding.queryInput.setOnEditorActionListener { _, actionId, event ->
            val enter = event?.keyCode == KeyEvent.KEYCODE_ENTER
            if (actionId == EditorInfo.IME_ACTION_SEARCH || enter) {
                if (event == null || event.action == KeyEvent.ACTION_UP) search()
                true
            } else {
                false
            }
        }
        binding.send.setOnClickListener { send() }

        binding.copyCode.setOnClickListener { DataHub.current.project(projectId)?.let { ProjectActions.copyInvite(this, it) } }
        binding.shareCode.setOnClickListener { DataHub.current.project(projectId)?.let { ProjectActions.shareInvite(this, it) } }
        binding.guestCard.setOnClickListener {
            Dialogs.showMember(this, null) { name, role ->
                TeamRepository.addGuestMember(projectId, name, role, failureToast())
                finish()
            }
        }

        DataHub.data.observe(this) { data ->
            val project = data.project(projectId)
            if (project == null) {
                if (!data.loading) finish()
                return@observe
            }
            binding.code.text = project.inviteCode
            found?.let { renderResult(it) }
        }
    }

    private fun hideKeyboard() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(binding.queryInput.windowToken, 0)
    }

    private fun search() {
        val query = binding.queryInput.text.toString()
        hideKeyboard()
        binding.find.isEnabled = false
        binding.message.isVisible = false
        binding.resultCard.isVisible = false
        found = null
        ProfileRepository.search(query) { result ->
            binding.find.isEnabled = true
            when (result) {
                is ProfileRepository.SearchResult.Found -> {
                    found = result.profile
                    renderResult(result.profile)
                }
                ProfileRepository.SearchResult.NotFound -> showMessage(getString(R.string.invite_not_found))
                ProfileRepository.SearchResult.Invalid -> showMessage(getString(R.string.invite_invalid))
                is ProfileRepository.SearchResult.Failed -> showMessage(errorMessage(result.error))
            }
        }
    }

    private fun showMessage(text: String) {
        binding.message.text = text
        binding.message.setTextColor(colorOf(R.color.danger))
        binding.message.isVisible = true
    }

    /** 찾은 사람 카드: 이미 팀원이거나 초대했으면 버튼 대신 상태 표시 */
    private fun renderResult(profile: PublicProfile) {
        val data = DataHub.current
        val project = data.project(projectId) ?: return
        binding.resultCard.isVisible = true
        val color = Labels.avatarColor(this, profile.avatarColor, profile.name)
        binding.avatar.text = profile.name.take(1)
        binding.avatar.backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 0x40))
        binding.avatar.setTextColor(color)
        binding.handle.text = profile.handle
        binding.school.text = profile.school.ifBlank { getString(R.string.me_school_none) }

        val isMember = profile.uid in project.memberUids
        val invited = data.invitesOf(projectId).any { it.toUid == profile.uid && it.status == InviteStatus.PENDING }
        val isMe = profile.uid == data.myUid
        binding.send.isEnabled = !isMember && !invited && !isMe
        binding.send.alpha = if (binding.send.isEnabled) 1f else 0.5f
        binding.send.text = getString(
            when {
                isMember -> R.string.invite_status_member
                invited -> R.string.invite_status_pending
                else -> R.string.invite_send
            }
        )
        binding.message.isVisible = isMe || isMember || invited
        binding.message.text = getString(
            when {
                isMe -> R.string.invite_self
                isMember -> R.string.invite_already_member
                else -> R.string.invite_already_invited
            }
        )
        binding.message.setTextColor(colorOf(R.color.text_secondary))
    }

    private fun send() {
        val profile = found ?: return
        val project = DataHub.current.project(projectId) ?: return
        binding.send.isEnabled = false
        InviteRepository.send(project, profile) { result ->
            when (result) {
                InviteRepository.SendResult.Sent -> {
                    toast(getString(R.string.invite_sent, profile.name))
                    renderResult(profile)
                }
                InviteRepository.SendResult.Self -> showMessage(getString(R.string.invite_self))
                InviteRepository.SendResult.AlreadyMember -> showMessage(getString(R.string.invite_already_member))
                InviteRepository.SendResult.AlreadyInvited -> showMessage(getString(R.string.invite_already_invited))
                is InviteRepository.SendResult.Failed -> {
                    binding.send.isEnabled = true
                    showMessage(errorMessage(result.error))
                }
            }
        }
    }

    companion object {
        private const val EXTRA_PROJECT_ID = "projectId"

        fun start(context: Context, projectId: String) {
            context.startActivity(Intent(context, InviteActivity::class.java).putExtra(EXTRA_PROJECT_ID, projectId))
        }
    }
}
