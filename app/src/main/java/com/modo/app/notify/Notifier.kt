package com.modo.app.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.modo.app.R
import com.modo.app.logic.AlarmOption
import com.modo.app.logic.AlarmRules
import com.modo.app.logic.Digest
import com.modo.app.logic.DigestTask
import com.modo.app.logic.TaskAlarm
import com.modo.app.ui.alarm.AlarmActivity
import com.modo.app.ui.chat.ChatRoomActivity
import com.modo.app.ui.root.RootActivity
import com.modo.app.ui.task.TaskEditActivity
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 알림(Notification)을 만드는 곳.
 * 채널 5개: 채팅(배너) · 업무 알람 · 마감 알림 · 업무 배정 · 진행 상황(파일 올리기)
 */
object Notifier {

    const val CH_CHAT = "chat"
    const val CH_ALARM = "alarm"
    const val CH_DEADLINE = "deadline"
    const val CH_ASSIGN = "assign"
    const val CH_PROGRESS = "progress"

    const val ID_DIGEST = 1001
    const val ID_ALARM = 1002
    const val ID_UPLOAD = 1003

    /**
     * 이 앱의 알림은 모두 한 묶음(group)에 넣고, 묶음 대표(요약) 알림을 함께 올린다.
     * 묶음을 정해 두지 않으면 시스템(Android 16)이 스스로 묶으면서 알림을 "조용한 알림"으로 바꿔,
     * 다시 울리는 알람과 뒤이어 오는 채팅의 배너 · 알람 화면이 뜨지 않거나 금방 사라진다.
     */
    private const val GROUP_APP = "modo"
    private const val ID_SUMMARY = 1000

    private const val TAG_CHAT = "chat"
    private const val TAG_ASSIGN = "assign"
    private const val TAG_MISSED = "missed"

    /** 알림에서 바로 답장할 때 입력한 글을 꺼내는 키 (RemoteInput) */
    const val KEY_REPLY = "reply_text"

    private const val IMMUTABLE = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    /**
     * 알림 답장(RemoteInput)은 시스템이 입력한 글을 인텐트에 채워 넣으므로 "변경 가능"해야 한다.
     * FLAG_MUTABLE은 API 31에서 생겼고, 그 전에는 따로 지정하지 않아도 변경 가능.
     */
    private fun mutableFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

    private fun manager(context: Context) = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // ───────────────────────── 채널 ─────────────────────────

