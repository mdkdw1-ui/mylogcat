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

    fun setVpnState(isOn: Boolean) { _isVpnOn.value = isOn }
    fun setHasConf(has: Boolean) { _hasConf.value = has }

    fun addLog(line: String) {
        val current = _logs.value.toMutableList()
        current.add(line)
        if (current.size > 3000) { current.removeAt(0) }
        _logs.value = current
    }

    fun clearLogs() { _logs.value = emptyList() }

    // ★ 앱 실행 시 과거의 'Crash(크래시)' 버퍼 로그를 가져와 화면에 띄움
    suspend fun loadPastCrashLogs() = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-b", "crash", "-v", "time", "-t", "100"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            val pastLogs = mutableListOf<String>()
            while (reader.readLine().also { line = it } != null) {
                line?.let { pastLogs.add(it) }
            }
            if (pastLogs.isNotEmpty()) {
                _logs.value = pastLogs
            }
        } catch (e: Exception) {}
    }
}
