package com.local.lancopypaste

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL

data class ReceivedClip(
    val id: String,
    val kind: String,
    val text: String,
    val dataUrl: String,
    val mimeType: String,
    val fromName: String,
    val createdAt: Long
)

data class HistoryItem(
    val id: String,
    val kind: String,
    val preview: String,
    val fromName: String,
    val createdAt: Long,
    val mimeType: String,
    val length: Int
)

object ClipSender {
    private const val TAG = "LANCopyPaste"
    private const val PREFS = "lan_copypaste"
    private const val SERVER_URL = "server_url"
    private const val DEVICE_TOKEN = "device_token"
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
            .commit()
    }

    fun getDeviceToken(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(DEVICE_TOKEN, "")
            .orEmpty()
    }

    fun saveDeviceToken(context: Context, token: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(DEVICE_TOKEN, token)
            .commit()
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
            AppLogger.log(context, "Gửi văn bản tới $endpoint (${cleanText.length} ký tự)")
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
            addAuthHeader(context, connection)

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(body)
            }

            val code = connection.responseCode
            connection.disconnect()
            if (code !in 200..299) {
                AppLogger.log(context, "Gửi văn bản thất bại HTTP $code")
                error("Server returned HTTP $code")
            }
            AppLogger.log(context, "Gửi văn bản thành công")
            Log.d(TAG, "Clip sent successfully")
            Unit
        }.onFailure {
            AppLogger.log(context, "Gửi văn bản lỗi", it)
        }
    }

    fun sendImage(context: Context, uriString: String, fromName: String = "Android Image"): Result<Unit> {
        return runCatching {
            val uri = Uri.parse(uriString)
            val mimeType = context.contentResolver.getType(uri) ?: "image/png"
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Cannot read image")
            val dataUrl = "data:$mimeType;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
            val endpoint = URL("${getServerUrl(context)}/api/clip/image")
            AppLogger.log(context, "Gửi ảnh tới $endpoint (${bytes.size} bytes, $mimeType)")
            val body = JSONObject()
                .put("dataUrl", dataUrl)
                .put("mimeType", mimeType)
                .put("fromName", fromName)
                .toString()

            val connection = endpoint.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            addAuthHeader(context, connection)

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(body)
            }

            val code = connection.responseCode
            connection.disconnect()
            if (code !in 200..299) {
                AppLogger.log(context, "Gửi ảnh thất bại HTTP $code")
                error("Server returned HTTP $code")
            }
            AppLogger.log(context, "Gửi ảnh thành công")
            Log.d(TAG, "Image sent successfully (${bytes.size} bytes)")
            Unit
        }.onFailure {
            AppLogger.log(context, "Gửi ảnh lỗi", it)
        }
    }

    fun latest(context: Context): Result<ReceivedClip?> {
        return runCatching {
            val endpoint = URL("${getServerUrl(context)}/api/latest")
            AppLogger.log(context, "Tải nội dung mới nhất từ $endpoint")
            Log.d(TAG, "Loading latest clip from $endpoint")
            val connection = endpoint.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            addAuthHeader(context, connection)
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val code = connection.responseCode
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            connection.disconnect()

            if (code !in 200..299) {
                AppLogger.log(context, "Tải nội dung mới nhất thất bại HTTP $code")
                error("Server returned HTTP $code")
            }

            val clip = JSONObject(body).optJSONObject("clip") ?: return@runCatching null
            ReceivedClip(
                id = clip.optString("id"),
                kind = clip.optString("kind", "text"),
                text = clip.optString("text"),
                dataUrl = clip.optString("dataUrl"),
                mimeType = clip.optString("mimeType"),
                fromName = clip.optString("fromName"),
                createdAt = clip.optLong("createdAt")
            )
        }.onFailure {
            AppLogger.log(context, "Tải nội dung mới nhất lỗi", it)
        }
    }

    fun history(context: Context, limit: Int = 50, query: String = "", kind: String = ""): Result<List<HistoryItem>> {
        return runCatching {
            val params = buildString {
                append("limit=$limit")
                if (query.isNotBlank()) append("&q=${java.net.URLEncoder.encode(query, "UTF-8")}")
                if (kind.isNotBlank()) append("&kind=${java.net.URLEncoder.encode(kind, "UTF-8")}")
            }
            val endpoint = URL("${getServerUrl(context)}/api/history?$params")
            AppLogger.log(context, "Tải lịch sử từ $endpoint")
            val connection = endpoint.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            addAuthHeader(context, connection)
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val code = connection.responseCode
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            connection.disconnect()

            if (code !in 200..299) {
                AppLogger.log(context, "Tải lịch sử thất bại HTTP $code")
                error("Server returned HTTP $code")
            }

            val items = JSONObject(body).optJSONArray("items") ?: return@runCatching emptyList()
            buildList {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    add(
                        HistoryItem(
                            id = item.optString("id"),
                            kind = item.optString("kind", "text"),
                            preview = item.optString("preview"),
                            fromName = item.optString("fromName"),
                            createdAt = item.optLong("createdAt"),
                            mimeType = item.optString("mimeType"),
                            length = item.optInt("length")
                        )
                    )
                }
            }
        }.onFailure {
            AppLogger.log(context, "Tải lịch sử lỗi", it)
        }
    }

    fun historyItem(context: Context, id: String): Result<ReceivedClip> {
        return runCatching {
            val endpoint = URL("${getServerUrl(context)}/api/history/$id")
            AppLogger.log(context, "Tải mục lịch sử $id từ $endpoint")
            val connection = endpoint.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            addAuthHeader(context, connection)
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val code = connection.responseCode
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            connection.disconnect()

            if (code !in 200..299) {
                AppLogger.log(context, "Tải mục lịch sử thất bại HTTP $code")
                error("Server returned HTTP $code")
            }

            val item = JSONObject(body)
            ReceivedClip(
                id = item.optString("id"),
                kind = item.optString("kind", "text"),
                text = item.optString("text"),
                dataUrl = item.optString("dataUrl"),
                mimeType = item.optString("mimeType"),
                fromName = item.optString("fromName"),
                createdAt = item.optLong("createdAt")
            )
        }.onFailure {
            AppLogger.log(context, "Tải mục lịch sử lỗi", it)
        }
    }

    fun pair(context: Context, code: String, deviceName: String = "Android", serverUrl: String? = null): Result<Unit> {
        return runCatching {
            val targetServer = normalizeUrl(serverUrl ?: getServerUrl(context))
            val endpoint = URL("$targetServer/api/pair")
            AppLogger.log(context, "Ghép nối tới $endpoint bằng mã ${code.trim().take(2)}****")
            Log.d(TAG, "Pairing device with $endpoint")
            val connection = endpoint.openConnection() as HttpURLConnection
            val body = JSONObject()
                .put("code", code.trim())
                .put("deviceName", deviceName)
                .toString()

            connection.requestMethod = "POST"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(body)
            }

            val codeResponse = connection.responseCode
            val bodyResponse = (if (codeResponse in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            connection.disconnect()
            if (codeResponse !in 200..299) {
                Log.e(TAG, "Pairing failed: HTTP $codeResponse $bodyResponse")
                AppLogger.log(context, "Ghép nối thất bại HTTP $codeResponse $bodyResponse")
                error("Server returned HTTP $codeResponse")
            }

            saveDeviceToken(context, JSONObject(bodyResponse).getString("token"))
            saveServerUrl(context, targetServer)
            AppLogger.log(context, "Ghép nối thành công với $targetServer")
            Log.d(TAG, "Pairing succeeded with $targetServer")
            Unit
        }.onFailure {
            AppLogger.log(context, "Ghép nối lỗi", it)
        }
    }

    fun updateDeviceStatus(context: Context, online: Boolean, source: String = "android-app"): Result<Unit> {
        return runCatching {
            val endpoint = URL("${getServerUrl(context)}/api/device/status")
            AppLogger.log(context, if (online) "Kiểm tra kết nối với máy chủ" else "Ngắt kết nối với máy chủ")
            val connection = endpoint.openConnection() as HttpURLConnection
            val body = JSONObject()
                .put("online", online)
                .put("source", source)
                .toString()

            connection.requestMethod = "POST"
            connection.connectTimeout = 4000
            connection.readTimeout = 4000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            addAuthHeader(context, connection)

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(body)
            }

            val code = connection.responseCode
            val bodyResponse = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            connection.disconnect()

            if (code !in 200..299) {
                AppLogger.log(context, "Cập nhật kết nối thất bại HTTP $code $bodyResponse")
                error("Server returned HTTP $code")
            }
            Unit
        }.onFailure {
            AppLogger.log(context, "Cập nhật kết nối lỗi", it)
        }
    }

    fun discoverServer(context: Context? = null): Result<String> {
        return runCatching {
            val address = NetworkInterface.getNetworkInterfaces().toList()
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .map { it.hostAddress }
                .firstOrNull { it.startsWith("192.168.") || it.startsWith("10.") || it.startsWith("172.") }
                ?: error("No LAN address")

            val prefix = address.substringBeforeLast(".")
            context?.let { AppLogger.log(it, "Bắt đầu tự tìm máy chủ trong dải $prefix.0/24") }
            for (host in 1..254) {
                val url = "http://$prefix.$host:3000"
                try {
                    val connection = URL("$url/api/discovery").openConnection() as HttpURLConnection
                    connection.connectTimeout = 120
                    connection.readTimeout = 120
                    connection.requestMethod = "GET"
                    if (connection.responseCode == 200) {
                        connection.disconnect()
                        context?.let { AppLogger.log(it, "Tìm thấy máy chủ: $url") }
                        return@runCatching url
                    }
                    connection.disconnect()
                } catch (_: Exception) {
                }
            }
            error("Server not found")
        }.onFailure {
            context?.let { ctx -> AppLogger.log(ctx, "Tự tìm máy chủ lỗi", it) }
        }
    }

    private fun addAuthHeader(context: Context, connection: HttpURLConnection) {
        val token = getDeviceToken(context)
        if (token.isNotBlank()) {
            connection.setRequestProperty("x-device-token", token)
        }
    }
}
