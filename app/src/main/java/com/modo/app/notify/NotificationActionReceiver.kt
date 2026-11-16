package com.modo.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.app.RemoteInput

/**
 * 알림에 달린 버튼을 누르면 실행되는 리시버 (PendingIntent.getBroadcast).
 * - 업무 알람: 완료로 표시 · 10분 뒤 다시
 * - 채팅 배너: 바로 답장(RemoteInput) · 읽음으로 표시
 * 매니페스트에서 직접 만든 퍼미션으로 보호해 다른 앱이 이 방송을 흉내 낼 수 없다.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.data?.lastPathSegment ?: return // 업무 id 또는 채팅방 id
        val app = context.applicationContext
        when (intent.action) {
            ACTION_TASK_DONE -> keepAlive { finish ->
                AlarmActions.done(app, key, intent.getStringExtra(EXTRA_PROJECT_ID).orEmpty(), finish)
            }
            ACTION_SNOOZE -> AlarmActions.snooze(app, key)
            ACTION_CHAT_READ -> keepAlive { finish -> AppNotifier.markRead(app, key, finish) }
            ACTION_CHAT_REPLY -> {
                // 알림 입력칸에 쓴 글 꺼내기
                val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(Notifier.KEY_REPLY)?.toString()?.trim().orEmpty()
                if (text.isEmpty()) return
                keepAlive { finish -> AppNotifier.reply(app, key, text, finish) }
            }
        }
    }

    /**
     * 리시버는 onReceive가 끝나면 곧바로 정리될 수 있다.
     * 서버에 쓰는 동안 살아 있도록 goAsync()로 잠시 미루고, 끝나거나 제한 시간이 지나면 finish 한다.
     */
    private fun keepAlive(work: (finish: () -> Unit) -> Unit) {
        val pending = goAsync()
        val handler = Handler(Looper.getMainLooper())
        var finished = false
        val finish = {
            if (!finished) {
                finished = true
                pending.finish()
            }
        }
        handler.postDelayed(finish, MAX_WAIT_MS)
        work { handler.post(finish) }
    }

    companion object {
        const val ACTION_TASK_DONE = "com.modo.app.action.TASK_DONE"
        const val ACTION_SNOOZE = "com.modo.app.action.SNOOZE"
        const val ACTION_CHAT_REPLY = "com.modo.app.action.CHAT_REPLY"
        const val ACTION_CHAT_READ = "com.modo.app.action.CHAT_READ"
        const val EXTRA_PROJECT_ID = "project_id"

        /** 리시버는 10초 안에 끝나야 하므로 그보다 짧게 */
        private const val MAX_WAIT_MS = 8_000L
    }
}
