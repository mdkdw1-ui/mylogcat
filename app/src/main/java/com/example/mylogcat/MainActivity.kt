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
                        onStart = { ContextCompat.startForegroundService(this, Intent(this, LogService::class.java)) },
                        onStop = { stopService(Intent(this, LogService::class.java)) },
                        onSave = { saveLogsFromWebView() },
                        onWebViewCreated = { webViewInstance = it },
                        onConfSelected = { uri -> handleConfFile(uri) },
                        onRequestVpnPermission = { startVpnPermissionActivity() }
                    )
                }
            }
        }
    }

    // 1. 사용자가 client.conf 선택 시 내부 저장소에 저장 후 VPN 권한 요청
    private fun handleConfFile(uri: Uri) {
        try {
            val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (text.isNullOrBlank()) {
                Toast.makeText(this, "파일이 비어있습니다.", Toast.LENGTH_SHORT).show()
                return
            }
            val file = File(filesDir, "wg.conf")
            file.writeText(text)
            pendingConfFile = file
            
            // VPN 권한 요청 (안드로이드 시스템 팝업)
            val intent = VpnService.prepare(this)
            if (intent != null) {
                startActivityForResult(intent, 100)
            } else {
                startVpnService(file)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "파일 읽기 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startVpnPermissionActivity() {
        val intent = VpnService.prepare(this)
        if (intent != null) startActivityForResult(intent, 100)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            pendingConfFile?.let { startVpnService(it) }
        }
    }

    // 2. VPN 서비스 시작
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
    onStart: () -> Unit,
    onStop: () -> Unit,
    onSave: () -> Unit,
    onWebViewCreated: (WebView) -> Unit,
    onConfSelected: (Uri) -> Unit,
    onRequestVpnPermission: () -> Unit
) {
    var isRunning by remember { mutableStateOf(false) }
    var serverStatus by remember { mutableStateOf("서버 확인 중...") }
    val context = LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onConfSelected(it) }
    }

    // 서버 상태 체크
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress("10.0.0.97", 8081), 2000)
                socket.close()
                serverStatus = "서버 연결됨 (VPN 활성화 상태)"
            } catch (e: Exception) {
                serverStatus = "서버 연결 실패 (VPN을 켜주세요)"
            }
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

            // VPN 제어 버튼
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { filePickerLauncher.launch("*/*") }, modifier = Modifier.weight(1f)) {
                    Text("client.conf 로드 및 VPN 연결")
                }
                OutlinedButton(onClick = {
                    context.startService(Intent(context, WireGuardService::class.java).setAction("stop"))
                }, modifier = Modifier.weight(1f)) {
                    Text("VPN 해제")
                }
            }

            // 로그 제어 버튼
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = { isRunning = true; onStart() }, enabled = !isRunning, modifier = Modifier.weight(1f)) { Text("로그 시작") }
                Button(onClick = { isRunning = false; onStop() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), enabled = isRunning, modifier = Modifier.weight(1f)) { Text("로그 중지") }
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
