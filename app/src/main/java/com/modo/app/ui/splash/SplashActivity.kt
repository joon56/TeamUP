package com.modo.app.ui.splash

import android.animation.Animator
import android.animation.AnimatorInflater
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.annotation.AnimatorRes
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.modo.app.R
import com.modo.app.data.AuthService
import com.modo.app.databinding.ActivitySplashBinding
import com.modo.app.ui.auth.LoginActivity
import com.modo.app.ui.common.applySystemBarsPadding
import com.modo.app.ui.root.RootActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 시작 화면(인트로). 로고를 잠깐 보여 준 뒤, 로그인돼 있으면 홈으로 아니면 로그인 화면으로 보낸다.
 * 매니페스트에서 LAUNCHER로 지정해 앱 아이콘을 누르면 가장 먼저 뜬다.
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val animators = mutableListOf<Animator>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 앱이 이미 떠 있는데(다른 화면을 보던 중) 알림 등으로 이 화면이 그 위에 다시 불린 경우:
        // 처음부터 다시 시작하지 않고 바로 닫아서, 보던 화면이 그대로 앞으로 나오게 함
        if (!isTaskRoot) {
            finish()
            return
        }
        enableEdgeToEdge()
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsPadding()

        // res/animator 의 XML 애니메이션을 불러와 뷰에 연결. 점 3개는 시작 시각만 다르게
        play(R.animator.logo_pop, binding.logoTile, 0)
        play(R.animator.splash_dot, binding.dot1, 0)
        play(R.animator.splash_dot, binding.dot2, 150)
        play(R.animator.splash_dot, binding.dot3, 300)

        // 코루틴: 메인 스레드를 멈추지 않고 잠깐 기다림 (Thread.sleep으로 기다리면 화면이 멈추고 ANR이 날 수 있음).
        // 화면이 보이는 동안에만 진행하므로, 기다리는 중에 홈으로 나갔다 오면 다시 기다린 뒤 넘어감
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                delay(SHOW_MS)
                openNext()
            }
        }
    }

    private fun play(@AnimatorRes animator: Int, target: View, delayMs: Long) {
        val loaded = AnimatorInflater.loadAnimator(this, animator)
        loaded.setTarget(target)
        loaded.startDelay = delayMs
        loaded.start()
        animators.add(loaded)
    }

    private fun openNext() {
        val signedIn = try {
            AuthService.currentUid != null
        } catch (e: IllegalStateException) {
            false // google-services.json 없이 빌드한 경우
        }
        startActivity(Intent(this, if (signedIn) RootActivity::class.java else LoginActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        for (animator in animators) animator.cancel() // 무한 반복 애니메이션은 꼭 멈춰야 함
        super.onDestroy()
    }

    private companion object {
        const val SHOW_MS = 900L
    }
}
