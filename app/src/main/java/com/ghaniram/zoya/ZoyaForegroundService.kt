package com.ghaniram.zoya

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/** Keeps the process alive and restores the process-wide Anu session after task/process recreation. */
class ZoyaForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val openIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        val pendingIntent = PendingIntent.getActivity(this, 4002, openIntent, pendingFlags)

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Anu is Active")
            .setContentText("Voice conversation continues in the background")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val hasMic = androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.RECORD_AUDIO
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (hasMic) {
                    androidx.core.app.ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID,
                        notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    )
                } else {
                    androidx.core.app.ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID,
                        notification,
                        0
                    )
                }
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.onFailure {
            runCatching { startForeground(NOTIFICATION_ID, notification) }
        }
        ZoyaSessionManager.initialize(application)
        if (intent?.action == ACTION_START || intent?.action == ACTION_WAKE_WORD || intent == null) {
            ZoyaSessionManager.restoreIfNeeded()
        }
        return START_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Anu voice assistant", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Keeps an active Anu voice conversation running in the background."
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.ghaniram.zoya.START_VOICE"
        const val ACTION_STOP = "com.ghaniram.zoya.STOP_VOICE"
        const val ACTION_WAKE_WORD = "com.ghaniram.zoya.START_WAKE_WORD"
        private const val CHANNEL_ID = "zoya_voice"
        private const val NOTIFICATION_ID = 4001
    }
}
