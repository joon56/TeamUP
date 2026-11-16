package com.modo.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat

/**
 * 앱 안에서 주고받는 방송(브로드캐스트).
 * 알람 서비스가 "알람이 멈췄다"는 방송을 보내면, 떠 있는 알람 화면이 동적 등록한 리시버로 받아 스스로 닫는다.
 * 다른 앱이 흉내 내지 못하도록 매니페스트에 직접 만든 퍼미션(signature)으로 보호한다.
 */
object AppEvents {

    /** 매니페스트 <permission> 이름. 같은 키로 서명한 앱(= 이 앱)만 보내고 받을 수 있음 */
    const val PERMISSION = "com.modo.app.permission.INTERNAL_EVENTS"

    /** 울리던 알람이 멈춤 (알람 화면을 닫으라는 신호) */
    const val ACTION_ALARM_STOPPED = "com.modo.app.event.ALARM_STOPPED"

    /** 방송 보내기: 이 앱 패키지로만, 퍼미션을 가진 수신자에게만 */
    fun send(context: Context, action: String) {
        context.sendBroadcast(Intent(action).setPackage(context.packageName), PERMISSION)
    }

    /** 동적 등록. 화면이 보이는 동안(onStart~onStop)만 등록하고 반드시 [unregister]로 해제할 것 */
    fun register(context: Context, receiver: BroadcastReceiver, vararg actions: String) {
        val filter = IntentFilter()
        for (action in actions) filter.addAction(action)
        ContextCompat.registerReceiver(context, receiver, filter, PERMISSION, null, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    fun unregister(context: Context, receiver: BroadcastReceiver) {
        try {
            context.unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            // 등록된 적이 없으면 무시
        }
    }
}
