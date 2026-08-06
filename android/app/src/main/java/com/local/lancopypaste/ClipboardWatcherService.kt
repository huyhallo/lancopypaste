package com.local.lancopypaste

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class ClipboardWatcherService : Service() {
    private lateinit var clipboard: ClipboardManager
    private var lastText = ""

    override fun onCreate() {
        super.onCreate()
        clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        ensureChannel()
        startForeground(WATCH_NOTIFICATION_ID, watcherNotification())
        clipboard.addPrimaryClipChangedListener(listener)
    }

    override fun onDestroy() {
        clipboard.removePrimaryClipChangedListener(listener)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private val listener = ClipboardManager.OnPrimaryClipChangedListener {
        val text = readClipboardText()
        if (text.isBlank() || text == lastText) return@OnPrimaryClipChangedListener
        lastText = text
        showSharePrompt(text)
    }

    private fun readClipboardText(): String {
        val clip = clipboard.primaryClip ?: return ""
        val description = clip.description ?: return ""
        if (!description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) &&
            !description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)
        ) {
            return ""
        }

        return clip.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
    }

    private fun showSharePrompt(text: String) {
        val sendIntent = Intent(this, ClipboardShareReceiver::class.java)
            .putExtra(EXTRA_TEXT, text)
        val sendPendingIntent = PendingIntent.getBroadcast(
            this,
            text.hashCode(),
            sendIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Chia sẻ clipboard?")
            .setContentText(text.take(90))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.take(400)))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(R.mipmap.ic_launcher, "Gửi", sendPendingIntent)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(SHARE_NOTIFICATION_ID, notification)
    }

    private fun watcherNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("LAN CopyPaste đang theo dõi clipboard")
            .setContentText("Khi clipboard đổi, app sẽ hỏi trước khi gửi.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Clipboard sharing",
            NotificationManager.IMPORTANCE_HIGH
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val EXTRA_TEXT = "com.local.lancopypaste.EXTRA_TEXT"
        private const val CHANNEL_ID = "clipboard_sharing"
        private const val WATCH_NOTIFICATION_ID = 1001
        private const val SHARE_NOTIFICATION_ID = 1002
    }
}
