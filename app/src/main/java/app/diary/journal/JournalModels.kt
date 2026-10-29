package app.diary.journal

import app.diary.domain.DayClock
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime

/** 日记本默认地址。口令不放在这里，也不打进安装包。 */
object JournalUrls {
    const val DEFAULT_BASE = "http://39.96.68.27:10002"

    fun normalizeBase(raw: String?): String {
        val trimmed = raw?.trim()?.trimEnd('/').orEmpty()
        if (trimmed.isEmpty()) return DEFAULT_BASE
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            throw IllegalArgumentException("地址需要以 http:// 或 https:// 开头")
        }
        if (trimmed.any { it.isWhitespace() }) {
            throw IllegalArgumentException("地址里不能有空格")
        }
        return trimmed
    }

    fun resolve(base: String, path: String): String {
        val trimmed = path.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        return normalizeBase(base) + "/" + trimmed.removePrefix("/")
    }
}

data class JournalEntry(
    val id: Long,
    val ts: String,
    val kind: String,
    val content: String,
    val imagePath: String?,
    val mediaUrl: String?,
    val localFileName: String? = null,
)

data class JournalUtterance(
    val id: Long,
    val ts: String,
    val trigger: String,
    val content: String,
)

data class JournalCard(
    val entryCount: Int,
    val imageCount: Int,
    val mood: Int?,
    val moodLabel: String?,
    val headline: String?,
    val keywords: List<String>,
    val companion: String?,
    val finalized: Boolean,
)

data class JournalMood(
    val tone: String?,
    val line: String?,
    val heard: List<String>,
    val source: String?,
) {
    companion object {
        val quiet = JournalMood(tone = null, line = null, heard = emptyList(), source = null)
    }
}

data class OnboardingQuestion(
    val key: String,
    val text: String,
    val index: Int,
    val total: Int,
)

data class JournalDay(
    val day: String,
    val today: String,
    val card: JournalCard?,
    val entries: List<JournalEntry>,
    val utterances: List<JournalUtterance>,
    val mood: JournalMood,
    val onboardingDone: Boolean,
    val question: OnboardingQuestion?,
    val configured: Boolean,
)

data class CreatedEntry(val id: Long, val day: String)

data class JournalReply(val reply: String, val mood: JournalMood)

sealed class TimelineItem {
    abstract val ts: String

    data class Mine(val entry: JournalEntry) : TimelineItem() {
        override val ts: String = entry.ts
    }

    data class Theirs(val utterance: JournalUtterance) : TimelineItem() {
        override val ts: String = utterance.ts
    }
}

/** 卡片上的陪伴句，和接口里的 mood.line。空的不显示；两句一样只留一句。 */
fun companionLines(cardCompanion: String?, moodLine: String?): List<String> {
    val card = cardCompanion?.trim().orEmpty()
    val mood = moodLine?.trim().orEmpty()
    return buildList {
        if (card.isNotEmpty()) add(card)
        if (mood.isNotEmpty() && mood != card) add(mood)
    }
}

fun JournalDay.companionLines(): List<String> = companionLines(card?.companion, mood.line)

fun JournalDay.hasEmotionCard(): Boolean {
    if (companionLines().isNotEmpty()) return true
    val current = card ?: return false
    if (current.mood != null) return true
    if (!current.moodLabel.isNullOrBlank()) return true
    if (!current.headline.isNullOrBlank()) return true
    if (current.keywords.isNotEmpty()) return true
    return current.entryCount > 0 || current.imageCount > 0
}

fun JournalDay.timeline(): List<TimelineItem> {
    val items = ArrayList<TimelineItem>(entries.size + utterances.size)
    entries.forEach { items.add(TimelineItem.Mine(it)) }
    utterances.forEach { items.add(TimelineItem.Theirs(it)) }
    items.sortWith(compareBy<TimelineItem> { it.ts }.thenBy { if (it is TimelineItem.Mine) 0 else 1 })
    return items
}

fun journalMillis(ts: String): Long? = runCatching {
    LocalDateTime.parse(ts).atZone(DayClock.zone).toInstant().toEpochMilli()
}.getOrNull()

