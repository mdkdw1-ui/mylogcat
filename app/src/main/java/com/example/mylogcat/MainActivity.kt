package com.example.mylogcat

import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.os.Environment
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var webViewInstance: WebView? = null
    private var pendingConfFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(
                        onStartLog = { ContextCompat.startForegroundService(this, Intent(this, LogService::class.java)) },
                        onStopLog = { stopService(Intent(this, LogService::class.java)) },
                        onSave = { saveLogsFromWebView() },
                        onWebViewCreated = { webViewInstance = it },
                        onConfSelected = { uri -> handleConfFile(uri) },
                        onVpnToggle = { isChecked -> toggleVpn(isChecked) },
                        getSavedConf = { readSavedConf() }
                    )
                }
            }
        }
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
            val file = File(filesDir, "wg.conf")
            file.writeText(text)
            Toast.makeText(this, "설정 파일이 저장되었습니다. 이제 VPN 스위치를 켜세요.", Toast.LENGTH_SHORT).show()
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
            pendingConfFile = file
            // VPN 권한 요청 (이미 허용했다면 바로 시작됨)
            val intent = VpnService.prepare(this)
            if (intent != null) {
                startActivityForResult(intent, 100)
            } else {
                startVpnService(file)
            }
        } else {
            stopService(Intent(this, WireGuardService::class.java).setAction("stop"))
            Toast.makeText(this, "VPN 연결을 해제했습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            pendingConfFile?.let { startVpnService(it) }
        }
    }

    private fun startVpnService(confFile: File) {
        val intent = Intent(this, WireGuardService::class.java).apply {
            action = "start"
            putExtra("confPath", confFile.absolutePath)
        }
        startService(intent)
        Toast.makeText(this, "WireGuard VPN 연결을 시작합니다.", Toast.LENGTH_SHORT).show()
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
    onConfSelected: (Uri) -> Unit,
    onVpnToggle: (Boolean) -> Unit,
    getSavedConf: () -> String
) {
    var isRunning by remember { mutableStateOf(false) }
    var isVpnOn by remember { mutableStateOf(false) }
    var serverStatus by remember { mutableStateOf("서버 확인 중...") }
    val context = LocalContext.current
    
    // 앱 시작 시 저장된 conf 파일이 있는지 확인
    var hasConf by remember { mutableStateOf(getSavedConf().isNotEmpty()) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { 
            onConfSelected(it) 
            hasConf = true // 파일 선택 후 상태 업데이트
        }
    }

    // 서버 연결 상태 주기적 체크
    LaunchedEffect(Unit) {
        while (true) {
            withContext(Dispatchers.IO) {
                try {
                    val socket = Socket()
                    socket.connect(InetSocketAddress("10.0.0.97", 8081), 2000)
                    socket.close()
                    serverStatus = "서버 연결됨 (VPN 활성화 상태)"
                    isVpnOn = true
                } catch (e: Exception) {
                    serverStatus = "서버 연결 실패 (VPN을 켜주세요)"
                    isVpnOn = false
                }
            }
            delay(3000) // 3초마다 갱신
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MyLogcat Pro") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Text(
                text = serverStatus,
                modifier = Modifier.padding(8.dp),
                color = if (serverStatus.contains("연결됨")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )

            // VPN 제어 영역
            Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("VPN 연결", modifier = Modifier.weight(1f))
                        Switch(
                            checked = isVpnOn,
                            onCheckedChange = { 
                                isVpnOn = it
                                onVpnToggle(it) 
                            }
                        )
                    }
                    if (!hasConf) {
                        Text("설정 파일이 없습니다. 아래 버튼을 눌러 로드하세요.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { filePickerLauncher.launch("*/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (hasConf) "client.conf 다시 선택" else "client.conf 파일 선택")
                    }
                }
            }

            // 로그 제어 버튼
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = { isRunning = true; onStartLog() }, enabled = !isRunning, modifier = Modifier.weight(1f)) { Text("로그 시작") }
                Button(onClick = { isRunning = false; onStopLog() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), enabled = isRunning, modifier = Modifier.weight(1f)) { Text("로그 중지") }
                Button(onClick = onSave, modifier = Modifier.weight(1f)) { Text("저장") }
            }

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        webViewClient = WebViewClient()
                        addJavascriptInterface(WebAppInterface(ctx), "Android")
                        loadUrl("http://10.0.0.97:8081")
                        onWebViewCreated(this)
                    }
                }
            )
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
            CoroutineScope(Dispatchers.Main).launch {
                Toast.makeText(context, "저장됨: Download/$fileName", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            CoroutineScope(Dispatchers.Main).launch {
                Toast.makeText(context, "저장 실패: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
