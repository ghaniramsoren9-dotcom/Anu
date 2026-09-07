package com.ghaniram.zoya

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Schedules user-created Anu tasks as real Android alarms. */
object AnuTaskAlarmScheduler {
    private const val ACTION = "com.ghaniram.zoya.ACTION_TASK_ALARM"
    private const val EXTRA_ID = "task_id"
    private const val EXTRA_TITLE = "task_title"
    private const val EXTRA_TIME = "task_time"

    fun schedule(context: Context, task: AnuTask) {
        val triggerAt = nextOccurrence(task.timeLabel) ?: return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, AnuTaskAlarmReceiver::class.java).apply {
            action = ACTION
            putExtra(EXTRA_ID, task.id)
            putExtra(EXTRA_TITLE, task.title)
            putExtra(EXTRA_TIME, task.timeLabel)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            task.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        }
    }

    fun cancel(context: Context, taskId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, AnuTaskAlarmReceiver::class.java).apply { action = ACTION }
        val pi = PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pi)
        pi.cancel()
    }

    private fun nextOccurrence(label: String): Long? {
        val formats = listOf("h:mm a", "hh:mm a", "H:mm", "HH:mm")
        val now = Calendar.getInstance()
        for (pattern in formats) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.getDefault()).apply { isLenient = false }.parse(label.trim())
            }.getOrNull() ?: continue
            val cal = Calendar.getInstance().apply {
                time = parsed
                set(Calendar.YEAR, now.get(Calendar.YEAR))
                set(Calendar.MONTH, now.get(Calendar.MONTH))
                set(Calendar.DAY_OF_MONTH, now.get(Calendar.DAY_OF_MONTH))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (cal.timeInMillis <= now.timeInMillis) cal.add(Calendar.DAY_OF_YEAR, 1)
            return cal.timeInMillis
        }
        return null
    }
}
