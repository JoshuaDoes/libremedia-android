package com.joshuadoes.music

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder

class LibremediaPlayer : Service() {
    var appBuild: String = "libremedia"
    var inputName: String = "Unknown"

    override fun onCreate() {
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        appBuild = intent?.getStringExtra("appBuild").toString()
        inputName = intent?.getStringExtra("inputName").toString()
        startForeground(1, createNotification())
        return START_STICKY
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder {
        return LocalBinder()
    }

    inner class LocalBinder : Binder() {
        fun getService(): LibremediaPlayer = this@LibremediaPlayer
    }

    fun createNotification(): Notification {
        val channelId = "libremedia"
        val channelName = "Media Playback"
        val notifMgmt = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH)
        notifMgmt.createNotificationChannel(channel)

        return Notification.Builder(this, channelId)
            .setSmallIcon(R.drawable.libremedia)
            .setContentTitle(appBuild)
            .setContentText(inputName)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .build()
    }
}
