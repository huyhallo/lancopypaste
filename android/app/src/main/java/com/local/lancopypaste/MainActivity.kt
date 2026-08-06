package com.local.lancopypaste

import android.Manifest
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.local.lancopypaste.ui.theme.LANCopyPasteTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private enum class AppTab(val title: String) {
    Home("Chính"),
    History("Lịch sử"),
    Settings("Cài đặt")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()
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

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CopyPasteApp(sharedText: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(AppTab.Home) }
    var status by remember { mutableStateOf("Sẵn sàng") }
    var sendText by remember { mutableStateOf(sharedText) }
    var receivedText by remember { mutableStateOf("") }
    var receivedMeta by remember { mutableStateOf("Chưa tải nội dung nào") }
    var serverUrl by remember { mutableStateOf(ClipSender.getServerUrl(context)) }
    var sending by remember { mutableStateOf(false) }

    fun sendCurrent(text: String, fromName: String) {
        ClipSender.saveServerUrl(context, serverUrl)
        sending = true
        status = "Đang gửi..."
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                ClipSender.send(context, text, fromName)
            }
            status = if (result.isSuccess) "Đã gửi" else "Gửi thất bại: ${result.exceptionOrNull()?.message}"
            sending = false
        }
    }

    fun loadLatest() {
        ClipSender.saveServerUrl(context, serverUrl)
        status = "Đang tải nội dung mới nhất..."
        scope.launch {
            val result = withContext(Dispatchers.IO) { ClipSender.latest(context) }
            result.onSuccess { clip ->
                if (clip == null || clip.text.isBlank()) {
                    receivedText = ""
                    receivedMeta = "Server chưa có nội dung"
                    status = "Chưa có nội dung để nhận"
                } else {
                    receivedText = clip.text
                    receivedMeta = "Từ ${clip.fromName} lúc ${formatTime(clip.createdAt)}"
                    status = "Đã tải nội dung mới nhất"
                }
            }.onFailure {
                status = "Tải thất bại: ${it.message}"
            }
        }
    }

    LaunchedEffect(sharedText) {
        if (sharedText.isNotBlank()) {
            sendCurrent(sharedText, "Android Share")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("LAN CopyPaste", fontWeight = FontWeight.Bold)
                        Text(status, style = MaterialTheme.typography.bodySmall)
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        label = { Text(item.title) },
                        icon = {}
                    )
                }
            }
        }
    ) { innerPadding ->
        when (tab) {
            AppTab.Home -> HomeScreen(
                modifier = Modifier.padding(innerPadding),
                sendText = sendText,
                onSendTextChange = { sendText = it },
                receivedText = receivedText,
                receivedMeta = receivedMeta,
                sending = sending,
                onPaste = { sendText = readClipboardText(context) },
                onSend = { sendCurrent(sendText, "Android App") },
                onLoadLatest = { loadLatest() },
                onCopyReceived = {
                    writeClipboardText(context, receivedText)
                    status = "Đã sao chép nội dung đã nhận"
                }
            )
            AppTab.History -> HistoryScreen(
                modifier = Modifier.padding(innerPadding),
                onOpenItem = { clip ->
                    receivedText = clip.text
                    receivedMeta = "Từ ${clip.fromName} lúc ${formatTime(clip.createdAt)}"
                    tab = AppTab.Home
                }
            )
            AppTab.Settings -> SettingsScreen(
                modifier = Modifier.padding(innerPadding),
                serverUrl = serverUrl,
                onServerUrlChange = { serverUrl = it },
                onStatus = { status = it }
            )
        }
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier,
    sendText: String,
    onSendTextChange: (String) -> Unit,
    receivedText: String,
    receivedMeta: String,
    sending: Boolean,
    onPaste: () -> Unit,
    onSend: () -> Unit,
    onLoadLatest: () -> Unit,
    onCopyReceived: () -> Unit
) {
    Column(
        modifier = modifier
            .padding(16.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        UtilityCard(title = "Gửi") {
            OutlinedTextField(
                value = sendText,
                onValueChange = onSendTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp),
                label = { Text("Nội dung") },
                placeholder = { Text("Dán hoặc nhập văn bản cần gửi...") }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onPaste, modifier = Modifier.weight(1f)) {
                    Text("Dán")
                }
                Button(enabled = !sending, onClick = onSend, modifier = Modifier.weight(1f)) {
                    Text("Gửi")
                }
            }
        }

        UtilityCard(title = "Nhận") {
            OutlinedTextField(
                value = receivedText,
                onValueChange = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp),
                label = { Text("Nội dung mới nhất") },
                placeholder = { Text("Bấm tải để lấy nội dung mới nhất từ server") },
                readOnly = true
            )
            Text(receivedMeta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onLoadLatest, modifier = Modifier.weight(1f)) {
                    Text("Tải")
                }
                Button(
                    enabled = receivedText.isNotBlank(),
                    onClick = onCopyReceived,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Sao chép")
                }
            }
        }
    }
}

