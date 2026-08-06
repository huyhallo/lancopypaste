package com.local.lancopypaste

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private const val MAX_LOG_BYTES = 256 * 1024
    private val stampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val fileStampFormat = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)

    fun log(context: Context, message: String, error: Throwable? = null) {
        runCatching {
            val line = buildString {
                append(stampFormat.format(Date()))
                append("  ")
                append(message)
                if (error != null) {
                    append(" | ")
                    append(error::class.java.simpleName)
                    append(": ")
                    append(error.message.orEmpty())
                }
                append('\n')
            }
            val file = logFile(context)
            file.parentFile?.mkdirs()
            file.appendText(line)
            trimIfNeeded(file)
        }
    }

    fun read(context: Context): String {
        return runCatching {
            val file = logFile(context)
            if (file.exists()) file.readText() else "Chưa có log."
        }.getOrElse { "Không đọc được log: ${it.message}" }
    }

    fun clear(context: Context) {
        runCatching { logFile(context).writeText("") }
    }

    fun exportLogFile(context: Context): File {
        val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: File(context.filesDir, "exports")
        targetDir.mkdirs()
        val target = File(targetDir, "lan-copypaste-log-${fileStampFormat.format(Date())}.txt")
        target.writeText(read(context))
        log(context, "Đã xuất file log: ${target.absolutePath}")
        return target
    }

    fun shareLog(context: Context) {
        val file = File(context.cacheDir, "lan-copypaste-log.txt")
        file.writeText(read(context))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "LAN CopyPaste log")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Chia sẻ log"))
    }

    private fun logFile(context: Context): File {
        return File(File(context.filesDir, "logs"), "app.log")
    }

    private fun trimIfNeeded(file: File) {
        if (file.length() <= MAX_LOG_BYTES) return
        val text = file.readText()
        file.writeText(text.takeLast(MAX_LOG_BYTES / 2))
    }
}
