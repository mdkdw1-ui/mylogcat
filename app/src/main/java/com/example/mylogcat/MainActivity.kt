package com.example.mylogcat

import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppContent()
                }
            }
        }
    }

    private fun handleVpnToggle(turnOn: Boolean, viewModel: LogViewModel) {
        if (turnOn) {
            val file = File(filesDir, "wg.conf")
            if (!file.exists()) {
                viewModel.setVpnState(false)
                return
            }
            val intent = VpnService.prepare(this)
            if (intent != null) {
                startActivityForResult(intent, 100)
            } else {
                startVpnService(file)
            }
        } else {
            stopService(Intent(this, WireGuardService::class.java).setAction("stop"))
        }
    }

    private fun startVpnService(confFile: File) {
        val intent = Intent(this, WireGuardService::class.java).apply {
            action = "start"
            putExtra("confPath", confFile.absolutePath)
        }
        startService(intent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            startVpnService(File(filesDir, "wg.conf"))
        }
    }

    private fun handleConfFile(uri: Uri, viewModel: LogViewModel) {
        try {
            val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (text.isNullOrBlank()) return
            File(filesDir, "wg.conf").writeText(text)
            viewModel.setHasConf(true)
        } catch (e: Exception) {}
    }

    @Composable
    fun AppContent(viewModel: LogViewModel = viewModel()) {
        val isVpnOn by viewModel.isVpnOn.collectAsState()
        val hasConf by viewModel.hasConf.collectAsState()
        val logs by viewModel.logs.collectAsState()
        
        var isCollecting by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val listState = rememberLazyListState()

        val filePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            uri?.let { handleConfFile(it, viewModel) }
        }

        // 로그 수집 시작/중지 로직
        LaunchedEffect(isCollecting) {
            if (isCollecting) {
                viewModel.clearLogs()
                withContext(Dispatchers.IO) {
                    try {
                        val process = Runtime.getRuntime().exec("logcat -v time -T 1")
                        val reader = BufferedReader(InputStreamReader(process.inputStream))
                        var line: String? = null
                        while (isCollecting && reader.readLine().also { line = it } != null) {
                            line?.let { viewModel.addLog(it) }
                        }
                    } catch (e: Exception) {}
                }
            }
        }

        // 스크롤 자동 맨 아래로
        LaunchedEffect(logs.size) {
            if (logs.isNotEmpty()) {
                listState.animateScrollToItem(logs.size - 1)
            }
        }

        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            // 1. 상단 제어 카드
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("WireGuard VPN", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Switch(checked = isVpnOn, onCheckedChange = { 
                            viewModel.setVpnState(it)
                            handleVpnToggle(it, viewModel) 
                        })
                    }
                    if (!hasConf) {
                        Text("⚠️ client.conf 파일을 로드하세요.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { filePickerLauncher.launch("*/*") }, modifier = Modifier.weight(1f)) {
                            Text(if (hasConf) "conf 변경" else "conf 로드", fontSize = 12.sp)
                        }
                        Button(
                            onClick = { 
                                if (isCollecting) {
                                    isCollecting = false
                                } else {
                                    isCollecting = true
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isCollecting) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(if (isCollecting) "수집 중지" else "로그 수집", fontSize = 12.sp)
                        }
                        Button(
                            onClick = {
                                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                                val fileName = "Logcat_$timeStamp.txt"
                                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                val file = File(downloadDir, fileName)
                                try {
                                    FileOutputStream(file).use { it.write(logs.joinToString("\n").toByteArray()) }
                                    android.widget.Toast.makeText(context, "저장됨: Download/$fileName", android.widget.Toast.LENGTH_LONG).show()
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "저장 실패", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("파일 저장", fontSize = 12.sp)
                        }
                    }
                }
            }

            // 2. 로그 출력 창 (가장 빠른 네이티브 리스트)
            Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color.Black).clip(RoundedCornerShape(8.dp))) {
                if (logs.isEmpty()) {
                    Text(
                        text = "로그 수집 버튼을 누르면\n실시간 로그가 여기에 표시됩니다.",
                        color = Color.Gray,
                        modifier = Modifier.align(Alignment.Center),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(8.dp)
                    ) {
                        items(logs) { logLine ->
                            Text(
                                text = logLine,
                                color = if (logLine.contains(" E ") || logLine.contains("FATAL")) Color.Red else Color.Green,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
