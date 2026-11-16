package com.modo.app.ui.common

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.DialogInterface
import android.text.InputFilter
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import com.google.android.material.textfield.TextInputLayout
import com.modo.app.R
import com.modo.app.data.Member
import com.modo.app.data.TeamRepository
import com.modo.app.data.UserProfile
import com.modo.app.databinding.DialogInputBinding
import com.modo.app.databinding.DialogProfileBinding
import com.modo.app.logic.InviteCode
import com.modo.app.logic.UserTag
import java.time.LocalDate
import java.time.LocalTime

object Dialogs {

    fun confirm(
        context: Context,
        @StringRes title: Int,
        @StringRes message: Int?,
        @StringRes positive: Int,
        onConfirm: () -> Unit,
    ) {
        val builder = AlertDialog.Builder(context)
            .setTitle(title)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(positive) { _, _ -> onConfirm() }
        if (message != null) builder.setMessage(message)
        builder.show()
    }

    /**
     * 저장 안 한 변경이 있을 때: [저장] [나가기] [계속 작성] 버튼 3개.
     * - 버튼 3개가 리스너 하나를 같이 쓰고, 어느 버튼인지는 which 값(BUTTON_POSITIVE 등)으로 구분
     * - 실수로 닫히면 안 되므로 뒤로 가기(setCancelable)와 바깥 터치(setCanceledOnTouchOutside)로는 닫히지 않게 함
     */
    fun confirmLeave(context: Context, onSave: () -> Unit, onDiscard: () -> Unit) {
        val listener = DialogInterface.OnClickListener { _, which ->
            when (which) {
                DialogInterface.BUTTON_POSITIVE -> onSave()
                DialogInterface.BUTTON_NEGATIVE -> onDiscard()
                DialogInterface.BUTTON_NEUTRAL -> Unit // 계속 작성: 창만 닫힘
            }
        }
        // 아이콘은 안드로이드에 들어 있는 그림(플랫폼 리소스 android.R.drawable.…)을 앱 색으로 물들여 씀
        val icon = AppCompatResources.getDrawable(context, android.R.drawable.ic_dialog_alert)?.mutate()
        if (icon != null) DrawableCompat.setTint(icon, context.colorOf(R.color.warning))
        val dialog = AlertDialog.Builder(context)
            .setIcon(icon)
            .setTitle(R.string.msg_leave_title)
            .setMessage(R.string.msg_leave_body)
            .setPositiveButton(R.string.action_save, listener)
            .setNegativeButton(R.string.action_discard, listener)
            .setNeutralButton(R.string.action_keep_editing, listener)
            .setCancelable(false)
            .create()
        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
    }

    /** 목록에서 하나만 고르기 (라디오 버튼 목록). 고르면 바로 닫힘 */
    fun choose(context: Context, @StringRes title: Int, labels: List<String>, checked: Int, onPicked: (index: Int) -> Unit) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setSingleChoiceItems(labels.toTypedArray(), checked) { dialog, which ->
                onPicked(which)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    /**
     * 목록에서 여러 개 고르기 (체크박스 목록). [확인]을 눌러야 반영됨.
     * @param checked 처음에 체크돼 있을 항목. 창 안에서 바뀌는 값은 복사본에 모아 둠
     */
    fun chooseMany(context: Context, @StringRes title: Int, labels: List<String>, checked: BooleanArray, onPicked: (BooleanArray) -> Unit) {
        val picked = checked.copyOf()
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMultiChoiceItems(labels.toTypedArray(), picked) { _, which, isChecked -> picked[which] = isChecked }
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_ok) { _, _ -> onPicked(picked) }
            .show()
    }

    /** 확인 버튼을 눌러도 검증에 실패하면 닫히지 않는 입력 다이얼로그 */
    private fun showInput(
        activity: Activity,
        @StringRes title: Int,
        binding: DialogInputBinding,
        @StringRes positive: Int,
        onPositive: (AlertDialog) -> Unit,
    ): AlertDialog {
        val dialog = AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(binding.root)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(positive, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { onPositive(dialog) }
            binding.input1.requestFocus()
        }
        dialog.show()
        return dialog
    }

    /** 홈 [코드로 참여] */
    fun showJoin(activity: Activity, onJoined: (projectId: String) -> Unit) {
        val binding = DialogInputBinding.inflate(LayoutInflater.from(activity))
        binding.message.visibility = View.VISIBLE
        binding.message.setText(R.string.join_body)
        binding.layout1.setHint(R.string.join_hint)
        binding.input1.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        binding.input1.filters = arrayOf(InputFilter.AllCaps(), InputFilter.LengthFilter(InviteCode.LENGTH + 2))

        showInput(activity, R.string.join_title, binding, R.string.action_join) { dialog ->
            val code = InviteCode.normalize(binding.input1.text.toString())
            if (!InviteCode.isValid(code)) {
                binding.layout1.error = activity.getString(R.string.join_not_found)
                return@showInput
            }
            binding.layout1.error = null
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
            TeamRepository.joinByCode(code, activity.getString(R.string.role_member)) { result ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                when (result) {
                    is TeamRepository.JoinResult.Joined -> {
                        dialog.dismiss()
                        onJoined(result.projectId)
                    }
                    is TeamRepository.JoinResult.AlreadyMember -> {
                        dialog.dismiss()
                        activity.toast(R.string.join_already)
                        onJoined(result.projectId)
                    }
                    TeamRepository.JoinResult.NotFound ->
                        binding.layout1.error = activity.getString(R.string.join_not_found)
                    is TeamRepository.JoinResult.Failed ->
                        binding.layout1.error = activity.errorMessage(result.error)
                }
            }
        }
    }

