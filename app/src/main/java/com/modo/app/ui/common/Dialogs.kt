package com.modo.app.ui.common

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Context
import android.text.InputFilter
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.modo.app.R
import com.modo.app.data.Member
import com.modo.app.data.TeamRepository
import com.modo.app.data.UserProfile
import com.modo.app.databinding.DialogInputBinding
import com.modo.app.databinding.DialogProfileBinding
import com.modo.app.databinding.DialogTimeWheelBinding
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
        val builder = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(positive) { _, _ -> onConfirm() }
        if (message != null) builder.setMessage(message)
        builder.show()
    }

    /** 확인 버튼을 눌러도 검증에 실패하면 닫히지 않는 입력 다이얼로그 */
    private fun showInput(
        activity: Activity,
        @StringRes title: Int,
        binding: DialogInputBinding,
        @StringRes positive: Int,
        onPositive: (AlertDialog) -> Unit,
    ): AlertDialog {
        val dialog = MaterialAlertDialogBuilder(activity)
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

    /** 팀원 추가(member == null) / 수정 */
    fun showMember(activity: Activity, member: Member?, onSubmit: (name: String, role: String) -> Unit) {
        val binding = DialogInputBinding.inflate(LayoutInflater.from(activity))
        binding.layout1.setHint(R.string.hint_member_name)
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
        val dialog = MaterialAlertDialogBuilder(activity)
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
     * 시간 선택: 시·분 스크롤 휠 (회의 피드백 7: 시계 모양 대신 일반적인 스크롤 방식).
     * 회의 시간이라 초는 없고, 분은 5분 단위.
     */
    fun pickTime(context: Context, initial: LocalTime, onPicked: (LocalTime) -> Unit) {
        val binding = DialogTimeWheelBinding.inflate(LayoutInflater.from(context))
        val minutes = (0 until 60 step MINUTE_STEP).toList()
        binding.hour.minValue = 0
        binding.hour.maxValue = 23
        binding.hour.displayedValues = Array(24) { "%02d".format(it) }
        binding.hour.value = initial.hour
        binding.minute.minValue = 0
        binding.minute.maxValue = minutes.lastIndex
        binding.minute.displayedValues = minutes.map { "%02d".format(it) }.toTypedArray()
        binding.minute.value = (initial.minute / MINUTE_STEP).coerceIn(0, minutes.lastIndex)
        binding.hour.wrapSelectorWheel = true
        binding.minute.wrapSelectorWheel = true
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.time_dialog_title)
            .setView(binding.root)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_ok) { _, _ ->
                onPicked(LocalTime.of(binding.hour.value, minutes[binding.minute.value]))
            }
            .show()
    }

    private const val MINUTE_STEP = 5
}
