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
            val app = context.applicationContext
            val event = if (timeLabel.isBlank()) {
                "It is time for your reminder: $title."
            } else {
                "It is time for your reminder: $title, scheduled for $timeLabel."
            }

            // Keep the normal Anu event path. ProactiveVoiceBridge deliberately avoids
            // reconnecting a disabled microphone and falls back to TTS when needed.
            ProactiveEventEngine.dispatch(app, event, "task:${taskId.ifBlank { title }}")
        }
    }
}
