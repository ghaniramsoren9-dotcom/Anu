package com.ghaniram.zoya

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper

/**
 * Bridges persistent settings changes to the process-wide runtime.
 * Settings screens may update SharedPreferences directly, so this listener is
 * the single safety net that keeps the live Gemini session in sync.
 */
class AnuSettingsRuntimeObserver(context: Context) : SharedPreferences.OnSharedPreferenceChangeListener {
    private val prefs = context.applicationContext.getSharedPreferences(
        "anu_settings_preferences",
        Context.MODE_PRIVATE
    )
    private val handler = Handler(Looper.getMainLooper())
    private val syncRunnable = Runnable { ZoyaSessionManager.onSettingsUpdated() }

    init {
        prefs.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == null) return
        handler.removeCallbacks(syncRunnable)
        handler.postDelayed(syncRunnable, 350L)
    }
}
