package com.local.lancopypaste

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.local.lancopypaste.ui.theme.LANCopyPasteTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    modifier: Modifier,
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    onStatus: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var watcherEnabled by remember { mutableStateOf(ClipSender.isClipboardWatcherEnabled(context)) }
    var pairCode by remember { mutableStateOf("") }
    var scannerVisible by remember { mutableStateOf(false) }
    var pairingInProgress by remember { mutableStateOf(false) }
    var logExpanded by remember { mutableStateOf(false) }
    var logPreview by remember { mutableStateOf(AppLogger.read(context).takeLast(1200)) }

    Column(
        modifier = modifier.padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
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
            Button(
                onClick = {
                    onStatus("Đang tìm máy chủ trong LAN...")
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { ClipSender.discoverServer(context) }
                        result.onSuccess {
                            onServerUrlChange(it)
                            ClipSender.saveServerUrl(context, it)
                            onStatus("Đã tìm thấy máy chủ")
                        }.onFailure {
                            onStatus("Không tìm thấy máy chủ: ${it.message}")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Tự tìm máy chủ")
            }
        }

        UtilityCard(title = "Ghép nối") {
            OutlinedTextField(
                value = pairCode,
                onValueChange = { pairCode = it.take(6) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Mã 6 số") },
                singleLine = true
            )
            Button(
                onClick = {
                    if (pairingInProgress) return@Button
                    pairingInProgress = true
                    scope.launch {
                        val normalizedServer = ClipSender.normalizeUrl(serverUrl)
                        ClipSender.saveServerUrl(context, normalizedServer)
                        onServerUrlChange(normalizedServer)
                        val result = withContext(Dispatchers.IO) {
                            ClipSender.pair(context, pairCode, "Android", normalizedServer)
                        }
                        onStatus(if (result.isSuccess) "Đã ghép nối" else "Ghép nối thất bại: ${result.exceptionOrNull()?.message}")
                        if (result.isSuccess) pairCode = ""
                        pairingInProgress = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !pairingInProgress
            ) {
                Text("Ghép nối thiết bị")
            }
            Button(
                onClick = { scannerVisible = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = !pairingInProgress
            ) {
                Text("Quét QR ghép nối")
            }
            if (scannerVisible) {
                QrScannerView(
                    modifier = Modifier.fillMaxWidth(),
                    onQrFound = { rawValue ->
                        scannerVisible = false
                        val parsed = parsePairingQr(rawValue)
                        if (parsed == null) {
                            onStatus("QR không hợp lệ")
                            return@QrScannerView
                        }

                        val (serverFromQr, codeFromQr) = parsed
                        pairCode = codeFromQr
                        if (!serverFromQr.isNullOrBlank()) {
                            onServerUrlChange(serverFromQr)
                            ClipSender.saveServerUrl(context, serverFromQr)
                        }
                        pairingInProgress = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                ClipSender.pair(context, codeFromQr, "Android", serverFromQr)
                            }
                            onStatus(if (result.isSuccess) "Đã ghép nối bằng QR" else "Ghép nối QR thất bại: ${result.exceptionOrNull()?.message}")
                            if (result.isSuccess) pairCode = ""
                            pairingInProgress = false
                        }
                    },
                    onClose = { scannerVisible = false }
                )
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
                            try {
                                ContextCompat.startForegroundService(context, intent)
                                onStatus("Đã bật theo dõi clipboard")
                            } catch (error: Exception) {
                                watcherEnabled = false
                                ClipSender.saveClipboardWatcherEnabled(context, false)
                                onStatus("Không bật được theo dõi clipboard: ${error.message}")
                            }
                        } else {
                            context.stopService(intent)
                            onStatus("Đã tắt theo dõi clipboard")
                        }
                    }
                )
            }
        }

        UtilityCard(title = "Kiểm tra kết nối") {
            Text(
                "Kiểm tra trạng thái với máy chủ hoặc chủ động ngắt kết nối thiết bị này.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Button(
                onClick = {
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            ClipSender.updateDeviceStatus(context, true, "android-manual")
                        }
                        onStatus(if (result.isSuccess) "Kết nối đang hoạt động" else "Kiểm tra kết nối thất bại: ${result.exceptionOrNull()?.message}")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Kiểm tra kết nối")
            }
            Button(
                onClick = {
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            ClipSender.updateDeviceStatus(context, false, "android-manual")
                        }
                        onStatus(if (result.isSuccess) "Đã ngắt kết nối" else "Ngắt kết nối thất bại: ${result.exceptionOrNull()?.message}")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Ngắt kết nối")
            }
        }

        UtilityCard(title = "Nhật ký") {
            if (logExpanded) {
                Text(
                    logPreview.ifBlank { "Chưa có log." },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Button(
                onClick = {
                    logExpanded = !logExpanded
                    if (logExpanded) logPreview = AppLogger.read(context).takeLast(1200)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (logExpanded) "Ẩn log" else "Hiện log")
            }
            Button(
                onClick = {
                    logPreview = AppLogger.read(context).takeLast(1200)
                    onStatus("Đã tải lại log")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Tải lại log")
            }
            Button(
                onClick = {
                    scope.launch {
                        val logText = AppLogger.read(context)
                        val result = withContext(Dispatchers.IO) {
                            ClipSender.send(context, logText, "Android Log")
                        }
                        onStatus(if (result.isSuccess) "Đã gửi log nội bộ" else "Gửi log thất bại: ${result.exceptionOrNull()?.message}")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Gửi log nội bộ")
            }
            Button(
                onClick = {
                    runCatching { AppLogger.shareLog(context) }
                        .onSuccess { onStatus("Đã mở chia sẻ log") }
                        .onFailure { onStatus("Không chia sẻ được log: ${it.message}") }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Chia sẻ log")
            }
            Button(
                onClick = {
                    runCatching { AppLogger.exportLogFile(context) }
                        .onSuccess {
                            logPreview = AppLogger.read(context).takeLast(1200)
                            onStatus("Đã xuất log: ${it.name}")
                        }
                        .onFailure { onStatus("Xuất log thất bại: ${it.message}") }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Xuất file log")
            }
            Button(
                onClick = {
                    AppLogger.clear(context)
                    logPreview = AppLogger.read(context).takeLast(1200)
                    onStatus("Đã xóa log trong app")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Xóa log")
            }
        }
    }
}

private fun parsePairingQr(rawValue: String): Pair<String?, String>? {
    val trimmed = rawValue.trim()
    if (trimmed.matches(Regex("^\\d{6}$"))) {
        return null to trimmed
    }

    return runCatching {
        val uri = Uri.parse(trimmed)
        val code = uri.getQueryParameter("pair") ?: return@runCatching null
        val host = uri.host ?: return@runCatching null
        val scheme = uri.scheme ?: "http"
        val port = if (uri.port > 0) ":${uri.port}" else ""
        "$scheme://$host$port" to code
    }.getOrNull()
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun SettingsScreenPreview() {
    LANCopyPasteTheme {
        SettingsScreen(
            modifier = Modifier,
            serverUrl = "http://192.168.1.101:3000",
            onServerUrlChange = {},
            onStatus = {}
        )
    }
}
