package com.modo.app.ui.meeting

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.modo.app.R
import com.modo.app.data.AppData
import com.modo.app.data.DataHub
import com.modo.app.data.ProfileRepository
import com.modo.app.data.TeamRepository
import com.modo.app.databinding.ActivityAvailabilityBinding
import com.modo.app.logic.WeekAvailability
import com.modo.app.ui.common.Dialogs
import com.modo.app.ui.common.applySystemBarsPadding
import com.modo.app.ui.common.ensureSignedIn
import com.modo.app.ui.common.failureToast
import com.modo.app.ui.common.toast
import com.modo.app.ui.root.RootActivity

/**
 * 가능 시간 입력 (when2meet식 격자).
 * - 내 기본값 (내 정보): 참여 중인 프로젝트에도 적용할지 고름
 * - 프로젝트 팀원 (나, 또는 앱을 안 쓰는 팀원): 그 프로젝트에만
 * - 가입 직후: 건너뛸 수 있고, 저장하거나 건너뛰면 루트 홈으로
 */
class AvailabilityActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAvailabilityBinding
    private var projectId: String? = null
    private var memberId: String? = null
    private var onboarding = false

    /** 처음 불러온 값 (저장 안 한 변경 확인용). null이면 아직 못 불러옴 */
    private var initial: WeekAvailability? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ensureSignedIn()) return
        projectId = intent.getStringExtra(EXTRA_PROJECT_ID)
        memberId = intent.getStringExtra(EXTRA_MEMBER_ID)
        onboarding = intent.getBooleanExtra(EXTRA_ONBOARDING, false)
        enableEdgeToEdge()
        binding = ActivityAvailabilityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsPadding()

        val bar = binding.topBar
        bar.back.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        bar.title.setText(R.string.avail_title)
        if (onboarding) {
            bar.back.isVisible = false
            // 뒤로 가기 자리가 비므로 제목을 본문 여백에 맞춤
            bar.title.setPaddingRelative(resources.getDimensionPixelSize(R.dimen.gap_l), 0, 0, 0)
            bar.title.setText(R.string.avail_title_onboarding)
            bar.textAction.isVisible = true
            bar.textAction.setText(R.string.action_skip)
            bar.textAction.setOnClickListener { openRoot() }
            binding.help.setText(R.string.avail_help_onboarding)
            binding.saveButton.setText(R.string.avail_save_start)
        }

        // 화면 회전: 칠하던 값과 처음 값을 그대로
        val savedWeek = savedInstanceState?.getLongArray(KEY_WEEK)
        val savedInitial = savedInstanceState?.getLongArray(KEY_INITIAL)
        if (savedWeek != null && savedInitial != null) {
            initial = WeekAvailability(savedInitial.toList())
            binding.grid.week = WeekAvailability(savedWeek.toList())
        }
        updateSummary()

        binding.grid.onChange = { updateSummary() }
        binding.presetEvenings.setOnClickListener { setWeek(binding.grid.week + WeekAvailability.weekdayEvenings()) }
        binding.presetClear.setOnClickListener { setWeek(WeekAvailability()) }
        binding.presetDefault.setOnClickListener {
            val default = DataHub.current.profile?.availability
            if (default == null || default.isEmpty) toast(R.string.avail_default_empty) else setWeek(default)
        }
        binding.saveButton.setOnClickListener { save() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val start = initial
                when {
                    onboarding -> openRoot()
                    start != null && binding.grid.week != start -> Dialogs.confirm(
                        this@AvailabilityActivity, R.string.msg_discard_title, R.string.msg_discard_body, R.string.action_discard,
                    ) { finish() }
                    else -> finish()
                }
            }
        })

        DataHub.data.observe(this) { render(it) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val start = initial ?: return
        outState.putLongArray(KEY_WEEK, binding.grid.week.days.toLongArray())
        outState.putLongArray(KEY_INITIAL, start.days.toLongArray())
    }

    private fun render(data: AppData) {
        val pid = projectId
        if (pid != null) {
            val project = data.project(pid)
            val member = data.member(pid, memberId)
            if (project == null || member == null) {
                if (!data.loading) finish()
                return
            }
            val mine = member.uid == data.myUid
            binding.topBar.title.text = if (mine) getString(R.string.avail_title) else getString(R.string.avail_title_member, member.name)
            binding.help.text = getString(R.string.avail_help) + " " + getString(R.string.avail_scope_project, project.name)
            binding.presetDefault.isVisible = mine
            if (initial == null) load(member.availability ?: WeekAvailability())
        } else {
            // 가입 직후에는 프로필 문서가 아직 없을 수 있음 → 빈 격자로 시작하고 저장할 때 확인
            binding.applyAll.isVisible = !onboarding && data.projects.isNotEmpty()
            val profile = data.profile
            if (initial == null && (profile != null || onboarding)) load(profile?.availability ?: WeekAvailability())
        }
        binding.saveButton.isEnabled = initial != null
    }

    private fun load(week: WeekAvailability) {
        initial = week
        setWeek(week)
    }

    private fun setWeek(week: WeekAvailability) {
        binding.grid.week = week
        updateSummary()
    }

    private fun updateSummary() {
        val week = binding.grid.week
        binding.summary.text = if (week.isEmpty) getString(R.string.avail_selected_none)
        else getString(R.string.me_availability_summary, hours(week))
    }

    private fun save() {
        val week = binding.grid.week
        val pid = projectId
        val mid = memberId
        if (pid != null && mid != null) {
            // 다 지우면 "입력 안 함"으로 (회의 추천 인원에서 빠짐)
            TeamRepository.setMemberAvailability(pid, mid, week.takeUnless { it.isEmpty }, failureToast())
            toast(R.string.avail_saved)
            finish()
            return
        }
        // 프로필 문서가 생기기 전에 저장하면 이름 없는 문서가 생기므로 기다림
        if (DataHub.current.profile == null) {
            toast(R.string.avail_profile_loading)
            return
        }
        ProfileRepository.saveAvailability(week, applyToProjects = onboarding || binding.applyAll.isChecked, onFailure = failureToast())
        toast(R.string.avail_saved)
        if (onboarding) openRoot() else finish()
    }

    private fun openRoot() {
        startActivity(Intent(this, RootActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }

    companion object {
        private const val EXTRA_PROJECT_ID = "project_id"
        private const val EXTRA_MEMBER_ID = "member_id"
        private const val EXTRA_ONBOARDING = "onboarding"
        private const val KEY_WEEK = "av_week"
        private const val KEY_INITIAL = "av_initial"

        /** 일주일 가능 시간 → "12" 또는 "7.5" (시간) */
        fun hours(week: WeekAvailability): String {
            val hours = week.freeSlotCount() / 2.0
            return if (hours % 1.0 == 0.0) hours.toInt().toString() else hours.toString()
        }

        /** 내 기본 가능 시간 (내 정보) */
        fun startForProfile(context: Context) {
            context.startActivity(Intent(context, AvailabilityActivity::class.java))
        }

        /** 프로젝트 안에서 나 또는 앱을 안 쓰는 팀원의 가능 시간 */
        fun startForMember(context: Context, projectId: String, memberId: String) {
            context.startActivity(
                Intent(context, AvailabilityActivity::class.java)
                    .putExtra(EXTRA_PROJECT_ID, projectId)
                    .putExtra(EXTRA_MEMBER_ID, memberId)
            )
        }

        /** 회원가입 직후: 보통 되는 시간 (건너뛰기 가능) */
        fun startOnboarding(context: Context) {
            context.startActivity(
                Intent(context, AvailabilityActivity::class.java)
                    .putExtra(EXTRA_ONBOARDING, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
        }
    }
}
