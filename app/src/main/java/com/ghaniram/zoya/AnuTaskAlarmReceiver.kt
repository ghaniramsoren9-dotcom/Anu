package com.ghaniram.zoya

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat

/** Receives scheduled task alarms, shows high-priority heads-up reminder notifications, and triggers proactive voice alert. */
class AnuTaskAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != "com.ghaniram.zoya.ACTION_TASK_ALARM") return
        val title = intent.getStringExtra("task_title")?.trim().orEmpty()
        if (title.isBlank()) return
        val timeLabel = intent.getStringExtra("task_time")?.trim().orEmpty()
        val taskId = intent.getStringExtra("task_id")?.trim().orEmpty()
        fire(context, taskId, title, timeLabel)
    }

    companion object {
        private const val CHANNEL_ID = "anu_task_reminders"

        fun fire(context: Context, taskId: String, title: String, timeLabel: String) {
            if (title.isBlank()) return
            val app = context.applicationContext

            // Only respond to active (not completed/unselected) reminders
            val currentTask = ZoyaSessionManager.state.value.tasks.firstOrNull { it.id == taskId }
            if (currentTask != null && currentTask.isCompleted) {
                return
            }

            // 1. Show high-priority heads-up reminder notification so the user never misses it even with phone locked
            showNotification(app, taskId, title, timeLabel)

            // 2. Dispatch to proactive voice engine with dedicated reminder prompt
            ProactiveEventEngine.dispatchReminder(app, title, timeLabel, taskId)
        }

        private fun showNotification(context: Context, taskId: String, title: String, timeLabel: String) {
            runCatching {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        "Anu Task Reminders",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Notifications for scheduled Anu task reminders and alarms"
                        enableLights(true)
                        enableVibration(true)
                    }
                    notificationManager.createNotificationChannel(channel)
                }

                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    taskId.hashCode(),
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val contentText = if (timeLabel.isNotBlank()) "$title ($timeLabel)" else title
                val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle("⏰ Anu Reminder")
                    .setContentText(contentText)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setAutoCancel(true)
                    .setSound(defaultSoundUri)
                    .setVibrate(longArrayOf(0, 500, 200, 500))
                    .setContentIntent(pendingIntent)
                    .build()

                notificationManager.notify(taskId.hashCode(), notification)
            }
        }
    }
}
