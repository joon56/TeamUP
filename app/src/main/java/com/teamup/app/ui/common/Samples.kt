package com.teamup.app.ui.common

import android.content.Context
import com.teamup.app.R
import com.teamup.app.data.SampleData

/** 샘플 데이터 문구를 현재 언어의 리소스에서 읽음 */
fun Context.sampleLabels(): SampleData.Labels = SampleData.Labels(
    projectNames = resources.getStringArray(R.array.sample_project_names).toList(),
    projectDescriptions = resources.getStringArray(R.array.sample_project_descs).toList(),
    leaderRole = getString(R.string.role_leader),
    memberNames = resources.getStringArray(R.array.sample_member_names).toList(),
    memberRoles = resources.getStringArray(R.array.sample_member_roles).toList(),
    tasksA = resources.getStringArray(R.array.sample_tasks_a).toList(),
    tasksB = resources.getStringArray(R.array.sample_tasks_b).toList(),
    tasksC = resources.getStringArray(R.array.sample_tasks_c).toList(),
)