@Composable
private fun HistoryScreen(modifier: Modifier, onOpenItem: (ReceivedClip) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<HistoryItem>>(emptyList()) }
    var status by remember { mutableStateOf("Chưa tải lịch sử") }

    fun loadHistory() {
        status = "Đang tải lịch sử..."
        scope.launch {
            val result = withContext(Dispatchers.IO) { ClipSender.history(context, 80) }
            result.onSuccess {
                items = it
                status = if (it.isEmpty()) "Chưa có lịch sử" else "${it.size} mục gần nhất"
            }.onFailure {
                status = "Tải thất bại: ${it.message}"
            }
        }
    }

    LaunchedEffect(Unit) { loadHistory() }

    Column(
        modifier = modifier
            .padding(16.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(status, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.secondary)
            OutlinedButton(onClick = { loadHistory() }) { Text("Tải lại") }
        }
        items.forEach { item ->
            Card(
                onClick = {
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { ClipSender.historyItem(context, item.id) }
                        result.onSuccess(onOpenItem)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.preview, maxLines = 3)
                    Text(
                        "${item.fromName} • ${formatTime(item.createdAt)} • ${item.length} ký tự",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    modifier: Modifier,
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    onStatus: (String) -> Unit
) {
    val context = LocalContext.current
    var watcherEnabled by remember { mutableStateOf(ClipSender.isClipboardWatcherEnabled(context)) }

    Column(
        modifier = modifier
            .padding(16.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        UtilityCard(title = "Máy chủ") {
            OutlinedTextField(
                value = serverUrl,
                onValueChange = onServerUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("URL hoặc IP") },
                singleLine = true
            )
            Button(
                onClick = {
                    val normalized = ClipSender.normalizeUrl(serverUrl)
                    ClipSender.saveServerUrl(context, normalized)
                    onServerUrlChange(normalized)
                    onStatus("Đã lưu máy chủ")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Lưu máy chủ")
            }
        }

        UtilityCard(title = "Tự hỏi khi clipboard đổi") {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Hiện thông báo trước khi gửi")
                    Text(
                        "Android có thể chỉ cho phép đọc clipboard trong một số ngữ cảnh. Bật tùy chọn này sẽ chạy foreground service.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Switch(
                    checked = watcherEnabled,
                    onCheckedChange = { enabled ->
                        watcherEnabled = enabled
                        ClipSender.saveClipboardWatcherEnabled(context, enabled)
                        val intent = Intent(context, ClipboardWatcherService::class.java)
                        if (enabled) {
                            ContextCompat.startForegroundService(context, intent)
                            onStatus("Đã bật theo dõi clipboard")
                        } else {
                            context.stopService(intent)
                            onStatus("Đã tắt theo dõi clipboard")
                        }
                    }
                )
            }
        }

        UtilityCard(title = "Gửi nhanh") {
            Text(
                "Thêm Quick Settings Tile \"Gửi clipboard\" để copy xong bấm gửi nhanh. Đây là cách ổn định nhất trên Android 14.",
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun UtilityCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
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

private fun formatTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))
}
