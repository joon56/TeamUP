package com.modo.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 기기 부팅이 끝나면(BOOT_COMPLETED) 시스템이 보내는 방송을 받는다.
 * 재부팅하면 AlarmManager 예약이 모두 사라지므로, 기기에 저장해 둔 알람을 다시 예약한다.
 * (RECEIVE_BOOT_COMPLETED 권한 + 매니페스트 정적 등록 필요)
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        AlarmScheduler.rescheduleAll(context)
    }
}