    /** 앱을 켤 때마다 호출. 같은 id로 다시 만들면 이름 · 설명만 갱신된다 (언어를 바꿨을 때) */
    fun createChannels(context: Context) {
        val chat = NotificationChannel(CH_CHAT, context.getString(R.string.channel_chat), NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.channel_chat_desc)
            setShowBadge(true) // 홈 화면 앱 아이콘에 배지
            // 알림음 + 어떤 종류의 소리인지(AudioAttributes)
            val sound: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val attributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()
            setSound(sound, attributes)
            enableLights(true)
            lightColor = ContextCompat.getColor(context, R.color.primary)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 120, 80, 120)
        }
        val alarm = NotificationChannel(CH_ALARM, context.getString(R.string.channel_alarm), NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.channel_alarm_desc)
            setShowBadge(false)
            // 소리와 진동은 AlarmRingService가 직접 낸다 (고른 알람음 · 30초 · 반복 진동)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val deadline = NotificationChannel(CH_DEADLINE, context.getString(R.string.channel_deadline), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = context.getString(R.string.channel_deadline_desc)
            setShowBadge(true)
        }
        val assign = NotificationChannel(CH_ASSIGN, context.getString(R.string.channel_assign), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = context.getString(R.string.channel_assign_desc)
            setShowBadge(true)
        }
        val progress = NotificationChannel(CH_PROGRESS, context.getString(R.string.channel_progress), NotificationManager.IMPORTANCE_LOW).apply {
            description = context.getString(R.string.channel_progress_desc)
            setShowBadge(false)
        }
        manager(context).createNotificationChannels(listOf(chat, alarm, deadline, assign, progress))
    }

    /** 알림을 띄울 수 있는지: Android 13(API 33)부터는 사용자가 허용해야 함 */
    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    @SuppressLint("MissingPermission") // canPost에서 확인함
    private fun post(context: Context, tag: String?, id: Int, notification: Notification) {
        if (!canPost(context)) return
        NotificationManagerCompat.from(context).notify(tag, id, notification)
        ensureSummary(context)
    }

    /** 묶음 대표 알림이 없으면 올림. 대표는 소리 · 배너 없이 묶음의 머리글 노릇만 한다 (알리는 것은 각 알림이 직접) */
    @SuppressLint("MissingPermission")
    fun ensureSummary(context: Context) {
        if (!canPost(context) || manager(context).activeNotifications.any { it.id == ID_SUMMARY && it.tag == null }) return
        val summary = base(context, CH_DEADLINE)
            .setContentTitle(context.getString(R.string.app_name))
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ID_SUMMARY, summary)
    }

    /** 묶음에 남은 알림이 없으면 대표 알림도 치움 (알림을 지운 뒤에 부름) */
    fun pruneSummary(context: Context) {
        val left = manager(context).activeNotifications.count {
            it.notification.group == GROUP_APP && it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0
        }
        if (left == 0) manager(context).cancel(ID_SUMMARY)
    }

    private fun base(context: Context, channel: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_modo)
            .setColor(ContextCompat.getColor(context, R.color.primary))
            .setWhen(System.currentTimeMillis())
            .setGroup(GROUP_APP)

    // ───────────────────────── 누르면 열릴 화면 (PendingIntent.getActivity) ─────────────────────────

    private fun openRoom(context: Context, roomId: String): PendingIntent =
        PendingIntent.getActivity(context, 0, ChatRoomActivity.intent(context, roomId).setData(uri("room", roomId)), IMMUTABLE)

    private fun openTask(context: Context, projectId: String, taskId: String): PendingIntent =
        PendingIntent.getActivity(context, 0, TaskEditActivity.intent(context, projectId, taskId).setData(uri("task", taskId)), IMMUTABLE)

    private fun openTasksTab(context: Context): PendingIntent =
        PendingIntent.getActivity(context, 0, RootActivity.intent(context, RootActivity.TAB_TASKS).setData(uri("root", "tasks")), IMMUTABLE)

    /** 같은 종류의 인텐트라도 대상마다 다른 PendingIntent가 되도록 data를 붙임 */
    private fun uri(kind: String, key: String): Uri = Uri.parse("modo://$kind/$key")

    /** 알림 버튼 → NotificationActionReceiver (PendingIntent.getBroadcast) */
    private fun actionBroadcast(
        context: Context, action: String, kind: String, key: String, flags: Int = IMMUTABLE, projectId: String = "",
    ): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java)
            .setAction(action)
            .setData(uri(kind, key))
            .putExtra(NotificationActionReceiver.EXTRA_PROJECT_ID, projectId)
        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }

    // ───────────────────────── 채팅 배너 (MessagingStyle + 바로 답장) ─────────────────────────

    /** 배너에 보여 줄 메시지 한 줄. mine = 내가 알림에서 답장한 글 */
    data class ChatLine(val sender: String, val text: String, val at: Long, val mine: Boolean = false)

    /**
     * 새 메시지 배너. 채널 중요도가 HIGH라서 화면 위에 내려온다(헤드업).
     * @param picture 사진 메시지면 펼쳤을 때 크게 보여 줄 사진 (BigPictureStyle)
     * @param quiet true면 소리 · 진동 없이 내용만 고침 (답장한 뒤 · 사진을 다 받았을 때)
     */
    fun chat(context: Context, roomId: String, title: String, isGroup: Boolean, lines: List<ChatLine>, picture: Bitmap? = null, quiet: Boolean = false) {
        if (lines.isEmpty()) return
        val last = lines.last()
        val builder = base(context, CH_CHAT)
            .setContentTitle(title)
            .setContentText(if (isGroup && !last.mine) "${last.sender}: ${last.text}" else last.text)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openRoom(context, roomId))
            .setAutoCancel(true)
            .setOnlyAlertOnce(quiet)
            .setWhen(last.at)

        if (picture != null) {
            builder.setLargeIcon(picture)
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(picture)
                    .bigLargeIcon(null as Bitmap?) // 펼치면 작은 미리보기는 숨김
                    .setBigContentTitle(title)
                    .setSummaryText(last.sender)
            )
        } else {
            val me = Person.Builder().setName(context.getString(R.string.notif_me)).build()
            val style = NotificationCompat.MessagingStyle(me)
            if (isGroup) style.setConversationTitle(title).setGroupConversation(true)
            for (line in lines) {
                // 보낸 사람이 null이면 "나"가 보낸 메시지로 표시됨
                val person = if (line.mine) null else Person.Builder().setName(line.sender).build()
                style.addMessage(line.text, line.at, person)
            }
            builder.setStyle(style)
        }

        // 버튼 1: 알림에서 바로 답장 (RemoteInput)
        val remoteInput = RemoteInput.Builder(KEY_REPLY).setLabel(context.getString(R.string.notif_reply_hint)).build()
        val replyIntent = actionBroadcast(context, NotificationActionReceiver.ACTION_CHAT_REPLY, "reply", roomId, mutableFlags())
        builder.addAction(
            NotificationCompat.Action.Builder(R.drawable.ic_send, context.getString(R.string.notif_reply), replyIntent)
                .addRemoteInput(remoteInput)
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                .setShowsUserInterface(false)
                .build()
        )
        // 버튼 2: 읽음으로 표시
        val readIntent = actionBroadcast(context, NotificationActionReceiver.ACTION_CHAT_READ, "read", roomId)
        builder.addAction(
            NotificationCompat.Action.Builder(R.drawable.ic_check, context.getString(R.string.notif_mark_read), readIntent)
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
                .setShowsUserInterface(false)
                .build()
        )
        post(context, TAG_CHAT, roomId.hashCode(), builder.build())
    }

    fun cancelChat(context: Context, roomId: String) {
        manager(context).cancel(TAG_CHAT, roomId.hashCode())
        pruneSummary(context)
    }

    // ───────────────────────── 업무 배정 (BigTextStyle) ─────────────────────────

    fun assigned(context: Context, projectId: String, taskId: String, title: String, projectName: String, due: String, description: String) {
        val summary = context.getString(R.string.notif_assign_summary, projectName, due)
        val big = if (description.isBlank()) summary else "$summary\n\n$description"
        val builder = base(context, CH_ASSIGN)
            .setContentTitle(context.getString(R.string.notif_assign_title))
            .setContentText(title)
            .setStyle(NotificationCompat.BigTextStyle().setBigContentTitle(title).bigText(big))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openTask(context, projectId, taskId))
            .setAutoCancel(true)
        post(context, TAG_ASSIGN, taskId.hashCode(), builder.build())
    }

    // ───────────────────────── 아침 마감 알림 (InboxStyle) ─────────────────────────

    fun digest(context: Context, digest: Digest) {
        if (digest.isEmpty) return
        val today = LocalDate.now()
        val parts = mutableListOf<String>()
        if (digest.today.isNotEmpty()) parts.add(context.getString(R.string.notif_digest_today, digest.today.size))
        if (digest.overdue.isNotEmpty()) parts.add(context.getString(R.string.notif_digest_overdue, digest.overdue.size))
        if (digest.tomorrow.isNotEmpty()) parts.add(context.getString(R.string.notif_digest_tomorrow, digest.tomorrow.size))
        val title = parts.joinToString(" · ")

        val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
        val all = digest.overdue + digest.today + digest.tomorrow
        // InboxStyle은 줄이 많으면 잘리므로 6줄까지만 넣고 나머지는 "외 N건"
        for (task in all.take(MAX_DIGEST_LINES)) style.addLine(digestLine(context, task, today))
        if (all.size > MAX_DIGEST_LINES) style.setSummaryText(context.getString(R.string.notif_digest_more, all.size - MAX_DIGEST_LINES))

        val builder = base(context, CH_DEADLINE)
            .setContentTitle(title)
            .setContentText(digestLine(context, all.first(), today))
            .setStyle(style)
            .setNumber(all.size)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openTasksTab(context))
            .setAutoCancel(true)
        post(context, null, ID_DIGEST, builder.build())
    }

    private const val MAX_DIGEST_LINES = 6

    private fun digestLine(context: Context, task: DigestTask, today: LocalDate): String {
        val time = task.dueTime?.let { " " + formatTime(it) }.orEmpty()
        val label = when {
            task.dueDate.isBefore(today) -> context.getString(R.string.status_overdue)
            task.dueDate == today -> context.getString(R.string.today) + time
            else -> context.getString(R.string.tomorrow) + time
        }
        return context.getString(R.string.notif_digest_line, label, task.title, task.projectName)
    }

    // ───────────────────────── 업무 알람 (버튼 3개 · 전체 화면) ─────────────────────────

    /**
     * 울리는 동안 떠 있는 알림. AlarmRingService가 startForeground에 넘긴다.
     * 버튼은 최대 3개: 완료로 표시(브로드캐스트) · 10분 뒤 다시(브로드캐스트) · 알람 끄기(서비스)
     */
    fun alarmRinging(context: Context, alarm: TaskAlarm): Notification {
        val full = PendingIntent.getActivity(
            context, 0, AlarmActivity.intent(context, alarm.taskId).setData(uri("alarm", alarm.taskId)), IMMUTABLE,
        )
        val done = actionBroadcast(context, NotificationActionReceiver.ACTION_TASK_DONE, "done", alarm.taskId, projectId = alarm.projectId)
        val snooze = actionBroadcast(context, NotificationActionReceiver.ACTION_SNOOZE, "snooze", alarm.taskId)
        val stop = PendingIntent.getService(
            context, 0, AlarmRingService.stopIntent(context).setData(uri("stop", alarm.taskId)), IMMUTABLE,
        )
        val builder = base(context, CH_ALARM)
            .setContentTitle(alarm.title)
            .setContentText(alarmSubtitle(context, alarm))
            .setSubText(alarmLabel(context, alarm))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)       // 밀어서 지울 수 없음
            .setAutoCancel(false)   // 눌러도 사라지지 않음 (버튼으로만 끔)
            .setContentIntent(full)
            .setFullScreenIntent(full, true) // 화면이 꺼져 있으면 알람 화면이 바로 뜸
            .addAction(R.drawable.ic_check, context.getString(R.string.alarm_action_done), done)
            .addAction(R.drawable.ic_postpone, context.getString(R.string.alarm_action_snooze, AlarmRules.SNOOZE_MIN), snooze)
            .addAction(R.drawable.ic_close, context.getString(R.string.alarm_action_stop), stop)
        return builder.build()
    }

    /** 30초 동안 아무것도 안 눌러서 알람이 멈췄을 때 남기는 알림 */
    fun alarmMissed(context: Context, alarm: TaskAlarm, willRepeat: Boolean) {
        val done = actionBroadcast(context, NotificationActionReceiver.ACTION_TASK_DONE, "done", alarm.taskId, projectId = alarm.projectId)
        val text = if (willRepeat) {
            context.getString(R.string.alarm_missed_repeat, AlarmRules.REPEAT_INTERVAL_MIN)
        } else {
            alarmSubtitle(context, alarm)
        }
        val builder = base(context, CH_DEADLINE)
            .setContentTitle(context.getString(R.string.alarm_missed_title, alarm.title))
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openTask(context, alarm.projectId, alarm.taskId))
            .setAutoCancel(true)
            .addAction(R.drawable.ic_check, context.getString(R.string.alarm_action_done), done)
        post(context, TAG_MISSED, alarm.taskId.hashCode(), builder.build())
    }

    fun cancelAlarmNotices(context: Context, taskId: String) {
        manager(context).cancel(TAG_MISSED, taskId.hashCode())
        manager(context).cancel(TAG_ASSIGN, taskId.hashCode())
        pruneSummary(context)
    }

    /** "마감 3시간 전" / "직접 설정한 알람" */
    fun alarmLabel(context: Context, alarm: TaskAlarm): String = when (alarm.option) {
        AlarmOption.MIN_30 -> context.getString(R.string.alarm_label_min, 30)
        AlarmOption.HOUR_1 -> context.getString(R.string.alarm_label_hour, 1)
        AlarmOption.HOUR_3 -> context.getString(R.string.alarm_label_hour, 3)
        AlarmOption.CUSTOM -> context.getString(R.string.alarm_label_custom)
    }

    /** "모프 7조 · 오늘 23:59 마감" */
    fun alarmSubtitle(context: Context, alarm: TaskAlarm): String =
        context.getString(R.string.alarm_due_line, alarm.projectName, formatDue(context, alarm.dueAt))

    /**
     * 오늘 23:59 / 내일 09:00 / 11.20 18:00
     * @param long true면 날짜를 길게: 11월 20일 (금) 18:00
     */
    fun formatDue(context: Context, millis: Long, long: Boolean = false): String {
        val dateTime = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime()
        val today = LocalDate.now()
        val pattern = context.getString(if (long) R.string.pattern_date else R.string.pattern_date_short)
        val day = when (dateTime.toLocalDate()) {
            today -> context.getString(R.string.today)
            today.plusDays(1) -> context.getString(R.string.tomorrow)
            else -> dateTime.toLocalDate().format(DateTimeFormatter.ofPattern(pattern, context.resources.configuration.locales[0]))
        }
        return "$day ${formatTime(dateTime.toLocalTime())}"
    }

    fun formatTime(time: LocalTime): String = "%02d:%02d".format(time.hour, time.minute)

    // ───────────────────────── 진행 상황: 파일 업로드 (setProgress) ─────────────────────────

    fun uploadProgress(context: Context, fileName: String, percent: Int) {
        val builder = base(context, CH_PROGRESS)
            .setContentTitle(context.getString(R.string.notif_upload_title))
            .setContentText(fileName)
            .setSubText(context.getString(R.string.percent, percent))
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        post(context, null, ID_UPLOAD, builder.build())
    }

    fun uploadFinished(context: Context, fileName: String, success: Boolean) {
        val builder = base(context, CH_PROGRESS)
            .setContentTitle(context.getString(if (success) R.string.notif_upload_done else R.string.notif_upload_failed))
            .setContentText(fileName)
            .setProgress(0, 0, false) // 진행 막대를 없앰
            .setOngoing(false)
            .setAutoCancel(true)
            .setTimeoutAfter(4000)
        post(context, null, ID_UPLOAD, builder.build())
    }

    fun cancel(context: Context, id: Int) {
        manager(context).cancel(id)
        pruneSummary(context)
    }

    fun cancelAll(context: Context) = manager(context).cancelAll()
}