fun journalPhotoName(remoteId: Long, imagePath: String?): String {
    val ext = imagePath?.substringAfterLast('.', "jpg")?.lowercase().orEmpty()
    val safe = when (ext) {
        "jpeg", "jpg" -> "jpg"
        "png", "gif", "webp" -> ext
        else -> "jpg"
    }
    return "jr-$remoteId.$safe"
}

fun imageDataUrl(bytes: ByteArray): String {
    if (bytes.isEmpty()) throw JournalException("收到了一张空图片。")
    val mime = when {
        bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> "image/jpeg"
        bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes.copyOfRange(1, 4).contentEquals("PNG".toByteArray()) -> "image/png"
        bytes.size >= 6 && (
            bytes.copyOfRange(0, 6).contentEquals("GIF87a".toByteArray()) ||
                bytes.copyOfRange(0, 6).contentEquals("GIF89a".toByteArray())
            ) -> "image/gif"
        bytes.size >= 12 &&
            bytes.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) &&
            bytes.copyOfRange(8, 12).contentEquals("WEBP".toByteArray()) -> "image/webp"
        else -> throw JournalException("这个格式我认不出来，请发 jpg / png / gif / webp 图片。")
    }
    val encoded = java.util.Base64.getEncoder().encodeToString(bytes)
    return "data:$mime;base64,$encoded"
}

fun journalErrorMessage(code: Int, raw: String): String {
    val detail = detailText(raw)
    return when (code) {
        401 -> "口令不对。请在设置里重新连接日记本。"
        429 -> detail.ifBlank { "试得太频繁，过一会儿再来。" }
        else -> detail.ifBlank { "日记本返回 $code" }
    }
}

private fun detailText(raw: String): String {
    val json = runCatching { JSONObject(raw) }.getOrNull() ?: return ""
    if (!json.has("detail") || json.isNull("detail")) return ""
    return when (val detail = json.get("detail")) {
        is String -> detail.trim()
        is JSONArray -> buildString {
            for (index in 0 until detail.length()) {
                val item = detail.opt(index)
                val piece = when (item) {
                    is String -> item
                    is JSONObject -> item.optString("msg")
                    else -> ""
                }.trim()
                if (piece.isNotEmpty()) {
                    if (isNotEmpty()) append(' ')
                    append(piece)
                }
            }
        }.trim()
        else -> ""
    }
}

fun parseJournalDay(raw: String): JournalDay {
    val json = JSONObject(raw)
    val card = if (!json.has("card") || json.isNull("card")) null else parseCard(json.getJSONObject("card"))
    val mood = if (!json.has("mood") || json.isNull("mood")) JournalMood.quiet else parseMood(json.getJSONObject("mood"))
    val onboarding = if (json.has("onboarding") && !json.isNull("onboarding")) json.getJSONObject("onboarding") else null
    val question = onboarding?.let { block ->
        if (!block.has("question") || block.isNull("question")) null else parseQuestion(block.getJSONObject("question"))
    }
    return JournalDay(
        day = json.optString("day"),
        today = json.optString("today"),
        card = card,
        entries = parseEntries(json.optJSONArray("entries")),
        utterances = parseUtterances(json.optJSONArray("utterances")),
        mood = mood,
        onboardingDone = onboarding?.optBoolean("done", true) ?: true,
        question = question,
        configured = if (json.has("configured") && !json.isNull("configured")) json.optBoolean("configured") else true,
    )
}

fun parseCreatedEntry(raw: String): CreatedEntry {
    val json = JSONObject(raw)
    val day = json.optString("day")
    if (!DayClock.isDayKey(day)) throw JournalException("日记本没有返回日期")
    return CreatedEntry(id = json.optLong("id"), day = day)
}

fun parseJournalReply(raw: String): JournalReply {
    val json = JSONObject(raw)
    val reply = nullableString(json, "reply").orEmpty()
    if (reply.isBlank()) throw JournalException("它没有说出话来")
    val mood = if (!json.has("mood") || json.isNull("mood")) JournalMood.quiet else parseMood(json.getJSONObject("mood"))
    return JournalReply(reply = reply.trim(), mood = mood)
}

fun parseOnboardingQuestion(raw: String): OnboardingQuestion? {
    val json = JSONObject(raw)
    val done = json.optBoolean("done", false)
    if (done || !json.has("question") || json.isNull("question")) return null
    return parseQuestion(json.getJSONObject("question"))
}

