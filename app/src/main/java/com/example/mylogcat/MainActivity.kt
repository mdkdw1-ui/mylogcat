package com.example.mylogcat

import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.os.Environment
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    private fun startLogService() {
        val intent = Intent(this, LogService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopLogService() {
        stopService(Intent(this, LogService::class.java))
    }

    private fun handleVpnToggle(turnOn: Boolean, viewModel: LogViewModel) {
        if (turnOn) {
            val file = File(filesDir, "wg.conf")
            if (!file.exists()) {
                Toast.makeText(this, "먼저 client.conf 파일을 선택해주세요.", Toast.LENGTH_SHORT).show()
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
            if (text.isNullOrBlank()) {
                Toast.makeText(this, "파일이 비어있습니다.", Toast.LENGTH_SHORT).show()
                return
            }
            File(filesDir, "wg.conf").writeText(text)
            Toast.makeText(this, "설정 파일이 저장되었습니다.", Toast.LENGTH_SHORT).show()
            viewModel.setHasConf(true)
        } catch (e: Exception) {
            Toast.makeText(this, "파일 읽기 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    @Composable
    fun AppContent(viewModel: LogViewModel = viewModel()) {
        val isLogging by viewModel.isLogging.collectAsState()
        val isVpnOn by viewModel.isVpnOn.collectAsState()
        val serverStatus by viewModel.serverStatus.collectAsState()
        val debugLog by viewModel.debugLogs.collectAsState()
        val hasConf by viewModel.hasConf.collectAsState()

        var webViewInstance by remember { mutableStateOf<WebView?>(null) }
        var isUiReady by remember { mutableStateOf(false) }
        var webViewError by remember { mutableStateOf<String?>(null) }
        var selectedTab by remember { mutableStateOf(0) }

        val filePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            uri?.let { handleConfFile(it, viewModel) }
        }

        LaunchedEffect(Unit) { isUiReady = true }

        LaunchedEffect(isVpnOn) {
            if (isVpnOn) {
                while (isVpnOn) {
                    try {
                        val socket = Socket()
                        socket.connect(InetSocketAddress("10.0.0.97", 8081), 1500)
                        socket.close()
                        viewModel.setServerStatus("✅ 서버 연결됨 (정상 작동 중)")
                        webViewError = null
                    } catch (e: Exception) {
                        viewModel.setServerStatus("❌ 서버 연결 대기 중... (${e.javaClass.simpleName})")
                    }
                    delay(3000)
                }
            } else {
                viewModel.setServerStatus("⏸️ VPN이 꺼져 있습니다.")
            }
        }

        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            // 상태 표시 카드
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (serverStatus.contains("✅")) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                )
            ) {
                Text(text = serverStatus, modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Medium)
            }

            // VPN 및 제어 패널
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("WireGuard VPN", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Switch(checked = isVpnOn, onCheckedChange = { 
                            viewModel.setVpnState(it)
                            handleVpnToggle(it, viewModel) 
                        })
                    }
                    if (!hasConf) {
                        Text("⚠️ 설정 파일 없음", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    OutlinedButton(onClick = { filePickerLauncher.launch("*/*") }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (hasConf) "client.conf 변경" else "client.conf 선택")
                    }

                    Divider(modifier = Modifier.padding(vertical = 12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.setLoggingState(true); startLogService() },
                            enabled = !isLogging && isVpnOn,
                            modifier = Modifier.weight(1f)
                        ) { Text("시작") }
                        
                        Button(
                            onClick = { viewModel.setLoggingState(false); stopLogService() },
                            enabled = isLogging,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) { Text("중지") }
                        
                        Button(
                            onClick = {
                                webViewInstance?.evaluateJavascript("(function(){ var l = document.body.innerText; if(l) Android.saveLogToFile(l); })();", null)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) { Text("저장") }
                    }
                }
            }

            // 탭 및 웹뷰
            TabRow(selectedTabIndex = selectedTab, modifier = Modifier.clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("로그 모니터") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("디버그 콘솔") })
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color.Black)) {
                if (selectedTab == 0) {
                    if (webViewError != null) {
                        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(webViewError ?: "", color = Color.White)
                            Button(onClick = { webViewError = null; webViewInstance?.reload() }) { Text("다시 시도") }
                        }
                    } else {
                        if (isUiReady) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { ctx ->
                                    WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    webViewClient = object : WebViewClient() {
                                        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                            if (request?.isForMainFrame == true) {
                                                webViewError = "로드 실패: ${error?.description}"
                                            }
                                        }
                                    }
                                    addJavascriptInterface(WebAppInterface(ctx), "Android")
                                    loadUrl("http://10.0.0.97:8081")
                                        webViewInstance = this
                                    }
                                }
                            )
                        }
                    }
                } else {
                    Text(
                        text = debugLog,
                        color = Color.Green,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.fillMaxSize().padding(8.dp).verticalScroll(rememberScrollState())
                    )
                }
            }
        }
    }
}

class WebAppInterface(private val context: android.content.Context) {
    @JavascriptInterface
    fun saveLogToFile(logText: String) {
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "Logcat_$timeStamp.txt"
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            FileOutputStream(File(downloadDir, fileName)).use { it.write(logText.toByteArray()) }
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, "저장됨: Download/$fileName", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, "저장 실패", Toast.LENGTH_LONG).show()
            }
        }
    }
}
