package com.ghaniram.zoya

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.atomic.AtomicLong

/** Runtime enforcement for the user-armed Touch Guard feature. */
object TouchGuardRuntime {
    private val lastAction = AtomicLong(0L)

    fun onAccessibilityEvent(service: AccessibilityControlService, event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_TOUCH_INTERACTION_START) return
        val store = AnuSettingsStore.getInstance(service)
        if (!store.touchGuardEnabled || !store.touchGuardArmed) return
        if (service.currentPackageName() == service.packageName) return

        val now = SystemClock.elapsedRealtime()
        val previous = lastAction.get()
        if (now - previous < 1500L || !lastAction.compareAndSet(previous, now)) return

        if (store.warnFirstSirenSecond) {
            runCatching {
                ToneGenerator(AudioManager.STREAM_ALARM, 80).apply {
                    startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 700)
                    release()
                }
            }
        }

        if (store.lockScreenImmediatelyOnTouch) {
            service.globalAction("lock")
        }
    }
}
