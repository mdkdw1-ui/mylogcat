package com.example.mylogcat

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

    @Volatile
    private var isRunning = false

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
        isRunning = false

        try {
            logProcess?.destroy()
        } catch (_: Exception) {
        }

        logProcess = null
    }

    suspend fun startLiveLogcat() = withContext(Dispatchers.IO) {
        stopLogcat()
        _logs.value = emptyList()
        isRunning = true

        try {
            val process = Runtime.getRuntime()
                .exec(arrayOf("logcat", "-v", "time", "-T", "1"))

            logProcess = process

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?

            while (isRunning && reader.readLine().also { line = it } != null) {
                line?.let { addLog(it) }
            }
        } catch (e: Exception) {
            if (isRunning) {
                addLog("logcat 시작 실패: ${e.message}")
                addLog("adb shell pm grant com.example.mylogcat android.permission.READ_LOGS 를 실행하세요.")
            }
        } finally {
            isRunning = false

            try {
                logProcess?.destroy()
            } catch (_: Exception) {
            }

            logProcess = null
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
