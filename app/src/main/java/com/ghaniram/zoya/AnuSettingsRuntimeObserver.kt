package com.ghaniram.zoya

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper

/** Bridges persistent settings changes to the process-wide runtime. */
class AnuSettingsRuntimeObserver(context: Context) : SharedPreferences.OnSharedPreferenceChangeListener {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("anu_settings_preferences", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private val syncRunnable = Runnable { ZoyaSessionManager.onSettingsUpdated() }
    private val wakeWordManager = WakeWordManager(appContext) { ZoyaSessionManager.connect() }

    init {
        prefs.registerOnSharedPreferenceChangeListener(this)
        syncWakeWord()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == null) return
        handler.removeCallbacks(syncRunnable)
        handler.postDelayed(syncRunnable, 350L)
        if (key == "bring_wake_word_back" || key == "voice_guardian_on") {
            handler.post { syncWakeWord() }
        }
    }

    private fun syncWakeWord() {
        val store = AnuSettingsStore.getInstance(appContext)
        // A real always-on wake listener is intentionally opt-in. It must never
        // compete with the active Gemini microphone session.
        if (store.bringWakeWordBack && store.voiceGuardianOn) wakeWordManager.start()
        else wakeWordManager.stop()
    }
}
