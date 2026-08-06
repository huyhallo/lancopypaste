package com.local.lancopypaste

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import kotlin.concurrent.thread

class ClipboardShareReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val text = intent.getStringExtra(ClipboardWatcherService.EXTRA_TEXT).orEmpty()
        if (text.isBlank()) return

        thread {
            val result = ClipSender.send(context, text, "Android Clipboard")
            val message = if (result.isSuccess) {
                "Đã gửi clipboard"
            } else {
                "Gửi thất bại: ${result.exceptionOrNull()?.message ?: "không rõ lỗi"}"
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
