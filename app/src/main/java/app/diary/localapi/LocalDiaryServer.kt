package app.diary.localapi

import app.diary.data.DiaryRepository
import app.diary.data.PendingPhoto
import app.diary.domain.DayClock
import app.diary.domain.ExportDay
import app.diary.domain.toJson
import app.diary.settings.AppSettings
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking

/**
 * 可选的本机 JSON 接口，默认关闭。
 * 只绑定 127.0.0.1。这不是 DSH Runtime、Foundry 或 Gateway，应用运行时不依赖 DSH。
 */
class LocalDiaryServer(
    private val repository: DiaryRepository,
    private val settings: AppSettings,
) {
    @Volatile
    var running: Boolean = false
        private set

    private var socket: ServerSocket? = null

    fun start(port: Int = PORT): Boolean {
        if (running) return true
        return try {
            val server = ServerSocket()
            server.reuseAddress = true
            server.bind(InetSocketAddress("127.0.0.1", port))
            socket = server
            running = true
            Thread {
                while (running) {
                    try {
                        val client = server.accept()
                        Thread { handle(client) }.apply {
                            isDaemon = true
                            start()
                        }
                    } catch (_: Exception) {
                        if (!running) break
                    }
                }
            }.apply {
                name = "diary-local-api"
                isDaemon = true
                start()
            }
            true
        } catch (_: Exception) {
            running = false
            false
        }
    }

    fun stop() {
        running = false
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
    }

    private fun handle(socket: Socket) {
        socket.use { client ->
            client.soTimeout = 8_000
            val input = client.getInputStream()
            val headerBytes = ByteArrayOutputStream()
            while (true) {
                val value = input.read()
                if (value == -1) return
                headerBytes.write(value)
                if (headerBytes.size() > 16 * 1024) {
                    writeJson(client, 400, """{"error":"请求头过大"}""")
                    return
                }
                val raw = headerBytes.toByteArray()
                val size = raw.size
                if (
                    size >= 4 &&
                    raw[size - 4] == '\r'.code.toByte() &&
                    raw[size - 3] == '\n'.code.toByte() &&
                    raw[size - 2] == '\r'.code.toByte() &&
                    raw[size - 1] == '\n'.code.toByte()
                ) {
                    break
                }
            }
            val headerText = headerBytes.toString(Charsets.ISO_8859_1)
            val lines = headerText.split("\r\n")
            val request = lines.firstOrNull().orEmpty().split(" ")
            if (request.size < 2) {
                writeJson(client, 400, """{"error":"无法解析请求"}""")
                return
            }
            val method = request[0]
            val path = request[1].substringBefore('?')
            val headers = lines.drop(1).mapNotNull { line ->
                val index = line.indexOf(':')
                if (index <= 0) null else line.substring(0, index).trim().lowercase() to line.substring(index + 1).trim()
            }.toMap()
            val length = headers["content-length"]?.toIntOrNull() ?: 0
            if (length > MAX_BODY) {
                writeJson(client, 413, """{"error":"请求体过大"}""")
                return
            }
            val body = if (length > 0) {
                val buffer = ByteArray(length)
                var offset = 0
                while (offset < length) {
                    val count = input.read(buffer, offset, length - offset)
                    if (count < 0) break
                    offset += count
                }
                String(buffer, 0, offset, Charsets.UTF_8)
            } else {
                ""
            }
            if (!authorized(headers["authorization"].orEmpty())) {
                writeJson(client, 401, """{"error":"需要令牌","hint":"Authorization: Bearer <令牌>"}""")
                return
            }
            try {
                route(client, method, path, body)
            } catch (_: Exception) {
                writeJson(client, 500, """{"error":"服务器内部错误"}""")
            }
        }
    }

    private fun authorized(header: String): Boolean {
        val expected = settings.token()
        val presented = header.trim().let { value ->
            when {
                value.startsWith("Bearer ") -> value.substring(7)
                value.startsWith("bearer ") -> value.substring(7)
                else -> ""
            }
        }.trim()
        if (expected.isBlank() || presented.isBlank()) return false
        return MessageDigest.isEqual(expected.toByteArray(Charsets.UTF_8), presented.toByteArray(Charsets.UTF_8))
    }

    private fun route(client: Socket, method: String, path: String, body: String) {
        when {
            method == "GET" && path == "/v1/health" -> writeJson(
                client,
                200,
                """{"ok":true,"schema":"diary.export.v1","service":"diary-local","dsh":false}""",
            )
            method == "GET" && path == "/v1/export" -> {
                val json = runBlocking { repository.exportDocument(photosIncluded = false).toJson() }
                writeJson(client, 200, json)
            }
            method == "GET" && path == "/v1/days" -> {
                val days = runBlocking { repository.markedDays() }
                val listed = days.joinToString(",") { day -> "\"$day\"" }
                writeJson(client, 200, """{"schema":"diary.export.v1","days":[$listed]}""")
            }
            method == "GET" && path.startsWith("/v1/days/") -> {
                val day = path.removePrefix("/v1/days/")
                if (!DayClock.isDayKey(day)) {
                    writeJson(client, 400, """{"error":"日期格式应为 yyyy-MM-dd"}""")
                    return
                }
                val exportDay = runBlocking { repository.exportDay(day) }
                writeJson(client, 200, wrapDay(exportDay))
            }
            method == "POST" && path == "/v1/entries" -> postEntry(client, body)
            else -> writeJson(client, 404, """{"error":"没有这个接口"}""")
        }
    }

    private fun postEntry(client: Socket, body: String) {
        val json = try {
            JSONObject(body)
        } catch (_: Exception) {
            writeJson(client, 400, """{"error":"需要 JSON 正文"}""")
            return
        }
        if (json.has("photos") || json.has("captions")) {
            writeJson(client, 400, """{"error":"本机写入只接受文字。图片请在应用里添加。"}""")
            return
        }
        val text = json.optString("body").trim()
        if (text.isEmpty()) {
            writeJson(client, 400, """{"error":"正文是空的"}""")
            return
        }
        if (text.length > 20_000) {
            writeJson(client, 400, """{"error":"正文太长"}""")
            return
        }
        val day = json.optString("day").trim().ifEmpty { DayClock.dayKey() }
        if (!DayClock.isDayKey(day)) {
            writeJson(client, 400, """{"error":"日期格式应为 yyyy-MM-dd"}""")
            return
        }
        val id = runBlocking {
            repository.addEntry(day, text, emptyList<PendingPhoto>())
        }
        writeJson(client, 201, """{"id":$id,"day":"$day"}""")
    }

    private fun wrapDay(day: ExportDay): String = buildString {
        append("{\n")
        append("  \"schema\": \"diary.export.v1\",\n")
        append("  \"timezone\": \"Asia/Shanghai\",\n")
        append("  \"photosIncluded\": false,\n")
        append("  \"day\": ")
        append(day.toJson("  ").trimStart())
        append("\n}\n")
    }

    private fun writeJson(socket: Socket, code: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val reason = when (code) {
            200 -> "OK"
            201 -> "Created"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            404 -> "Not Found"
            413 -> "Payload Too Large"
            else -> "Error"
        }
        val head = "HTTP/1.1 $code $reason\r\n" +
            "Content-Type: application/json; charset=utf-8\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Connection: close\r\n\r\n"
        val output = socket.getOutputStream()
        output.write(head.toByteArray(Charsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    companion object {
        const val PORT = 18765
        private const val MAX_BODY = 256 * 1024
    }
}
