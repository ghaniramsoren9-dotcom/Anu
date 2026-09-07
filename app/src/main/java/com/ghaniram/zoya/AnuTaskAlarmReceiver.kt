package com.ghaniram.zoya

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Receives scheduled task alarms and hands them to Anu's proactive voice runtime. */
class AnuTaskAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != "com.ghaniram.zoya.ACTION_TASK_ALARM") return
        fire(
            context,
            intent.getStringExtra("task_id").orEmpty(),
            intent.getStringExtra("task_title").orEmpty(),
            intent.getStringExtra("task_time").orEmpty()
        )
    }

    companion object {
        fun fire(context: Context, taskId: String, title: String, timeLabel: String) {
            if (title.isBlank()) return
            val event = if (timeLabel.isBlank()) {
                "It is time for the user's reminder: $title."
            } else {
                "It is time for the user's reminder: $title, scheduled for $timeLabel."
            }
            ProactiveEventEngine.dispatch(context.applicationContext, event, "task:${taskId.ifBlank { title }}")
        }
    }
}
