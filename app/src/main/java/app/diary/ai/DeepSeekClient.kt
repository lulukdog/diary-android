package app.diary.ai

import app.diary.domain.SUMMARY_SYSTEM_PROMPT
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class DeepSeekException(message: String) : Exception(message)

class DeepSeekClient(private val http: OkHttpClient) {
    fun summarize(apiKey: String, userPrompt: String): String {
        val payload = JSONObject()
            .put("model", MODEL)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", SUMMARY_SYSTEM_PROMPT))
                    .put(JSONObject().put("role", "user").put("content", userPrompt)),
            )
            .put("temperature", 0.3)
        val request = Request.Builder()
            .url(ENDPOINT)
            .header("Authorization", "Bearer ${apiKey.trim()}")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON))
            .build()
        try {
            http.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw DeepSeekException(failureMessage(response.code, raw))
                }
                val content = runCatching {
                    JSONObject(raw)
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                }.getOrNull()?.trim().orEmpty()
                if (content.isEmpty()) throw DeepSeekException("摘要是空的")
                return content
            }
        } catch (error: DeepSeekException) {
            throw error
        } catch (_: SocketTimeoutException) {
            throw DeepSeekException("连接 DeepSeek 超时")
        } catch (_: UnknownHostException) {
            throw DeepSeekException("没有连上网络")
        } catch (_: IOException) {
            throw DeepSeekException("网络中断了")
        }
    }

    private fun failureMessage(code: Int, raw: String): String {
        val message = runCatching {
            JSONObject(raw).getJSONObject("error").optString("message")
        }.getOrNull().orEmpty()
        return when (code) {
            401 -> "DeepSeek 没有接受这个 API Key"
            402 -> "DeepSeek 账户额度不足"
            429 -> "DeepSeek 请求太频繁，过一会儿再试"
            else -> if (message.isNotBlank()) "DeepSeek：$message" else "DeepSeek 返回 $code"
        }
    }

    companion object {
        const val ENDPOINT = "https://api.deepseek.com/chat/completions"
        const val MODEL = "deepseek-chat"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
