package com.teamup.app.ui.common

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.text.InputFilter
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.teamup.app.R
import com.teamup.app.data.Member
import com.teamup.app.data.TeamRepository
import com.teamup.app.databinding.DialogInputBinding
import com.teamup.app.logic.InviteCode
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

    fun pickTime(context: Context, initial: LocalTime, onPicked: (LocalTime) -> Unit) {
        TimePickerDialog(
            context,
            { _, hour, minute -> onPicked(LocalTime.of(hour, minute)) },
            initial.hour, initial.minute, true,
        ).show()
    }
}
