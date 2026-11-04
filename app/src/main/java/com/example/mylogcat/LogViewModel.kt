package com.example.mylogcat

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class LogViewModel : ViewModel() {
    private val _isVpnOn = MutableStateFlow(false)
    val isVpnOn: StateFlow<Boolean> = _isVpnOn

    private val _isLogging = MutableStateFlow(false)
    val isLogging: StateFlow<Boolean> = _isLogging

    private val _serverStatus = MutableStateFlow("서버 확인 중...")
    val serverStatus: StateFlow<String> = _serverStatus

    private val _debugLogs = MutableStateFlow("대기 중...\n")
    val debugLogs: StateFlow<String> = _debugLogs

    fun setVpnState(isOn: Boolean) { _isVpnOn.value = isOn }
    fun setLoggingState(isLogging: Boolean) { _isLogging.value = isLogging }
    fun setServerStatus(status: String) { _serverStatus.value = status }
    
    // 디버그 로그 무한 증가 방지 (최대 500줄 유지)
    fun appendLog(log: String) {
        val current = _debugLogs.value
        val newLog = current + log + "\n"
        val lines = newLog.split("\n")
        if (lines.size > 500) {
            _debugLogs.value = lines.takeLast(500).joinToString("\n")
        } else {
            _debugLogs.value = newLog
        }
    }
}
