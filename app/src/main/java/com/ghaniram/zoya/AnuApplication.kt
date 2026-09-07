package com.ghaniram.zoya

import android.app.Application

/** Application bootstrap for Anu runtime services. */
class AnuApplication : Application() {
    private lateinit var settingsObserver: AnuSettingsRuntimeObserver

    override fun onCreate() {
        super.onCreate()
        // Initialize the process-wide session before background receivers can use it.
        ZoyaSessionManager.initialize(this)
        settingsObserver = AnuSettingsRuntimeObserver(this)
        // Gemini Live rotates its WebSocket connection roughly every 10 minutes.
        // Keep an explicitly active Anu voice session automatically recovered.
        LiveConnectionWatchdog.start(this)
    }
}
