package com.example.mylogcat

import android.os.Bundle
import android.webkit.WebView
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        webView.settings.javaScriptEnabled = true

        // 서버의 사설 IP 주소 입력 (WireGuard 연결 시)
        val logdyUrl = "http://10.0.0.252:8081" 
        webView.loadUrl(logdyUrl)

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            // TODO: Termux 스크립트 실행 또는 Kotlin으로 직접 소켓 통신 구현
            webView.reload()
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            // TODO: 로그 수집 중지 로직
        }

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            // TODO: 현재 웹뷰의 로그를 다운로드 폴더에 저장하는 로직
            saveLogToDownload()
        }
    }

    private fun saveLogToDownload() {
        // 파일 저장 예시 (실제로는 서버에서 데이터를 받아와야 함)
        val downloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadDir, "mylogcat_log.txt")
        FileOutputStream(file).use {
            it.write("테스트 로그 저장 데이터".toByteArray())
        }
    }
}
