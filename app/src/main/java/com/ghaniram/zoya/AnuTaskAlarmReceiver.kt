package com.ghaniram.zoya

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Receives scheduled task alarms and hands them to Anu's proactive voice runtime. */
class AnuTaskAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != "com.ghaniram.zoya.ACTION_TASK_ALARM") return
        val title = intent.getStringExtra("task_title")?.trim().orEmpty()
        if (title.isBlank()) return
        val event = "It is time for the user's reminder: $title."
        ProactiveEventEngine.dispatch(context.applicationContext, event, "task:${intent.getStringExtra("task_id") ?: title}")
    }
}
