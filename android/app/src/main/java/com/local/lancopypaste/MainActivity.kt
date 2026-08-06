package com.local.lancopypaste

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.local.lancopypaste.ui.theme.LANCopyPasteTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LANCopyPasteTheme {
                CopyPasteApp(sharedText = extractSharedText(intent))
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    private fun extractSharedText(intent: Intent?): String {
        if (intent?.action != Intent.ACTION_SEND) return ""
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
        val title = intent.getCharSequenceExtra(Intent.EXTRA_TITLE)?.toString().orEmpty()

        return listOf(title, text)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("\n")
    }
}

@Composable
fun CopyPasteApp(sharedText: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var serverUrl by remember { mutableStateOf(ClipSender.getServerUrl(context)) }
    var text by remember { mutableStateOf(sharedText) }
    var status by remember { mutableStateOf("Sẵn sàng") }
    var sending by remember { mutableStateOf(false) }
    var receivedText by remember { mutableStateOf("") }
    var receivedMeta by remember { mutableStateOf("Chưa tải nội dung nào") }

    LaunchedEffect(sharedText) {
        if (sharedText.isNotBlank()) {
            sending = true
            status = "Đang gửi nội dung được chia sẻ..."
            val result = withContext(Dispatchers.IO) {
                ClipSender.send(context, sharedText, "Android Share")
            }
            status = if (result.isSuccess) {
                "Đã gửi nội dung được chia sẻ"
            } else {
                "Gửi thất bại: ${result.exceptionOrNull()?.message}"
            }
            sending = false
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Header(status = status, serverUrl = serverUrl)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Máy chủ",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("URL hoặc IP") },
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            serverUrl = ClipSender.normalizeUrl(serverUrl)
                            ClipSender.saveServerUrl(context, serverUrl)
                            status = "Đã lưu máy chủ"
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Lưu máy chủ")
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Đã nhận",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = receivedText,
                        onValueChange = {},
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        label = { Text("Nội dung mới nhất") },
                        placeholder = { Text("Bấm \"Tải mới nhất\" để xem nội dung từ server") },
                        readOnly = true
                    )
                    Text(
                        text = receivedMeta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                ClipSender.saveServerUrl(context, serverUrl)
                                status = "Đang tải nội dung mới nhất..."
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        ClipSender.latest(context)
                                    }
                                    result.onSuccess { clip ->
                                        if (clip == null || clip.text.isBlank()) {
                                            receivedText = ""
                                            receivedMeta = "Server chưa có nội dung"
                                            status = "Chưa có nội dung để nhận"
                                        } else {
                                            receivedText = clip.text
                                            receivedMeta = "Từ ${clip.fromName}"
                                            status = "Đã tải nội dung mới nhất"
                                        }
                                    }.onFailure {
                                        status = "Tải thất bại: ${it.message}"
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Tải mới nhất")
                        }
                        Button(
                            enabled = receivedText.isNotBlank(),
                            onClick = {
                                writeClipboardText(context, receivedText)
                                status = "Đã sao chép nội dung đã nhận"
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Sao chép")
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Gửi sang laptop",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp),
                        label = { Text("Nội dung") },
                        placeholder = { Text("Dán hoặc nhập văn bản cần gửi...") }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { text = readClipboardText(context) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Dán")
                        }
                        Button(
                            enabled = !sending,
                            onClick = {
                                ClipSender.saveServerUrl(context, serverUrl)
                                sending = true
                                status = "Đang gửi..."
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        ClipSender.send(context, text, "Android App")
                                    }
                                    status = if (result.isSuccess) {
                                        "Đã gửi"
                                    } else {
                                        "Gửi thất bại: ${result.exceptionOrNull()?.message}"
                                    }
                                    sending = false
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Gửi")
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Gửi nhanh bằng Quick Tile",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Thêm tile \"Gửi clipboard\" vào Quick Settings. Sau khi copy văn bản, kéo bảng nhanh và bấm tile để gửi sang laptop.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Header(status: String, serverUrl: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "LAN CopyPaste",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Chia sẻ văn bản trong mạng LAN",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                ) {
                }
            }
            AssistChip(
                onClick = {},
                label = { Text(status) }
            )
            Text(
                text = serverUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

private fun readClipboardText(context: Context): String {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = clipboard.primaryClip ?: return ""
    val description = clip.description ?: return ""
    if (!description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) &&
        !description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)
    ) {
        return ""
    }

    return clip.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
}

private fun writeClipboardText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("LAN CopyPaste", text))
}
