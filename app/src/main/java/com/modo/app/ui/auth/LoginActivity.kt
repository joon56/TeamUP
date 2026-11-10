package com.modo.app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.modo.app.R
import com.modo.app.data.AuthService
import com.modo.app.data.Prefs
import com.modo.app.databinding.ActivityLoginBinding
import com.modo.app.logic.UserTag
import com.modo.app.ui.common.Swatches
import com.modo.app.ui.common.applySystemBarsPadding
import kotlin.random.Random
import com.modo.app.ui.common.errorMessage
import com.modo.app.ui.common.toast
import com.modo.app.ui.meeting.AvailabilityActivity
import com.modo.app.ui.root.RootActivity

/** 로그인 / 회원가입 (한 화면에서 모드 전환) */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private var signUpMode = false
    private var avatarColor = 0

    /** 요청 중에 다시 제출하면 계정이 두 번 만들어질 수 있으므로 막음 */
    private var submitting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsPadding()

        // 화면 회전 후에도 로그인/회원가입 모드 유지
        signUpMode = savedInstanceState?.getBoolean(KEY_SIGN_UP) ?: false
        avatarColor = savedInstanceState?.getInt(KEY_COLOR) ?: Random.nextInt(8)
        Swatches.build(binding.colorGroup, avatarColor) { avatarColor = it }
        updateMode()

        binding.submit.setOnClickListener { submit() }
        binding.switchMode.setOnClickListener {
            signUpMode = !signUpMode
            updateMode()
        }
        binding.forgot.setOnClickListener { resetPassword() }

        // 키 이벤트: 비밀번호 칸에서 키보드 [완료] 또는 Enter 키 → 바로 제출
        binding.passwordInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit()
                true
            } else {
                false
            }
        }
        binding.passwordInput.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                if (event.action == KeyEvent.ACTION_UP) submit()
                true // DOWN·UP 모두 소비 → 같은 Enter로 [완료] 동작이 한 번 더 실행되지 않음
            } else {
                false
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_SIGN_UP, signUpMode)
        outState.putInt(KEY_COLOR, avatarColor)
    }

    private fun updateMode() {
        binding.nameLayout.isVisible = signUpMode
        binding.signUpExtras.isVisible = signUpMode
        binding.forgot.isVisible = !signUpMode
        binding.submit.setText(if (signUpMode) R.string.action_signup else R.string.action_login)
        binding.switchMode.setText(if (signUpMode) R.string.login_switch_to_login else R.string.login_switch_to_signup)
        binding.errorText.isVisible = false
    }

    private fun submit() {
        if (submitting) return
        val name = binding.nameInput.text.toString().trim()
        val email = binding.emailInput.text.toString().trim()
        val password = binding.passwordInput.text.toString()

        binding.nameLayout.error = null
        binding.emailLayout.error = null
        binding.passwordLayout.error = null
        binding.errorText.isVisible = false

        var valid = true
        if (signUpMode && name.isEmpty()) {
            binding.nameLayout.error = getString(R.string.error_name_required)
            valid = false
        } else if (signUpMode && !UserTag.isValidName(name)) {
            binding.nameLayout.error = getString(R.string.error_name_chars)
            valid = false
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailLayout.error = getString(R.string.error_email_invalid)
            valid = false
        }
        if (password.length < 6) {
            binding.passwordLayout.error = getString(R.string.error_password_short)
            valid = false
        }
        if (!valid) return

        setLoading(true)
        val callback: (Exception?) -> Unit = { error ->
            setLoading(false)
            if (error == null) {
                // 처음 가입했으면 "보통 되는 시간"부터 (건너뛰기 가능)
                if (signUpMode) {
                    AvailabilityActivity.startOnboarding(this)
                    finish()
                } else {
                    openMain()
                }
            } else {
                binding.errorText.text = errorMessage(error)
                binding.errorText.isVisible = true
            }
        }
        try {
            if (signUpMode) {
                // 학교·아바타 색은 첫 로그인 때 프로필 문서를 만들면서 함께 저장
                Prefs.pendingProfile = Prefs.PendingProfile(binding.schoolInput.text.toString().trim(), avatarColor)
                AuthService.signUp(name, email, password, callback)
            } else {
                AuthService.signIn(email, password, callback)
            }
        } catch (e: IllegalStateException) {
            setLoading(false)
            toast(R.string.error_firebase_not_ready)
        }
    }

    private fun resetPassword() {
        val email = binding.emailInput.text.toString().trim()
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailLayout.error = getString(R.string.error_email_invalid)
            return
        }
        AuthService.sendPasswordReset(email) { error ->
            if (error == null) toast(R.string.msg_reset_sent) else toast(errorMessage(error))
        }
    }

    private fun setLoading(loading: Boolean) {
        submitting = loading
        binding.submit.isEnabled = !loading
        binding.switchMode.isEnabled = !loading
        binding.submit.text = if (loading) getString(R.string.loading)
        else getString(if (signUpMode) R.string.action_signup else R.string.action_login)
    }

    private fun openMain() {
        startActivity(
            Intent(this, RootActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }

    companion object {
        private const val KEY_SIGN_UP = "sign_up_mode"
        private const val KEY_COLOR = "avatar_color"
    }
}
