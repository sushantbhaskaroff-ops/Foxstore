package com.example.web

import android.content.Context
import android.util.Log
import com.example.data.AppSubmissionInput
import com.example.data.StoreRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID

class LocalWebPortalServer(
    private val context: Context,
    private val repository: StoreRepository,
    private val userId: String,
    private val developerName: String,
    private val onAppUploadedFromWeb: (String) -> Unit,
) {
    companion object {
        const val PORT = 8765
        const val URL = "http://127.0.0.1:$PORT"
        private const val TAG = "LocalWebPortal"
    }

    private val sessionToken = UUID.randomUUID().toString()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    fun start() {
        if (acceptJob?.isActive == true) return
        acceptJob = scope.launch {
            try {
                val loopback = InetAddress.getByName("127.0.0.1")
                val socket = ServerSocket(PORT, 16, loopback)
                serverSocket = socket
                Log.i(TAG, "Developer Upload Website running at $URL")
                while (isActive && !socket.isClosed) {
                    val client = try {
                        socket.accept()
                    } catch (_: Exception) {
                        break
                    }
                    launch { handleClient(client) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Local server failed to bind port $PORT: ${e.message}")
            }
        }
    }

    fun stop() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        acceptJob?.cancel()
        scope.cancel()
    }

    private fun handleClient(client: Socket) {
        client.use { socket ->
            socket.soTimeout = 5000
            val input = BufferedInputStream(socket.getInputStream())
            val out = socket.getOutputStream()

            val headerBytes = readUntilHeadersEnd(input) ?: return
            val headerText = headerBytes.toString(Charsets.UTF_8)
            val lines = headerText.split("\r\n")
            if (lines.isEmpty()) return
            val requestLine = lines[0].split(" ")
            if (requestLine.size < 2) return
            val method = requestLine[0].uppercase()
            val path = requestLine[1].substringBefore("?")

            val contentLength = lines.drop(1)
                .firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
                ?.substringAfter(":")
                ?.trim()
                ?.toIntOrNull()
                ?.coerceIn(0, 64_000)
                ?: 0

            val bodyBytes = if (contentLength > 0) {
                ByteArray(contentLength).also { buf ->
                    var read = 0
                    while (read < contentLength) {
                        val n = input.read(buf, read, contentLength - read)
                        if (n <= 0) break
                        read += n
                    }
                }
            } else ByteArray(0)

            when {
                method == "GET" && (path == "/" || path == "/index.html") -> {
                    val html = context.assets.open("developer_portal/index.html")
                        .bufferedReader()
                        .use { it.readText() }
                    writeResponse(out, 200, "text/html; charset=utf-8", html.toByteArray(Charsets.UTF_8))
                }

                method == "GET" && path == "/api/session" -> {
                    val json = JSONObject().apply {
                        put("userId", userId)
                        put("developerName", developerName)
                        put("token", sessionToken)
                        put("url", URL)
                    }.toString()
                    writeResponse(out, 200, "application/json; charset=utf-8", json.toByteArray(Charsets.UTF_8))
                }

                method == "GET" && path == "/api/my-apps" -> {
                    val apps = runBlocking { repository.fetchMySubmissionsSnapshot(userId) }
                    val arr = JSONArray()
                    apps.forEach { app ->
                        arr.put(JSONObject().apply {
                            put("id", app.id)
                            put("name", app.name)
                            put("packageName", app.packageName)
                            put("versionName", app.versionName)
                            put("tagline", app.tagline)
                            put("status", app.status)
                            put("reviewNote", app.reviewNote)
                        })
                    }
                    val resp = JSONObject().put("apps", arr).toString()
                    writeResponse(out, 200, "application/json; charset=utf-8", resp.toByteArray(Charsets.UTF_8))
                }

                method == "POST" && path == "/api/submit" -> {
                    val bodyStr = bodyBytes.toString(Charsets.UTF_8)
                    val responseJson = handleSubmitJson(bodyStr)
                    val ok = responseJson.optBoolean("ok", false)
                    writeResponse(
                        out,
                        if (ok) 200 else 400,
                        "application/json; charset=utf-8",
                        responseJson.toString().toByteArray(Charsets.UTF_8)
                    )
                }

                else -> {
                    writeResponse(out, 404, "text/plain; charset=utf-8", "Not Found".toByteArray())
                }
            }
        }
    }

    private fun handleSubmitJson(rawJson: String): JSONObject {
        return try {
            val json = JSONObject(rawJson)
            if (json.optString("token") != sessionToken) {
                return JSONObject().put("ok", false).put("error", "Session token mismatch. Please refresh.")
            }
            val input = AppSubmissionInput(
                name = json.optString("name"),
                packageName = json.optString("packageName"),
                developerName = json.optString("developerName", developerName),
                tagline = json.optString("tagline"),
                description = json.optString("description"),
                releaseNotes = json.optString("releaseNotes"),
                category = json.optString("category", "Productivity"),
                versionName = json.optString("versionName", "1.0.0"),
                sizeLabel = json.optString("sizeLabel", "12.0 MB"),
                iconKey = json.optString("iconKey", "rocket"),
                accentHex = json.optString("accentHex", "#10B981"),
                source = "WEBSITE",
            )
            val result = runBlocking { repository.submitApp(input) }
            result.fold(
                onSuccess = { appId ->
                    onAppUploadedFromWeb(input.name)
                    JSONObject().put("ok", true).put("id", appId)
                },
                onFailure = { err ->
                    JSONObject().put("ok", false).put("error", err.message ?: "Failed to upload app")
                }
            )
        } catch (e: Exception) {
            JSONObject().put("ok", false).put("error", e.message ?: "Invalid upload payload")
        }
    }

    private fun readUntilHeadersEnd(input: BufferedInputStream): ByteArray? {
        val buffer = ByteArrayOutputStream()
        var state = 0
        while (buffer.size() < 16_384) {
            val b = input.read()
            if (b == -1) return null
            buffer.write(b)
            state = when (state) {
                0 -> if (b == '\r'.code) 1 else 0
                1 -> if (b == '\n'.code) 2 else if (b == '\r'.code) 1 else 0
                2 -> if (b == '\r'.code) 3 else 0
                3 -> if (b == '\n'.code) return buffer.toByteArray() else 0
                else -> 0
            }
        }
        return buffer.toByteArray()
    }

    private fun writeResponse(out: OutputStream, status: Int, contentType: String, body: ByteArray) {
        val statusText = when (status) {
            200 -> "OK"
            400 -> "Bad Request"
            404 -> "Not Found"
            else -> "Error"
        }
        val headers = buildString {
            append("HTTP/1.1 $status $statusText\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${body.size}\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(headers.toByteArray(Charsets.UTF_8))
        out.write(body)
        out.flush()
    }
}
