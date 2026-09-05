package com.ghaniram.zoya

import android.app.Application

/** Application class for Anu Assistant. */
class AnuApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AnuSettingsRuntimeObserver.register(this)
    }
}