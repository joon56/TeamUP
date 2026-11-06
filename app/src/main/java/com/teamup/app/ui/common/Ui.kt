package com.teamup.app.ui.common

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.teamup.app.R
import com.teamup.app.data.AppData
import com.teamup.app.data.Priority
import com.teamup.app.data.TaskStatus
import com.teamup.app.logic.TaskRules
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// ───────────────────────── 날짜·시간 표시 ─────────────────────────

object Fmt {
    private fun locale(context: Context): Locale = context.resources.configuration.locales[0]

    private fun format(context: Context, @StringRes pattern: Int, date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern(context.getString(pattern), locale(context)))

    /** 11월 6일 (금) */
    fun date(context: Context, date: LocalDate): String = format(context, R.string.pattern_date, date)

    /** 11.6 */
    fun dateShort(context: Context, date: LocalDate): String = format(context, R.string.pattern_date_short, date)

    /** 2026년 11월 6일 (금) */
    fun dateFull(context: Context, date: LocalDate): String = format(context, R.string.pattern_date_full, date)

    fun time(context: Context, time: LocalTime): String =
        time.format(DateTimeFormatter.ofPattern(context.getString(R.string.pattern_time), locale(context)))

    /** D-5 / D-Day / D+2 */
    fun dDay(context: Context, date: LocalDate, today: LocalDate): String {
        val d = TaskRules.dDay(date, today)
        return when {
            d == 0 -> context.getString(R.string.dday_today)
            d > 0 -> context.getString(R.string.dday_left, d)
            else -> context.getString(R.string.dday_passed, -d)
        }
    }

    /** 오늘 / 내일 / 11월 8일 (일) */
    fun relativeDay(context: Context, date: LocalDate, today: LocalDate): String = when (TaskRules.dDay(date, today)) {
        0 -> context.getString(R.string.today)
        1 -> context.getString(R.string.tomorrow)
        else -> date(context, date)
    }

    /** 1=월 ... 7=일 */
    fun weekday(context: Context, day: Int, long: Boolean = false): String {
        val names = context.resources.getStringArray(if (long) R.array.weekdays_long else R.array.weekdays_short)
        return names[(day - 1).coerceIn(names.indices)]
    }

    fun percent(context: Context, rate: Int?): String =
        if (rate == null) context.getString(R.string.no_tasks) else context.getString(R.string.percent, rate)
}

// ───────────────────────── 상태·우선순위·색 ─────────────────────────

object Labels {

    val PROJECT_COLORS = intArrayOf(
        R.color.project_0, R.color.project_1, R.color.project_2, R.color.project_3,
        R.color.project_4, R.color.project_5, R.color.project_6, R.color.project_7,
    )

    @ColorRes
    fun projectColor(index: Int): Int = PROJECT_COLORS[index.mod(PROJECT_COLORS.size)]

    fun status(context: Context, status: TaskStatus, overdue: Boolean = false): String = context.getString(
        when {
            overdue -> R.string.status_overdue
            status == TaskStatus.TODO -> R.string.status_todo
            status == TaskStatus.IN_PROGRESS -> R.string.status_in_progress
            else -> R.string.status_done
        }
    )

    @ColorRes
    fun statusColor(status: TaskStatus, overdue: Boolean = false): Int = when {
        overdue -> R.color.danger
        status == TaskStatus.DONE -> R.color.success
        status == TaskStatus.IN_PROGRESS -> R.color.info
        else -> R.color.text_secondary
    }

    @ColorRes
    fun statusContainer(status: TaskStatus, overdue: Boolean = false): Int = when {
        overdue -> R.color.danger_container
        status == TaskStatus.DONE -> R.color.success_container
        status == TaskStatus.IN_PROGRESS -> R.color.info_container
        else -> R.color.surface_high
    }

    fun priority(context: Context, priority: Priority): String = context.getString(
        when (priority) {
            Priority.HIGH -> R.string.priority_high
            Priority.MEDIUM -> R.string.priority_medium
            Priority.LOW -> R.string.priority_low
        }
    )

    @ColorRes
    fun priorityColor(priority: Priority): Int = when (priority) {
        Priority.HIGH -> R.color.danger
        Priority.MEDIUM -> R.color.warning
        Priority.LOW -> R.color.info
    }

    /** 담당자 이름. 없으면 "담당자 없음", 나간 팀원이면 "나간 팀원", 나면 "(나)" 표시 */
    fun assignee(context: Context, data: AppData, projectId: String, memberId: String?): String {
        if (memberId == null) return context.getString(R.string.none_assignee)
        val member = data.member(projectId, memberId) ?: return context.getString(R.string.unknown_member)
        return if (member.id == data.myUid) context.getString(R.string.name_with_me, member.name) else member.name
    }
}

fun Context.colorOf(@ColorRes res: Int): Int = ContextCompat.getColor(this, res)

fun Context.tintOf(@ColorRes res: Int): ColorStateList = ColorStateList.valueOf(colorOf(res))

// ───────────────────────── 터치 이벤트 ─────────────────────────

/** 누르는 동안 살짝 작아지는 효과. false를 돌려주므로 클릭 이벤트는 그대로 동작 */
@SuppressLint("ClickableViewAccessibility")
fun View.addPressEffect() {
    setOnTouchListener { view, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.97f).scaleY(0.97f).setDuration(80).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
        }
        false
    }
}

// ───────────────────────── 메시지 ─────────────────────────

fun Context.toast(@StringRes res: Int) = Toast.makeText(this, res, Toast.LENGTH_SHORT).show()

fun Context.toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

/** Firebase 예외를 사용자용 문장으로 */
fun Context.errorMessage(e: Exception?): String = getString(
    when (e) {
        is FirebaseNetworkException -> R.string.error_network
        is FirebaseTooManyRequestsException -> R.string.error_too_many
        is FirebaseAuthWeakPasswordException -> R.string.error_password_short
        is FirebaseAuthUserCollisionException -> R.string.error_email_in_use
        is FirebaseAuthInvalidCredentialsException, is FirebaseAuthInvalidUserException -> R.string.error_login_failed
        is FirebaseFirestoreException -> when (e.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> R.string.error_permission
            FirebaseFirestoreException.Code.UNAVAILABLE -> R.string.error_network
            else -> R.string.error_generic
        }
        else -> R.string.error_generic
    }
)

/** 쓰기 실패 콜백용: 화면이 닫혀도 안전하도록 applicationContext로 토스트 */
fun Context.failureToast(): (Exception) -> Unit {
    val app = applicationContext
    return { e -> app.toast(app.errorMessage(e)) }
}