    /**
     * 팀원 추가(member == null) / 수정
     * @param pickContact 넘겨주면 이름 칸 오른쪽에 연락처 버튼이 생김. 고른 연락처의 이름을 콜백으로 돌려받아 칸에 채움
     */
    fun showMember(
        activity: Activity,
        member: Member?,
        pickContact: ((onName: (String) -> Unit) -> Unit)? = null,
        onSubmit: (name: String, role: String) -> Unit,
    ) {
        val binding = DialogInputBinding.inflate(LayoutInflater.from(activity))
        binding.layout1.setHint(R.string.hint_member_name)
        if (pickContact != null) {
            binding.layout1.endIconMode = TextInputLayout.END_ICON_CUSTOM
            binding.layout1.setEndIconDrawable(R.drawable.ic_contacts)
            binding.layout1.endIconContentDescription = activity.getString(R.string.member_from_contacts)
            binding.layout1.setEndIconOnClickListener {
                pickContact { name ->
                    binding.input1.setText(name)
                    binding.input1.setSelection(name.length)
                }
            }
        }
        binding.layout2.visibility = View.VISIBLE
        binding.layout2.setHint(R.string.hint_member_role)
        binding.input1.imeOptions = EditorInfo.IME_ACTION_NEXT
        binding.input2.imeOptions = EditorInfo.IME_ACTION_DONE
        if (member != null) {
            binding.input1.setText(member.name)
            binding.input2.setText(member.role)
        }
        val title = if (member == null) R.string.member_add_title else R.string.member_edit_title
        val positive = if (member == null) R.string.action_add else R.string.action_save
        showInput(activity, title, binding, positive) { dialog ->
            val name = binding.input1.text.toString().trim()
            if (name.isEmpty()) {
                binding.layout1.error = activity.getString(R.string.error_name_required)
                return@showInput
            }
            onSubmit(name, binding.input2.text.toString().trim())
            dialog.dismiss()
        }
    }

    /** 내 정보 → 프로필 수정 (이름·학교·아바타 색) */
    fun showProfile(activity: Activity, profile: UserProfile?, fallbackName: String, onSubmit: (name: String, school: String, color: Int) -> Unit) {
        val binding = DialogProfileBinding.inflate(LayoutInflater.from(activity))
        val name = profile?.name?.ifBlank { null } ?: fallbackName
        binding.nameInput.setText(name)
        binding.nameInput.setSelection(name.length)
        binding.schoolInput.setText(profile?.school.orEmpty())
        var color = profile?.avatarColor?.takeIf { it >= 0 } ?: 0
        Swatches.build(binding.colorGroup, color) { color = it }
        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.profile_dialog_title)
            .setView(binding.root)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_save, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val newName = binding.nameInput.text.toString().trim()
                when {
                    newName.isEmpty() -> binding.nameLayout.error = activity.getString(R.string.error_name_required)
                    !UserTag.isValidName(newName) -> binding.nameLayout.error = activity.getString(R.string.error_name_chars)
                    else -> {
                        onSubmit(newName, binding.schoolInput.text.toString().trim(), color)
                        dialog.dismiss()
                    }
                }
            }
        }
        dialog.show()
    }

    /** 설정 탭 이름 변경 */
    fun showName(activity: Activity, current: String, onSubmit: (String) -> Unit) {
        val binding = DialogInputBinding.inflate(LayoutInflater.from(activity))
        binding.layout1.setHint(R.string.hint_name)
        binding.input1.setText(current)
        binding.input1.setSelection(current.length)
        showInput(activity, R.string.name_dialog_title, binding, R.string.action_save) { dialog ->
            val name = binding.input1.text.toString().trim()
            if (name.isEmpty()) {
                binding.layout1.error = activity.getString(R.string.error_name_required)
                return@showInput
            }
            onSubmit(name)
            dialog.dismiss()
        }
    }

    fun pickDate(context: Context, initial: LocalDate, onPicked: (LocalDate) -> Unit) {
        DatePickerDialog(
            context,
            { _, year, month, day -> onPicked(LocalDate.of(year, month + 1, day)) },
            initial.year, initial.monthValue - 1, initial.dayOfMonth,
        ).show()
    }

    /**
     * 시간 선택창(TimePickerDialog). 테마에서 시계 대신 시·분을 돌리는 휠(spinner)로 바꿔 둠
     * (회의 피드백 7: 시계 모양 대신 일반적인 스크롤 방식). 마지막 true = 24시간 표기
     */
    fun pickTime(context: Context, initial: LocalTime, onPicked: (LocalTime) -> Unit) {
        TimePickerDialog(
            context,
            { _, hour, minute -> onPicked(LocalTime.of(hour, minute)) },
            initial.hour, initial.minute, true,
        ).show()
    }
}