private fun parseCard(json: JSONObject): JournalCard = JournalCard(
    entryCount = json.optInt("entry_count"),
    imageCount = json.optInt("image_count"),
    mood = nullableInt(json, "mood"),
    moodLabel = nullableString(json, "mood_label"),
    headline = nullableString(json, "headline"),
    keywords = parseStrings(json.optJSONArray("keywords")),
    companion = nullableString(json, "companion"),
    finalized = json.optInt("finalized") == 1 || json.optBoolean("finalized", false),
)

private fun parseMood(json: JSONObject): JournalMood = JournalMood(
    tone = nullableString(json, "tone"),
    line = nullableString(json, "line"),
    heard = parseStrings(json.optJSONArray("heard")),
    source = nullableString(json, "source"),
)

private fun parseQuestion(json: JSONObject): OnboardingQuestion = OnboardingQuestion(
    key = json.optString("key"),
    text = nullableString(json, "text").orEmpty(),
    index = json.optInt("index"),
    total = json.optInt("total"),
)

private fun parseEntries(array: JSONArray?): List<JournalEntry> {
    if (array == null) return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val json = array.optJSONObject(index) ?: continue
            add(
                JournalEntry(
                    id = json.optLong("id"),
                    ts = json.optString("ts"),
                    kind = json.optString("kind"),
                    content = nullableString(json, "content").orEmpty(),
                    imagePath = nullableString(json, "image_path"),
                    mediaUrl = nullableString(json, "media_url"),
                ),
            )
        }
    }
}

private fun parseUtterances(array: JSONArray?): List<JournalUtterance> {
    if (array == null) return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val json = array.optJSONObject(index) ?: continue
            val content = nullableString(json, "content").orEmpty()
            if (content.isBlank()) continue
            add(
                JournalUtterance(
                    id = json.optLong("id"),
                    ts = json.optString("ts"),
                    trigger = nullableString(json, "trigger").orEmpty(),
                    content = content,
                ),
            )
        }
    }
}

private fun parseStrings(array: JSONArray?): List<String> {
    if (array == null) return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            if (array.isNull(index)) continue
            val text = array.optString(index).trim()
            if (text.isNotEmpty() && text != "null") add(text)
        }
    }
}

private fun nullableString(json: JSONObject, key: String): String? {
    if (!json.has(key) || json.isNull(key)) return null
    val text = json.optString(key).trim()
    if (text.isEmpty() || text == "null") return null
    return text
}

private fun nullableInt(json: JSONObject, key: String): Int? {
    if (!json.has(key) || json.isNull(key)) return null
    return json.optInt(key)
}

class JournalException(message: String, val code: Int? = null) : Exception(message)

/** 一次上传还要发哪些部分。已经有服务器 id 的文字或照片不再发。 */
data class UploadParts(
    val sendText: Boolean,
    val pendingPhotoIds: List<Long>,
) {
    val finished: Boolean get() = !sendText && pendingPhotoIds.isEmpty()
}

fun uploadParts(body: String, textRemoteId: Long?, photos: List<Pair<Long, Long?>>): UploadParts =
    UploadParts(
        sendText = body.isNotBlank() && textRemoteId == null,
        pendingPhotoIds = photos.mapNotNull { (id, remoteId) -> if (remoteId == null) id else null },
    )

/**
 * 这一天列表里要留下的本地行。
 * 连上日记本时，藏起「已经记到这一天」的副本，避免和服务器时间线叠两遍。
 * 没连上时，留下手机上的原文，并补上还没有原文对应的服务器缓存。
 */
data class LocalRow(
    val key: Long,
    val cached: Boolean,
    val synced: Boolean,
    val syncedServerDay: String?,
    val serverIds: Set<Long>,
)

fun visibleLocalKeys(rows: List<LocalRow>, serverPageLoaded: Boolean, viewingDay: String): List<Long> {
    val originals = rows.filter { !it.cached }
    if (serverPageLoaded) {
        return originals
            .filter { !(it.synced && it.syncedServerDay == viewingDay) }
            .map { it.key }
    }
    val covered = originals.flatMap { it.serverIds }.toSet()
    val mirrors = rows.filter { row -> row.cached && row.serverIds.none { it in covered } }
    return (originals + mirrors).map { it.key }
}
