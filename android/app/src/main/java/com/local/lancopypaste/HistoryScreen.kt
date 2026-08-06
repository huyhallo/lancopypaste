package com.local.lancopypaste

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.local.lancopypaste.ui.theme.LANCopyPasteTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HistoryScreen(modifier: Modifier, onOpenItem: (ReceivedClip) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<HistoryItem>>(emptyList()) }
    var status by remember { mutableStateOf("Chưa tải lịch sử") }
    var query by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("") }
    var limit by remember { mutableStateOf(80) }

    fun loadHistory() {
        status = "Đang tải lịch sử..."
        scope.launch {
            val result = withContext(Dispatchers.IO) { ClipSender.history(context, limit, query, kind) }
            result.onSuccess {
                items = it
                status = if (it.isEmpty()) "Chưa có lịch sử" else "${it.size} mục gần nhất"
            }.onFailure {
                status = "Tải thất bại: ${it.message}"
            }
        }
    }

    LaunchedEffect(query, kind, limit) { loadHistory() }

    HistoryContent(
        modifier = modifier,
        status = status,
        items = items,
        query = query,
        kind = kind,
        limit = limit,
        onQueryChange = { query = it },
        onKindChange = { kind = it },
        onLimitChange = { limit = it },
        onRefresh = { loadHistory() },
        onOpenItem = { item ->
            scope.launch {
                val result = withContext(Dispatchers.IO) { ClipSender.historyItem(context, item.id) }
                result.onSuccess(onOpenItem)
            }
        }
    )
}

@Composable
private fun HistoryContent(
    modifier: Modifier,
    status: String,
    items: List<HistoryItem>,
    query: String,
    kind: String,
    limit: Int,
    onQueryChange: (String) -> Unit,
    onKindChange: (String) -> Unit,
    onLimitChange: (Int) -> Unit,
    onRefresh: () -> Unit,
    onOpenItem: (HistoryItem) -> Unit
) {
    Column(
        modifier = modifier.padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(status, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.secondary)
            OutlinedButton(onClick = onRefresh) { Text("Tải lại") }
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Tìm kiếm") },
            singleLine = true
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onKindChange("") }, modifier = Modifier.weight(1f)) {
                Text(if (kind.isBlank()) "✓ Tất cả" else "Tất cả")
            }
            OutlinedButton(onClick = { onKindChange("text") }, modifier = Modifier.weight(1f)) {
                Text(if (kind == "text") "✓ Văn bản" else "Văn bản")
            }
            OutlinedButton(onClick = { onKindChange("image") }, modifier = Modifier.weight(1f)) {
                Text(if (kind == "image") "✓ Ảnh" else "Ảnh")
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(20, 80, 150).forEach { value ->
                OutlinedButton(onClick = { onLimitChange(value) }, modifier = Modifier.weight(1f)) {
                    Text(if (limit == value) "✓ $value" else "$value")
                }
            }
        }
        items.forEach { item ->
            Card(
                onClick = { onOpenItem(item) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.preview, maxLines = 3)
                    Text(
                        "${if (item.kind == "image") "Ảnh" else "Văn bản"} • ${item.fromName} • ${formatTime(item.createdAt)} • ${
                            if (item.kind == "image") "${item.length / 1024} KB" else "${item.length} ký tự"
                        }",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun HistoryScreenPreview() {
    LANCopyPasteTheme {
        HistoryContent(
            modifier = Modifier,
            status = "3 mục gần nhất",
            items = listOf(
                HistoryItem("1", "text", "Bản xem trước lịch sử clipboard", "Android App", 0, "", 120),
                HistoryItem("2", "image", "Ảnh image/png - 244 KB", "Web", 0, "image/png", 244000),
                HistoryItem("3", "text", "Một đoạn văn bản khác", "DESKTOP Auto", 0, "", 42)
            ),
            query = "",
            kind = "",
            limit = 80,
            onQueryChange = {},
            onKindChange = {},
            onLimitChange = {},
            onRefresh = {},
            onOpenItem = {}
        )
    }
}
