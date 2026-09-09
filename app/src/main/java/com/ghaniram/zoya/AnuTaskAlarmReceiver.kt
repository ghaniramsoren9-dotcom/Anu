package com.ghaniram.zoya

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Receives scheduled task alarms and hands them to Anu's proactive voice runtime. */
class AnuTaskAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != "com.ghaniram.zoya.ACTION_TASK_ALARM") return
        fire(context, intent.getStringExtra("task_id").orEmpty(), intent.getStringExtra("task_title").orEmpty(), intent.getStringExtra("task_time").orEmpty())
    }
    companion object {
        fun fire(context: Context, taskId: String, title: String, timeLabel: String) {
            if (title.isBlank()) return
            val app = context.applicationContext
            val event = if (timeLabel.isBlank()) "REMINDER ALERT: It is time for your reminder: $title. Speak this reminder to the user right now in a clear, natural voice. Do not stay silent." else "REMINDER ALERT: It is time for your reminder: $title, scheduled for $timeLabel. Speak this reminder to the user right now in a clear, natural voice. Do not stay silent."
            // One dispatch path only. The bridge queues into Gemini Live while connecting.
            ProactiveVoiceBridge.dispatch(app, event)
        }
    }
}
