package app.diary.domain

import java.security.MessageDigest

data class SummarySourceEntry(
    val body: String,
    val captions: List<String>,
)

const val SUMMARY_SYSTEM_PROMPT: String =
    "你是一位克制、温和的日记整理者。根据用户当天写下的文字和图片说明，用中文写一段当日摘要。" +
        "保留具体的人、地点和事件。不要评判，不要鸡汤，不要编造原文里没有的内容。" +
        "如果内容很少，就写短一些；内容较多时控制在 220 字以内。" +
        "不要使用标题、列表或 Markdown。"

fun summaryMaterialExists(entries: List<SummarySourceEntry>): Boolean =
    entries.any { entry ->
        entry.body.isNotBlank() || entry.captions.any { it.isNotBlank() }
    }

fun buildSummaryPrompt(dayKey: String, entries: List<SummarySourceEntry>): String = buildString {
    append("日期：").append(dayKey).append("（Asia/Shanghai）\n")
    append("下面是这一天的日记。图片文件没有上传，只有用户写的图片说明。\n\n")
    entries.forEachIndexed { index, entry ->
        append("第 ").append(index + 1).append(" 则\n")
        val body = entry.body.trim()
        append("正文：")
        append(if (body.isEmpty()) "（没有文字）" else body)
        append('\n')
        val captions = entry.captions.map { it.trim() }.filter { it.isNotEmpty() }
        if (captions.isNotEmpty()) {
            append("图片说明：").append(captions.joinToString("；")).append('\n')
        }
        append('\n')
    }
}.take(12_000)

fun diarySourceHash(entries: List<SummarySourceEntry>): String {
    val raw = entries.joinToString("\u001e") { entry ->
        entry.body + "\u001f" + entry.captions.joinToString("\u001f")
    }
    val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { byte -> "%02x".format(byte) }
}
