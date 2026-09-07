package com.ghaniram.zoya

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Bridges persistent settings changes to the process-wide runtime without restarting the UI/live session. */
class AnuSettingsRuntimeObserver(context: Context) : SharedPreferences.OnSharedPreferenceChangeListener {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("anu_settings_preferences", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private val syncRunnable = Runnable {
        CapabilityRegistry.refresh(appContext)
    }
    private val wakeWordManager = WakeWordManager(appContext) { ZoyaSessionManager.connect() }

    init {
        prefs.registerOnSharedPreferenceChangeListener(this)
        CapabilityRegistry.refresh(appContext)
        CoroutineScope(Dispatchers.Main.immediate).launch {
            ZoyaSessionManager.state.collect {
                syncWakeWord()
                CapabilityRegistry.refresh(appContext)
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == null) return
        handler.removeCallbacks(syncRunnable)
        handler.postDelayed(syncRunnable, 350L)
        if (key == "bring_wake_word_back" || key == "voice_guardian_on") {
            handler.post { syncWakeWord() }
        }
        // Only settings that fundamentally change the active Live session should reconnect.
        // Theme, event-trigger, behaviour and UI settings must apply without tearing down
        // the current Activity/session, otherwise Chat/Settings appear to refresh.
        if (key == "custom_gemini_key" || key == "voice_speaker") {
            handler.postDelayed({
                if (ZoyaSessionManager.state.value.connectionState != ConnectionState.DISCONNECTED) {
                    ZoyaSessionManager.onSettingsUpdated()
                }
            }, 500L)
        }
    }

    private fun syncWakeWord() {
        val store = AnuSettingsStore.getInstance(appContext)
        val disconnected = ZoyaSessionManager.state.value.connectionState == ConnectionState.DISCONNECTED
        // Wake Word is its own feature. Voice Guardian may protect the mic, but it
        // must not be a prerequisite for wake-word detection.
        if (store.bringWakeWordBack && disconnected) {
            ensureWakeForegroundService()
            wakeWordManager.start()
        } else {
            wakeWordManager.stop()
        }
    }

    private fun ensureWakeForegroundService() {
        runCatching {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
                ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                ContextCompat.startForegroundService(
                    appContext,
                    android.content.Intent(appContext, ZoyaForegroundService::class.java).setAction(ZoyaForegroundService.ACTION_WAKE_WORD)
                )
            }
        }
    }
}
