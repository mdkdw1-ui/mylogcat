import os

BASE = "app/src/main"

LOG_VIEWMODEL = r'''package com.example.mylogcat

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class LogViewModel : ViewModel() {

    private val _isVpnOn = MutableStateFlow(false)
    val isVpnOn: StateFlow<Boolean> = _isVpnOn

    private val _hasConf = MutableStateFlow(false)
    val hasConf: StateFlow<Boolean> = _hasConf

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    private var logProcess: Process? = null

    fun setVpnState(isOn: Boolean) {
        _isVpnOn.value = isOn
    }

    fun setHasConf(has: Boolean) {
        _hasConf.value = has
    }

    fun addLog(line: String) {
        val current = _logs.value.toMutableList()
        current.add(line)
        if (current.size > 3000) current.removeAt(0)
        _logs.value = current
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun stopLogcat() {
        try {
            logProcess?.destroy()
        } catch (_: Exception) {
        }
        logProcess = null
    }

    suspend fun startLiveLogcat() = withContext(Dispatchers.IO) {
        stopLogcat()
        _logs.value = emptyList()

        try {
            val process = Runtime.getRuntime()
                .exec(arrayOf("logcat", "-v", "time", "-T", "1"))
            logProcess = process

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                line?.let { addLog(it) }
            }
        } catch (e: Exception) {
            addLog("logcat 시작 실패: ${e.message}")
            addLog("adb shell pm grant com.example.mylogcat android.permission.READ_LOGS 를 실행하세요.")
        }
    }

    suspend fun loadPastCrashLogs() = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime()
                .exec(arrayOf("logcat", "-d", "-b", "crash", "-v", "time", "-t", "100"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val pastLogs = mutableListOf<String>()
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                line?.let { pastLogs.add(it) }
            }

            if (pastLogs.isNotEmpty()) {
                _logs.value = pastLogs
            }

            process.destroy()
        } catch (e: Exception) {
            addLog("크래시 로그 읽기 실패: ${e.message}")
        }
    }

    override fun onCleared() {
        stopLogcat()
        super.onCleared()
    }
}
'''

WIREGUARD_SERVICE = r'''package com.example.mylogcat

import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.IBinder
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config

class WireGuardService : android.app.Service() {

    private var backend: Backend? = null
    private var tunnel: Tunnel? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "start" -> {
                val confUri = intent.getStringExtra("confUri")

                if (confUri == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }

                if (VpnService.prepare(this) != null) {
                    stopSelf()
                    return START_NOT_STICKY
                }

                startWg(confUri)
            }

            "stop" -> stopWg()
        }

        return START_NOT_STICKY
    }

    private fun startWg(confUriString: String) {
        try {
            val uri = Uri.parse(confUriString)

            val config = contentResolver.openInputStream(uri)?.use {
                Config.parse(it)
            } ?: throw IllegalStateException("client.conf를 읽을 수 없습니다")

            backend = GoBackend(this)

            tunnel = object : Tunnel {
                override fun getName() = "mylogcat-wg"

                override fun onStateChange(newState: Tunnel.State) {
                    running = newState == Tunnel.State.UP
                }
            }

            backend?.setState(tunnel, Tunnel.State.UP, config)
            running = true
        } catch (e: Exception) {
            running = false
            stopSelf()
        }
    }

    private fun stopWg() {
        try {
            if (tunnel != null) {
                backend?.setState(tunnel, Tunnel.State.DOWN, null)
            }
        } catch (_: Exception) {
        }

        running = false
        stopSelf()
    }

    override fun onDestroy() {
        stopWg()
        super.onDestroy()
    }

    companion object {
        @Volatile
        var running = false
    }
}
'''

MANIFEST = r'''<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.READ_LOGS" />

    <application
        android:allowBackup="true"
        android:label="MyLogcat"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher"
        android:supportsRtl="true"
        android:usesCleartextTraffic="true"
        android:hardwareAccelerated="true"
        android:theme="@style/Theme.Material3.DayNight.NoActionBar">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".WireGuardService"
            android:exported="false" />
    </application>
</manifest>
'''

def write(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)

    with open(path, "w", encoding="utf-8") as f:
        f.write(content)

    print("수정 완료:", path)

write(
    os.path.join(BASE, "java/com/example/mylogcat/LogViewModel.kt"),
    LOG_VIEWMODEL
)

write(
    os.path.join(BASE, "java/com/example/mylogcat/WireGuardService.kt"),
    WIREGUARD_SERVICE
)

write(
    os.path.join(BASE, "AndroidManifest.xml"),
    MANIFEST
)

print("\n완료. 이제 MainActivity.kt의 VPN/파일 선택 부분만 수동 확인하세요.")
print("GitHub push 후 APK를 다시 빌드하세요.")
