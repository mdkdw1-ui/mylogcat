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
    private var webViewInstance: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(
                        onStartLog = { startLogService() },
                        onStopLog = { stopService(Intent(this, LogService::class.java)) },
                        onSave = { saveLogsFromWebView() },
                        onWebViewCreated = { webViewInstance = it },
                        onReloadWebView = { webViewInstance?.reload() },
                        onConfSelected = { uri -> handleConfFile(uri) },
                        onVpnToggle = { isChecked -> toggleVpn(isChecked) },
                        getSavedConf = { readSavedConf() }
                    )
                }
            }
        }
    }

    private fun startLogService() {
        val intent = Intent(this, LogService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun readSavedConf(): String {
        val file = File(filesDir, "wg.conf")
        return if (file.exists()) file.readText() else ""
    }

    private fun handleConfFile(uri: Uri) {
        try {
            val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (text.isNullOrBlank()) {
                Toast.makeText(this, "파일이 비어있습니다.", Toast.LENGTH_SHORT).show()
                return
            }
            File(filesDir, "wg.conf").writeText(text)
            Toast.makeText(this, "설정 파일이 저장되었습니다.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "파일 읽기 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleVpn(turnOn: Boolean) {
        if (turnOn) {
            val file = File(filesDir, "wg.conf")
            if (!file.exists()) {
                Toast.makeText(this, "먼저 client.conf 파일을 선택해주세요.", Toast.LENGTH_SHORT).show()
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            startVpnService(File(filesDir, "wg.conf"))
        }
    }

    private fun startVpnService(confFile: File) {
        val intent = Intent(this, WireGuardService::class.java).apply {
            action = "start"
            putExtra("confPath", confFile.absolutePath)
        }
        startService(intent)
    }

    private fun saveLogsFromWebView() {
        webViewInstance?.evaluateJavascript("""
            (function() {
                var logs = document.body.innerText; 
                if(logs) { Android.saveLogToFile(logs); } 
                else { Android.saveLogToFile("저장할 로그가 없습니다."); }
            })();
        """.trimIndent(), null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onStartLog: () -> Unit,
    onStopLog: () -> Unit,
    onSave: () -> Unit,
    onWebViewCreated: (WebView) -> Unit,
    onReloadWebView: () -> Unit,
    onConfSelected: (Uri) -> Unit,
    onVpnToggle: (Boolean) -> Unit,
    getSavedConf: () -> String,
    viewModel: LogViewModel = viewModel()
) {
    val isLogging by viewModel.isLogging.collectAsState()
    val isVpnOn by viewModel.isVpnOn.collectAsState()
    val serverStatus by viewModel.serverStatus.collectAsState()
    val debugLog by viewModel.debugLogs.collectAsState()

    var hasConf by remember { mutableStateOf(getSavedConf().isNotEmpty()) }
    var webViewError by remember { mutableStateOf<String?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onConfSelected(it); hasConf = true }
    }

    LaunchedEffect(isVpnOn) {
        while (isVpnOn) {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress("10.0.0.97", 8081), 1500)
                socket.close()
                viewModel.setServerStatus("✅ 서버 연결됨 (정상 작동 중)")
                webViewError = null
            } catch (e: Exception) {
                viewModel.setServerStatus("❌ 서버 연결 대기 중... (${e.javaClass.simpleName})")
                viewModel.appendLog("연결 실패: ${e.message}")
            }
            delay(3000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MyLogcat Pro", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 12.dp)) {
            
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (serverStatus.contains("✅")) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                )
            ) {
                Text(
                    text = serverStatus,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("WireGuard VPN", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Switch(
                            checked = isVpnOn,
                            onCheckedChange = { 
                                viewModel.setVpnState(it)
                                onVpnToggle(it) 
                            }
                        )
                    }
                    if (!hasConf) {
                        Text("⚠️ 설정 파일이 없습니다. 아래 버튼으로 로드하세요.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    OutlinedButton(
                        onClick = { filePickerLauncher.launch("*/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (hasConf) "client.conf 파일 변경" else "client.conf 파일 선택")
                    }

                    Divider(modifier = Modifier.padding(vertical = 12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.setLoggingState(true); onStartLog() },
                            enabled = !isLogging && isVpnOn,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("수집 시작")
                        }
                        Button(
                            onClick = { viewModel.setLoggingState(false); onStopLog() },
                            enabled = isLogging,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("중지")
                        }
                        Button(
                            onClick = onSave,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("저장")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            var selectedTab by remember { mutableStateOf(0) }
            TabRow(selectedTabIndex = selectedTab, modifier = Modifier.clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("로그 모니터") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("디버그 콘솔") })
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color.Black)) {
                if (selectedTab == 0) {
                    if (webViewError != null) {
                        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(webViewError ?: "", color = Color.White)
                            Button(onClick = { 
                                webViewError = null
                                onReloadWebView()
                            }) { Text("다시 시도") }
                        }
                    } else {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    webViewClient = object : WebViewClient() {
                                        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                            super.onReceivedError(view, request, error)
                                            if (request?.isForMainFrame == true) {
                                                webViewError = "웹페이지 로드 실패\n코드: ${error?.errorCode}\n내용: ${error?.description}"
                                                viewModel.appendLog("WebView 에러: ${error?.description}")
                                            }
                                        }
                                    }
                                    addJavascriptInterface(WebAppInterface(ctx), "Android")
                                    loadUrl("http://10.0.0.97:8081")
                                    onWebViewCreated(this)
                                }
                            }
                        )
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
            val file = File(downloadDir, fileName)
            FileOutputStream(file).use { it.write(logText.toByteArray()) }
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, "저장됨: Download/$fileName", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, "저장 실패: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
