package com.example.mylogcat

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object LogRepository {

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    private val _isCollecting = MutableStateFlow(false)
    val isCollecting: StateFlow<Boolean> = _isCollecting

    fun addLog(line: String) {
        val current = _logs.value.toMutableList()
        current.add(line)

        if (current.size > 5000) {
            val removeCount = current.size - 5000
            repeat(removeCount) {
                current.removeAt(0)
            }
        }

        _logs.value = current
    }

    fun setCollecting(value: Boolean) {
        _isCollecting.value = value
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
}
