package com.example.mylogcat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

class LogCollectionService : Service() {

    private var logProcess: Process? = null
    private var collectJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground()

        if (LogRepository.isCollecting.value) {
            return START_STICKY
        }

        LogRepository.setCollecting(true)
        startLogcat()

        return START_STICKY
    }

    private fun startLogcat() {
        collectJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val process = ProcessBuilder("logcat", "-v", "time")
                    .start()
                logProcess = process

                val reader = BufferedReader(
                    InputStreamReader(process.inputStream)
                )

                while (LogRepository.isCollecting.value) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue
                    LogRepository.addLog(line)
                }
            } catch (e: Exception) {
                LogRepository.addLog("로그 수집 오류: ${e.message}")
            } finally {
                stopSelf()
            }
        }
    }

    private fun startForeground() {
        val channelId = "log_collection_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "로그 수집",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, channelId)
                    .setContentTitle("MyLogcat")
                    .setContentText("로그 수집 중")
                    .setSmallIcon(android.R.drawable.ic_menu_info_details)
                    .build()
            } else {
                Notification.Builder(this)
                    .setContentTitle("MyLogcat")
                    .setContentText("로그 수집 중")
                    .setSmallIcon(android.R.drawable.ic_menu_info_details)
                    .build()
            }

        startForeground(1, notification)
    }

    override fun onDestroy() {
        LogRepository.setCollecting(false)

        try {
            logProcess?.destroy()
        } catch (_: Exception) {
        }

        logProcess = null
        collectJob?.cancel()
        collectJob = null

        super.onDestroy()
    }
}
