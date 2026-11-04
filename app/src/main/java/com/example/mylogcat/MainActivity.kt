package com.example.mylogcat

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(
                        onStart = { ContextCompat.startForegroundService(this, Intent(this, LogService::class.java)) },
                        onStop = { stopService(Intent(this, LogService::class.java)) },
                        onSave = { saveLogsFromWebView() },
                        onWebViewCreated = { webViewInstance = it }
                    )
                }
            }
        }
    }

    private fun saveLogsFromWebView() {
        webViewInstance?.evaluateJavascript("""
            (function() {
                // Logdy 화면에서 로그 텍스트만 추출하는 자바스크립트
                var logs = document.body.innerText; 
                if(logs) {
                    Android.saveLogToFile(logs);
                } else {
                    Android.saveLogToFile("저장할 로그가 없습니다.");
                }
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
    onWebViewCreated: (WebView) -> Unit
) {
    var isRunning by remember { mutableStateOf(false) }
    var serverStatus by remember { mutableStateOf("서버 확인 중...") }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress("10.0.0.97", 8081), 3000)
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

            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = { isRunning = true; onStart() }, enabled = !isRunning, modifier = Modifier.weight(1f)) { Text("시작") }
                Button(onClick = { isRunning = false; onStop() }, enabled = isRunning, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.weight(1f)) { Text("중지") }
                Button(onClick = onSave, modifier = Modifier.weight(1f)) { Text("저장") }
            }

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        webViewClient = WebViewClient()
                        // 자바스크립트에서 파일 저장을 호출할 수 있도록 브릿지 연결
                        addJavascriptInterface(WebAppInterface(ctx), "Android")
                        loadUrl("http://10.0.0.97:8081")
                        onWebViewCreated(this)
                    }
                }
            )
        }
    }
}

// 자바스크립트와 통신하여 파일을 저장하는 클래스
class WebAppInterface(private val context: android.content.Context) {
    @JavascriptInterface
    fun saveLogToFile(logText: String) {
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "Logcat_$timeStamp.txt"
            
            // 다운로드 폴더 경로
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadDir, fileName)
            
            FileOutputStream(file).use {
                it.write(logText.toByteArray())
            }
            
            // UI 스레드에서 토스트 메시지 띄우기
            CoroutineScope(Dispatchers.Main).launch {
                Toast.makeText(context, "다운로드 폴더에 저장되었습니다: $fileName", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            CoroutineScope(Dispatchers.Main).launch {
                Toast.makeText(context, "저장 실패: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
