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
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

val DarkBackground = Color(0xFF1E1E24)
val CardBackground = Color(0xFF2B2B36)
val NeonCyan = Color(0xFF00E5FF)
val NeonPurple = Color(0xFFB388FF)
val TextGray = Color(0xFFB0B0B0)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = NeonCyan,
                    secondary = NeonPurple,
                    background = DarkBackground,
                    surface = CardBackground
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    AppContent()
                }
            }
        }
    }

    private fun handleVpnToggle(turnOn: Boolean, viewModel: LogViewModel) {
        if (turnOn) {
            val prefs = getSharedPreferences("mylogcat", MODE_PRIVATE)
            val uriString = prefs.getString("wg_conf_uri", null)

            if (uriString == null) {
                viewModel.setVpnState(false)
                return
            }

            val intent = VpnService.prepare(this)

            if (intent != null) {
                startActivityForResult(intent, 100)
            } else {
                startVpnService(Uri.parse(uriString))
            }
        } else {
            stopService(
                Intent(this, WireGuardService::class.java).setAction("stop")
            )
        }
    }

    private fun startVpnService(confUri: Uri) {
        startService(
            Intent(this, WireGuardService::class.java).apply {
                action = "start"
                putExtra("confUri", confUri.toString())
            }
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 100 && resultCode == RESULT_OK) {
            val prefs = getSharedPreferences("mylogcat", MODE_PRIVATE)
            val uriString = prefs.getString("wg_conf_uri", null)

            if (uriString != null) {
                startVpnService(Uri.parse(uriString))
            }
        }
    }

    private fun handleConfFile(uri: Uri, viewModel: LogViewModel) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )

            getSharedPreferences("mylogcat", MODE_PRIVATE)
                .edit()
                .putString("wg_conf_uri", uri.toString())
                .apply()

            viewModel.setHasConf(true)
        } catch (e: Exception) {
            viewModel.setHasConf(false)
        }
    }

    @Composable
    fun AppContent(viewModel: LogViewModel = viewModel()) {
        val isVpnOn by viewModel.isVpnOn.collectAsState()
        val hasConf by viewModel.hasConf.collectAsState()
        val logs by viewModel.logs.collectAsState()

        var isCollecting by remember { mutableStateOf(false) }
        var showOnlyErrors by remember { mutableStateOf(false) }
        var isUserScrolling by remember { mutableStateOf(false) }

        val context = LocalContext.current
        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()

        val filePickerLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            uri?.let { handleConfFile(it, viewModel) }
        }

        LaunchedEffect(logs.size) {
            if (logs.isNotEmpty() && !isUserScrolling) {
                listState.animateScrollToItem(logs.size - 1)
            }
        }

        LaunchedEffect(listState.isScrollInProgress) {
            isUserScrolling = listState.isScrollInProgress
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "MYLOGCAT PRO",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = NeonCyan,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .shadow(8.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF2A2A35), Color(0xFF1E1E24))
                            )
                        )
                        .padding(16.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Security,
                                contentDescription = null,
                                tint = NeonPurple
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                "WireGuard VPN",
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            Switch(
                                checked = isVpnOn,
                                onCheckedChange = {
                                    viewModel.setVpnState(it)
                                    handleVpnToggle(it, viewModel)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = NeonCyan,
                                    checkedTrackColor = NeonCyan.copy(alpha = 0.5f)
                                )
                            )
                        }

                        if (!hasConf) {
                            Text(
                                "client.conf 파일을 선택하세요",
                                color = Color.Red,
                                fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                filePickerLauncher.launch(arrayOf("*/*"))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF3F3F50)
                            )
                        ) {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = null,
                                tint = TextGray
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                if (hasConf) "설정 파일 변경" else "client.conf 선택",
                                color = Color.White
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        if (isCollecting) {
                            isCollecting = false
                            viewModel.stopLogcat()
                        } else {
                            isCollecting = true
                            scope.launch {
                                viewModel.startLiveLogcat()
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .shadow(6.dp, RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent
                    ),
                    contentPadding = PaddingValues()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    if (isCollecting) {
                                        listOf(Color(0xFFFF5252), Color(0xFFD50000))
                                    } else {
                                        listOf(NeonCyan, Color(0xFF00B0FF))
                                    }
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (isCollecting) "수집 중지" else "로그 수집",
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }

                Button(
                    onClick = {
                        val timeStamp = SimpleDateFormat(
                            "yyyyMMdd_HHmmss",
                            Locale.getDefault()
                        ).format(Date())

                        val file = File(
                            Environment.getExternalStoragePublicDirectory(
                                Environment.DIRECTORY_DOWNLOADS
                            ),
                            "Logcat_$timeStamp.txt"
                        )

                        try {
                            FileOutputStream(file).use {
                                it.write(logs.joinToString("\n").toByteArray())
                            }

                            android.widget.Toast.makeText(
                                context,
                                "저장됨: ${file.name}",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(
                                context,
                                "저장 실패: ${e.message}",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .shadow(6.dp, RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonPurple
                    )
                ) {
                    Icon(
                        Icons.Default.Save,
                        contentDescription = null,
                        tint = Color.Black
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        "파일 저장",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CardBackground)
                    .clickable {
                        showOnlyErrors = !showOnlyErrors
                    }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = showOnlyErrors,
                    onCheckedChange = {
                        showOnlyErrors = it
                    },
                    colors = CheckboxDefaults.colors(
                        checkedColor = NeonCyan
                    )
                )

                Text(
                    "에러(Error) 및 크래시 로그만 보기",
                    color = Color.White,
                    fontSize = 14.sp
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F0F13))
            ) {
                if (logs.isEmpty()) {
                    Text(
                        "로그 수집 버튼을 누르면\n실시간 로그가 여기에 표시됩니다.",
                        color = TextGray,
                        modifier = Modifier.align(Alignment.Center),
                        fontSize = 14.sp
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    ) {
                        val filteredLogs = logs.filter { line ->
                val matchesSearch = searchText.isBlank() ||
                    line.contains(searchText, ignoreCase = true)

                val isError = line.contains(" E ") ||
                    line.contains("FATAL") ||
                    line.contains("Exception")

                val isMyLogcat = line.contains("com.example.mylogcat", ignoreCase = true)

                val isCrash = line.contains("FATAL", ignoreCase = true) ||
                    line.contains("AndroidRuntime", ignoreCase = true) ||
                    line.contains("Exception", ignoreCase = true)

                val shouldShowMyLogcat = !isMyLogcat || isCrash

                matchesSearch &&
                    shouldShowMyLogcat &&
                    (!showOnlyErrors || isError)
            }

                        items(filteredLogs) { logLine ->
                            Text(
                                text = logLine,
                                color = if (
                                    logLine.contains(" E ") ||
                                    logLine.contains("FATAL")
                                ) {
                                    Color(0xFFFF5252)
                                } else {
                                    Color(0xFF69F0AE)
                                },
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
