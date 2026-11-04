package com.example.mylogcat

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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
        if (current.size > 3000) { // 메모리 보호를 위해 최대 3000줄만 유지
            current.removeAt(0)
        }
        _logs.value = current
    }

    fun clearLogs() { _logs.value = emptyList() }
}
