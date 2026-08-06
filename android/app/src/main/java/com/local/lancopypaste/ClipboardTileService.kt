package com.local.lancopypaste

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import kotlin.concurrent.thread

class ClipboardTileService : TileService() {
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onStartListening() {
        qsTile?.state = Tile.STATE_ACTIVE
        qsTile?.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val text = readClipboardText()

        if (text.isBlank()) {
            Toast.makeText(this, "Clipboard không có văn bản", Toast.LENGTH_SHORT).show()
            return
        }

        qsTile?.state = Tile.STATE_UNAVAILABLE
        qsTile?.updateTile()

        thread {
            val result = ClipSender.send(this, text, "Android Tile")
            qsTile?.state = Tile.STATE_ACTIVE
            qsTile?.updateTile()

            val message = if (result.isSuccess) {
                "Đã gửi clipboard"
            } else {
                "Gửi thất bại: ${result.exceptionOrNull()?.message ?: "không rõ lỗi"}"
            }

            mainHandler.post {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun readClipboardText(): String {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip ?: return ""
        val description = clip.description ?: return ""

        if (!description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) &&
            !description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)
        ) {
            return ""
        }

        return clip.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
    }
}
