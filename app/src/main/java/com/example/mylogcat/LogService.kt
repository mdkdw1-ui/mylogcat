package com.example.mylogcat

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket

class LogService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            isRunning = true
            startForeground(1, buildNotification("로그 수집 중..."))
            startLogging()
        }
        return START_STICKY
    }

    private fun startLogging() {
        serviceScope.launch {
            try {
                val socket = Socket("10.0.0.97", 8080)
                val writer = OutputStreamWriter(socket.getOutputStream())
                
                // 💡 [핵심 수정] *:E (모든 에러) 대신 크래시(AndroidRuntime)와 앱 에러만 필터링
                // 필요에 따라 "com.example.mylogcat" 등을 추가할 수 있습니다.
                val process = Runtime.getRuntime().exec("logcat -v threadtime AndroidRuntime:E *:F") 
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                
                while (isRunning && reader.readLine().also { line = it } != null) {
                    line?.let {
                        writer.write("$it\n")
                        writer.flush()
                    }
                }
                socket.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("logcat_channel", "Logcat Service", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        return Notification.Builder(this, "logcat_channel")
            .setContentTitle("MyLogcat")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .build()
    }

    override fun onDestroy() {
        isRunning = false
        serviceScope.cancel()
        super.onDestroy()
    }
}
