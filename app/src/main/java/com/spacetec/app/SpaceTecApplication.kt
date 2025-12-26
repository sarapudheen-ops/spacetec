package com.spacetec.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SpaceTecApplication : Application() {
    
    companion object {
        const val CHANNEL_DTC = "dtc_notifications"
        const val CHANNEL_CONNECTION = "connection_notifications"
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }
    
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            
            manager.createNotificationChannel(NotificationChannel(
                CHANNEL_DTC, "DTC Alerts", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Diagnostic trouble code notifications" })
            
            manager.createNotificationChannel(NotificationChannel(
                CHANNEL_CONNECTION, "Connection Status", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Scanner connection status" })
        }
    }
}
