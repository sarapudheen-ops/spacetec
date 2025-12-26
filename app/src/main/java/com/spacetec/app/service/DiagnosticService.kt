package com.spacetec.app.service

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.spacetec.app.SpaceTecApplication
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject

@AndroidEntryPoint
class DiagnosticService : Service() {
    
    // ScannerManager is provided by scanner modules which are disabled in this build.
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var monitoringJob: Job? = null
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, createNotification("Monitoring..."))
        startMonitoring()
        return START_STICKY
    }
    
    private fun startMonitoring() {
        // Monitoring requires scanner modules. Disabled for now.
        monitoringJob = scope.launch {
            // no-op
            while (isActive) {
                delay(30_000)
            }
        }
    }
    
    private fun checkForNewDTCs(data: ByteArray) {
        val str = String(data)
        if (str.contains("43") && str.length > 4) {
            // New DTCs detected - notify
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(2, NotificationCompat.Builder(this, SpaceTecApplication.CHANNEL_DTC)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("New DTC Detected")
                .setContentText("Check your vehicle diagnostics")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build())
        }
    }
    
    private fun createNotification(text: String) = NotificationCompat.Builder(this, SpaceTecApplication.CHANNEL_CONNECTION)
        .setSmallIcon(android.R.drawable.ic_menu_info_details)
        .setContentTitle("SpaceTec Diagnostics")
        .setContentText(text)
        .build()
    
    override fun onDestroy() {
        monitoringJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}
