package app.diary.journal

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class JournalClient(private val http: OkHttpClient) {
    fun me(base: String, token: String): Boolean {
        val raw = get(JournalUrls.resolve(base, "/api/me"), token)
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: throw JournalException("日记本没有返回可读的结果")
        return json.optBoolean("ok", false)
    }

    fun state(base: String, token: String, day: String): JournalDay {
        val raw = get(JournalUrls.resolve(base, "/api/state?day=$day"), token)
        return parseJournalDay(raw)
    }

    fun createText(base: String, token: String, text: String): CreatedEntry {
        val payload = JSONObject().put("text", text.trim())
        return parseCreatedEntry(post(JournalUrls.resolve(base, "/api/entries"), token, payload, longRead = false))
    }

    fun createImage(base: String, token: String, imageDataUrl: String, caption: String): CreatedEntry {
        val payload = JSONObject().put("image", imageDataUrl)
        val trimmed = caption.trim()
        if (trimmed.isNotEmpty()) payload.put("caption", trimmed)
        return parseCreatedEntry(post(JournalUrls.resolve(base, "/api/entries"), token, payload, longRead = false))
    }

    fun ask(base: String, token: String, text: String): JournalReply {
        val payload = JSONObject().put("text", text.trim())
        return parseJournalReply(post(JournalUrls.resolve(base, "/api/ask"), token, payload, longRead = true))
    }

    fun answerOnboarding(base: String, token: String, key: String, answer: String?, skipped: Boolean) {
        val payload = JSONObject().put("key", key).put("skipped", skipped)
        if (!answer.isNullOrBlank()) payload.put("answer", answer.trim())
        val raw = post(JournalUrls.resolve(base, "/api/onboarding/answer"), token, payload, longRead = false)
        runCatching { JSONObject(raw) }.getOrElse { throw JournalException("访谈没有返回结果") }
    }

    fun media(base: String, token: String, path: String): ByteArray {
        val request = Request.Builder()
            .url(JournalUrls.resolve(base, path))
            .header("Authorization", bearer(token))
            .get()
            .build()
        return execute(request, longRead = false).use { response ->
            val bytes = response.body?.bytes() ?: ByteArray(0)
            if (!response.isSuccessful) {
                val text = runCatching { bytes.toString(Charsets.UTF_8) }.getOrDefault("")
                throw JournalException(journalErrorMessage(response.code, text), response.code)
            }
            if (bytes.isEmpty()) throw JournalException("图片是空的")
            bytes
        }
    }

    private fun get(url: String, token: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", bearer(token))
            .header("Accept", "application/json")
            .get()
            .build()
        return bodyString(request, longRead = false)
    }

    private fun post(url: String, token: String, payload: JSONObject, longRead: Boolean): String {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", bearer(token))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON))
            .build()
        return bodyString(request, longRead)
    }

    private fun bodyString(request: Request, longRead: Boolean): String {
        execute(request, longRead).use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw JournalException(journalErrorMessage(response.code, raw), response.code)
            }
            return raw
        }
    }

    private fun execute(request: Request, longRead: Boolean) = try {
        val client = if (longRead) {
            http.newBuilder()
                .readTimeout(130, TimeUnit.SECONDS)
                .writeTimeout(130, TimeUnit.SECONDS)
                .callTimeout(140, TimeUnit.SECONDS)
                .build()
        } else {
            http
        }
        client.newCall(request).execute()
    } catch (_: SocketTimeoutException) {
        throw JournalException("连接日记本超时")
    } catch (_: UnknownHostException) {
        throw JournalException("没有连上日记本")
    } catch (error: JournalException) {
        throw error
    } catch (_: IOException) {
        throw JournalException("没有连上日记本")
    }

    private fun bearer(token: String): String = "Bearer ${token.trim()}"

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
