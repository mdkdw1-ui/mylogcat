package com.example.mylogcat

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
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
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
}

@Composable
fun AppContent() {
    val logs by LogRepository.logs.collectAsState()
    val isCollecting by LogRepository.isCollecting.collectAsState()

    var showOnlyErrors by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var isUserScrolling by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

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
            text = "MYLOGCAT",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = NeonCyan,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    if (isCollecting) {
                        context.stopService(
                            Intent(context, LogCollectionService::class.java)
                        )
                    } else {
                        context.startForegroundService(
                            Intent(context, LogCollectionService::class.java)
                        )
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent
                ),
                contentPadding = PaddingValues(0.dp)
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isCollecting) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.Black
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isCollecting) "중지" else "수집 시작",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
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
                        "MyLogcat_$timeStamp.txt"
                    )

                    try {
                        FileOutputStream(file).use { out ->
                            out.write(logs.joinToString("\n").toByteArray())
                        }
                        Toast.makeText(
                            context,
                            "저장 완료: ${file.name}",
                            Toast.LENGTH_SHORT
                        ).show()
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            "저장 실패: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonPurple
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = null,
                    tint = Color.Black
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "저장",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            placeholder = {
                Text(
                    text = "검색어 입력",
                    color = TextGray
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeonCyan,
                unfocusedBorderColor = Color(0xFF3F3F50),
                cursorColor = NeonCyan,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

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
                text = "에러만 보기",
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
                    text = "수집 시작을 누르세요.",
                    color = TextGray,
                    modifier = Modifier.align(Alignment.Center),
                    fontSize = 14.sp
                )
            } else {
                val filteredLogs = logs.filter { line ->
                    val matchesSearch = if (searchText.isBlank()) {
                        true
                    } else {
                        line.contains(searchText, ignoreCase = true)
                    }

                    val isError = line.contains(" E ") ||
                            line.contains("FATAL") ||
                            line.contains("Exception")

                    if (showOnlyErrors) {
                        matchesSearch && isError
                    } else {
                        matchesSearch
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    items(filteredLogs) { logLine ->
                        Text(
                            text = logLine,
                            color = when {
                                logLine.contains("FATAL") -> Color(0xFFFF5252)
                                logLine.contains(" E ") -> Color(0xFFFF8A80)
                                logLine.contains(" W ") -> Color(0xFFFFD54F)
                                else -> Color(0xFF69F0AE)
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
