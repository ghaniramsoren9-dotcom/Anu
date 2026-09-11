package com.ghaniram.zoya

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Schedules user-created Anu tasks as real Android alarms plus an in-process fast path. */
object AnuTaskAlarmScheduler {
    private const val ACTION = "com.ghaniram.zoya.ACTION_TASK_ALARM"
    private const val EXTRA_ID = "task_id"
    private const val EXTRA_TITLE = "task_title"
    private const val EXTRA_TIME = "task_time"
    private val handler = Handler(Looper.getMainLooper())
    private val pendingLocal = ConcurrentHashMap<String, Runnable>()

    fun schedule(context: Context, task: AnuTask) = schedule(context, task.id, task.title, task.timeLabel)

    /** Schedule directly from the UI values; does not depend on asynchronous StateFlow updates. */
    fun schedule(context: Context, taskId: String, title: String, timeLabel: String) {
        val triggerAt = nextOccurrence(timeLabel) ?: return
        val app = context.applicationContext
        cancelLocal(taskId)

        // In-process fast path: when Anu's process is alive, a reminder fires at the requested
        // wall-clock time even if exact-alarm special access has not been granted yet.
        val delay = triggerAt - System.currentTimeMillis()
        if (delay > 0 && delay <= 24 * 60 * 60 * 1000L) {
            val runnable = Runnable {
                pendingLocal.remove(taskId)
                AnuTaskAlarmReceiver.fire(app, taskId, title, timeLabel)
            }
            pendingLocal[taskId] = runnable
            handler.postDelayed(runnable, delay)
        }

        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(app, AnuTaskAlarmReceiver::class.java).apply {
            action = ACTION
            putExtra(EXTRA_ID, taskId)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_TIME, timeLabel)
        }
        val pi = PendingIntent.getBroadcast(
            app,
            stableRequestCode(taskId),
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
        cancelLocal(taskId)
        val alarmManager = context.applicationContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context.applicationContext, AnuTaskAlarmReceiver::class.java).apply { action = ACTION }
        val pi = PendingIntent.getBroadcast(
            context.applicationContext,
            stableRequestCode(taskId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pi)
        pi.cancel()
    }

    private fun cancelLocal(taskId: String) {
        pendingLocal.remove(taskId)?.let(handler::removeCallbacks)
    }

    private fun stableRequestCode(id: String): Int = id.hashCode()

    private fun nextOccurrence(label: String): Long? {
        val normalized = normalizeTimeLabel(label)
        val formats = listOf(
            "h:mm a", "hh:mm a", "H:mm", "HH:mm", "h a", "hh a"
        )
        val now = Calendar.getInstance()
        for (pattern in formats) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(normalized)
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

    private fun normalizeTimeLabel(raw: String): String {
        var s = raw.trim()
            .replace("\u00A0", " ")
            .replace(Regex("\\s+"), " ")

        // Convert Indic / Odia / Devanagari numerals to ASCII digits
        s = s.map { ch ->
            when (ch) {
                in '୦'..'୯' -> '0' + (ch - '୦')
                in '०'..'९' -> '0' + (ch - '०')
                else -> ch
            }
        }.joinToString("")

        // Ensure space before am/pm if glued to numbers (e.g., "7:00pm" -> "7:00 PM", "7pm" -> "7 PM")
        s = s.replace(Regex("(?i)(\\d+)(am|pm)"), "$1 $2")
        s = s.replace(Regex("(?i)(\\d+:\\d{2})(am|pm)"), "$1 $2")
        return s.uppercase(Locale.US)
    }
}
