package com.local.lancopypaste

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.local.lancopypaste.ui.theme.LANCopyPasteTheme

@Composable
fun HomeScreen(
    modifier: Modifier,
    sendText: String,
    onSendTextChange: (String) -> Unit,
    sendImageUri: String,
    onSendImageUriChange: (String) -> Unit,
    receivedText: String,
    receivedImageDataUrl: String,
    receivedMeta: String,
    sending: Boolean,
    onPaste: () -> Unit,
    onSend: () -> Unit,
    onLoadLatest: () -> Unit,
    onCopyReceived: () -> Unit
) {
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        onSendImageUriChange(uri?.toString().orEmpty())
    }

    Column(
        modifier = modifier.padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        UtilityCard(title = "Gửi") {
            OutlinedTextField(
                value = sendText,
                onValueChange = onSendTextChange,
                modifier = Modifier.fillMaxWidth().height(190.dp),
                label = { Text("Nội dung") },
                placeholder = { Text("Dán hoặc nhập văn bản cần gửi...") }
            )
            if (sendImageUri.isNotBlank()) {
                ImagePreview(uriString = sendImageUri)
                OutlinedButton(onClick = { onSendImageUriChange("") }, modifier = Modifier.fillMaxWidth()) {
                    Text("Bỏ ảnh")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onPaste, modifier = Modifier.weight(1f)) { Text("Dán") }
                OutlinedButton(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.weight(1f)) { Text("Ảnh") }
                Button(enabled = !sending, onClick = onSend, modifier = Modifier.weight(1f)) { Text("Gửi") }
            }
        }

        UtilityCard(title = "Nhận") {
            if (receivedImageDataUrl.isNotBlank()) {
                DataUrlImage(dataUrl = receivedImageDataUrl)
            } else {
                OutlinedTextField(
                    value = receivedText,
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth().height(190.dp),
                    label = { Text("Nội dung mới nhất") },
                    placeholder = { Text("Bấm tải để lấy nội dung mới nhất từ server") },
                    readOnly = true
                )
            }
            Text(receivedMeta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onLoadLatest, modifier = Modifier.weight(1f)) { Text("Tải") }
                Button(
                    enabled = receivedText.isNotBlank() || receivedImageDataUrl.isNotBlank(),
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
fun UtilityCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun ImagePreview(uriString: String) {
    val context = LocalContext.current
    val bitmap = remember(uriString) {
        runCatching {
            context.contentResolver.openInputStream(Uri.parse(uriString))?.use {
                BitmapFactory.decodeStream(it)?.asImageBitmap()
            }
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = "Ảnh đã chọn", modifier = Modifier.fillMaxWidth().height(180.dp))
    }
}

@Composable
private fun DataUrlImage(dataUrl: String) {
    val bitmap = remember(dataUrl) {
        runCatching {
            val bytes = Base64.decode(dataUrl.substringAfter(",", ""), Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = "Ảnh đã nhận", modifier = Modifier.fillMaxWidth().height(220.dp))
    } else {
        Box(modifier = Modifier.fillMaxWidth().height(160.dp).background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun HomeScreenPreview() {
    LANCopyPasteTheme {
        HomeScreen(
            modifier = Modifier,
            sendText = "Nội dung cần gửi",
            onSendTextChange = {},
            sendImageUri = "",
            onSendImageUriChange = {},
            receivedText = "Nội dung mới nhất từ laptop",
            receivedImageDataUrl = "",
            receivedMeta = "Văn bản từ Laptop lúc 21:30",
            sending = false,
            onPaste = {},
            onSend = {},
            onLoadLatest = {},
            onCopyReceived = {}
        )
    }
}
