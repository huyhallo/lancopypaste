package com.local.lancopypaste

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class ReceivedClip(
    val id: String,
    val text: String,
    val fromName: String,
    val createdAt: Long
)

data class HistoryItem(
    val id: String,
    val preview: String,
    val fromName: String,
    val createdAt: Long,
    val length: Int
)

object ClipSender {
    private const val TAG = "LANCopyPaste"
    private const val PREFS = "lan_copypaste"
    private const val SERVER_URL = "server_url"
    private const val WATCH_CLIPBOARD = "watch_clipboard"
    private const val DEFAULT_SERVER = "http://192.168.1.101:3000"

    fun getServerUrl(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(SERVER_URL, DEFAULT_SERVER)
            ?: DEFAULT_SERVER
    }

    fun saveServerUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(SERVER_URL, normalizeUrl(url))
            .apply()
    }

    fun isClipboardWatcherEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(WATCH_CLIPBOARD, false)
    }

    fun saveClipboardWatcherEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(WATCH_CLIPBOARD, enabled)
            .apply()
    }

    fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isBlank()) return DEFAULT_SERVER
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }
    }

    fun send(context: Context, text: String, fromName: String = "Android"): Result<Unit> {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return Result.failure(IllegalArgumentException("No text to send"))

        return runCatching {
            val endpoint = URL("${getServerUrl(context)}/api/clip")
            Log.d(TAG, "Sending clip to $endpoint from $fromName (${cleanText.length} chars)")
            val connection = endpoint.openConnection() as HttpURLConnection
            val body = JSONObject()
                .put("text", cleanText)
                .put("fromName", fromName)
                .toString()

            connection.requestMethod = "POST"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(body)
            }

            val code = connection.responseCode
            connection.disconnect()
            if (code !in 200..299) {
                error("Server returned HTTP $code")
            }
            Log.d(TAG, "Clip sent successfully")
        }
    }

    fun latest(context: Context): Result<ReceivedClip?> {
        return runCatching {
            val endpoint = URL("${getServerUrl(context)}/api/latest")
            Log.d(TAG, "Loading latest clip from $endpoint")
            val connection = endpoint.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val code = connection.responseCode
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            connection.disconnect()

            if (code !in 200..299) {
                error("Server returned HTTP $code")
            }

            val clip = JSONObject(body).optJSONObject("clip") ?: return@runCatching null
            ReceivedClip(
                id = clip.optString("id"),
                text = clip.optString("text"),
                fromName = clip.optString("fromName"),
                createdAt = clip.optLong("createdAt")
            )
        }
    }

    fun history(context: Context, limit: Int = 50): Result<List<HistoryItem>> {
        return runCatching {
            val endpoint = URL("${getServerUrl(context)}/api/history?limit=$limit")
            val connection = endpoint.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val code = connection.responseCode
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            connection.disconnect()

            if (code !in 200..299) {
                error("Server returned HTTP $code")
            }

            val items = JSONObject(body).optJSONArray("items") ?: return@runCatching emptyList()
            buildList {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    add(
                        HistoryItem(
                            id = item.optString("id"),
                            preview = item.optString("preview"),
                            fromName = item.optString("fromName"),
                            createdAt = item.optLong("createdAt"),
                            length = item.optInt("length")
                        )
                    )
                }
            }
        }
    }

    fun historyItem(context: Context, id: String): Result<ReceivedClip> {
        return runCatching {
            val endpoint = URL("${getServerUrl(context)}/api/history/$id")
            val connection = endpoint.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val code = connection.responseCode
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            connection.disconnect()

            if (code !in 200..299) {
                error("Server returned HTTP $code")
            }

            val item = JSONObject(body)
            ReceivedClip(
                id = item.optString("id"),
                text = item.optString("text"),
                fromName = item.optString("fromName"),
                createdAt = item.optLong("createdAt")
            )
        }
    }
}
