package com.local.lancopypaste

import android.Manifest
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.local.lancopypaste.ui.theme.LANCopyPasteTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import kotlin.math.cos
import kotlin.math.sin

enum class AppTab(val title: String) {
    Home("Chính"),
    History("Lịch sử"),
    Settings("Cài đặt")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLogger.log(this, "Mở app")
        requestNotificationPermission()
        enableEdgeToEdge()
        setContent {
            LANCopyPasteTheme {
                CopyPasteApp(
                    sharedText = extractSharedText(intent),
                    sharedImageUri = extractSharedImageUri(intent)
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        AppLogger.log(this, "Nhận intent mới: ${intent.action} ${intent.type}")
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
        if (intent.type.orEmpty().startsWith("image/")) return ""
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
        val title = intent.getCharSequenceExtra(Intent.EXTRA_TITLE)?.toString().orEmpty()
        return listOf(title, text).map { it.trim() }.filter { it.isNotBlank() }.distinct().joinToString("\n")
    }

    private fun extractSharedImageUri(intent: Intent?): String {
        if (intent?.action != Intent.ACTION_SEND) return ""
        if (!intent.type.orEmpty().startsWith("image/")) return ""
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)?.toString().orEmpty()
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.toString().orEmpty()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CopyPasteApp(sharedText: String, sharedImageUri: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(AppTab.Home) }
    var status by remember { mutableStateOf("Sẵn sàng") }
    var sendText by remember { mutableStateOf(sharedText) }
    var sendImageUri by remember { mutableStateOf(sharedImageUri) }
    var receivedText by remember { mutableStateOf("") }
    var receivedImageDataUrl by remember { mutableStateOf("") }
    var receivedMeta by remember { mutableStateOf("Chưa tải nội dung nào") }
    var serverUrl by remember { mutableStateOf(ClipSender.getServerUrl(context)) }
    var sending by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, serverUrl) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            ClipSender.updateDeviceStatus(context, true, "android-resume")
                        }
                    }
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_DESTROY -> {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            ClipSender.updateDeviceStatus(context, false, "android-background")
                        }
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun sendTextNow(text: String, fromName: String) {
        ClipSender.saveServerUrl(context, serverUrl)
        sending = true
        status = "Đang gửi..."
        scope.launch {
            val result = withContext(Dispatchers.IO) { ClipSender.send(context, text, fromName) }
            status = if (result.isSuccess) "Đã gửi" else "Gửi thất bại: ${result.exceptionOrNull()?.message}"
            sending = false
        }
    }

    fun sendImageNow(uri: String, fromName: String) {
        ClipSender.saveServerUrl(context, serverUrl)
        sending = true
        status = "Đang gửi ảnh..."
        scope.launch {
            val result = withContext(Dispatchers.IO) { ClipSender.sendImage(context, uri, fromName) }
            status = if (result.isSuccess) "Đã gửi ảnh" else "Gửi ảnh thất bại: ${result.exceptionOrNull()?.message}"
            sending = false
        }
    }

    fun loadLatest() {
        ClipSender.saveServerUrl(context, serverUrl)
        status = "Đang tải nội dung mới nhất..."
        scope.launch {
            val result = withContext(Dispatchers.IO) { ClipSender.latest(context) }
            result.onSuccess { clip ->
                if (clip == null || (clip.text.isBlank() && clip.dataUrl.isBlank())) {
                    receivedText = ""
                    receivedImageDataUrl = ""
                    receivedMeta = "Server chưa có nội dung"
                    status = "Chưa có nội dung để nhận"
                } else {
                    receivedText = clip.text
                    receivedImageDataUrl = clip.dataUrl
                    receivedMeta = "${if (clip.kind == "image") "Ảnh" else "Văn bản"} từ ${clip.fromName} lúc ${formatTime(clip.createdAt)}"
                    status = "Đã tải nội dung mới nhất"
                }
            }.onFailure {
                status = "Tải thất bại: ${it.message}"
            }
        }
    }

    LaunchedEffect(sharedText) {
        if (sharedText.isNotBlank()) {
            AppLogger.log(context, "Nhận chia sẻ text từ Android (${sharedText.length} ký tự)")
            sendTextNow(sharedText, "Android Share")
        }
    }

    LaunchedEffect(sharedImageUri) {
        if (sharedImageUri.isNotBlank()) {
            AppLogger.log(context, "Nhận chia sẻ ảnh từ Android: $sharedImageUri")
            sendImageNow(sharedImageUri, "Android Share")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text("LAN CopyPaste", fontWeight = FontWeight.Bold)
                    Text(status, style = MaterialTheme.typography.bodySmall)
                }
            })
        },
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { NavIcon(item, selected = tab == item) },
                        label = { Text(item.title) }
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
                sendImageUri = sendImageUri,
                onSendImageUriChange = { sendImageUri = it },
                receivedText = receivedText,
                receivedImageDataUrl = receivedImageDataUrl,
                receivedMeta = receivedMeta,
                sending = sending,
                onPaste = { sendText = readClipboardText(context) },
                onSend = {
                    if (sendImageUri.isNotBlank()) sendImageNow(sendImageUri, "Android App")
                    else sendTextNow(sendText, "Android App")
                },
                onLoadLatest = { loadLatest() },
                onCopyReceived = {
                    if (receivedText.isNotBlank()) {
                        writeClipboardText(context, receivedText)
                        status = "Đã sao chép nội dung đã nhận"
                    } else {
                        status = "Copy ảnh sẽ được thêm ở bước sau"
                    }
                }
            )
            AppTab.History -> HistoryScreen(
                modifier = Modifier.padding(innerPadding),
                onOpenItem = { clip ->
                    receivedText = clip.text
                    receivedImageDataUrl = clip.dataUrl
                    receivedMeta = "${if (clip.kind == "image") "Ảnh" else "Văn bản"} từ ${clip.fromName} lúc ${formatTime(clip.createdAt)}"
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
private fun NavIcon(tab: AppTab, selected: Boolean) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val cutout = MaterialTheme.colorScheme.surface
    Canvas(modifier = Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        when (tab) {
            AppTab.Home -> {
                drawRoundRect(color, Offset(w * .22f, h * .28f), Size(w * .56f, h * .5f), CornerRadius(w * .08f))
                drawLine(cutout, Offset(w * .34f, h * .45f), Offset(w * .66f, h * .45f), strokeWidth = w * .06f)
                drawLine(cutout, Offset(w * .34f, h * .58f), Offset(w * .56f, h * .58f), strokeWidth = w * .06f)
            }
            AppTab.History -> {
                drawCircle(color, radius = w * .34f, center = Offset(w * .5f, h * .5f), style = Stroke(width = w * .08f))
                drawLine(color, Offset(w * .5f, h * .5f), Offset(w * .5f, h * .28f), strokeWidth = w * .08f)
                drawLine(color, Offset(w * .5f, h * .5f), Offset(w * .66f, h * .58f), strokeWidth = w * .08f)
            }
            AppTab.Settings -> {
                repeat(8) { index ->
                    val angle = Math.toRadians((index * 45).toDouble())
                    drawLine(
                        color,
                        Offset(w * .5f + cos(angle).toFloat() * w * .3f, h * .5f + sin(angle).toFloat() * h * .3f),
                        Offset(w * .5f + cos(angle).toFloat() * w * .42f, h * .5f + sin(angle).toFloat() * h * .42f),
                        strokeWidth = w * .07f
                    )
                }
                drawCircle(color, radius = w * .2f, center = Offset(w * .5f, h * .5f), style = Stroke(width = w * .08f))
            }
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

fun formatTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))
}
