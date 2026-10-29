package app.diary.domain

/**
 * 没有模型或模型失败时，从当天原文里摘 1 到 3 句。
 * 只使用写下的字，不补情绪，也不改写成另一句话。
 */
fun extractiveDayBrief(entries: List<SummarySourceEntry>): String? {
    val pieces = LinkedHashSet<String>()
    for (entry in entries) {
        val body = collapse(entry.body)
        if (body.isNotEmpty()) pieces += body
        for (caption in entry.captions) {
            val text = collapse(caption)
            if (text.isNotEmpty() && text != body) pieces += text
        }
    }
    if (pieces.isEmpty()) return null
    val lines = if (pieces.size == 1) {
        sentences(pieces.first()).take(3)
    } else {
        pieces.map { sentences(it).firstOrNull() ?: it }.take(3)
    }.map { clip(it) }.filter { it.isNotEmpty() }
    if (lines.isEmpty()) return null
    return lines.joinToString("\n")
}

private fun collapse(raw: String): String =
    raw.trim().replace(Regex("[\\t \\u00A0]+"), " ").trim()

private fun sentences(text: String): List<String> {
    val chunks = text.split(Regex("(?<=[。！？])\\s*|\\n+|(?<=[.!?])\\s+"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    return if (chunks.isEmpty()) listOf(text.trim()).filter { it.isNotEmpty() } else chunks
}

private fun clip(text: String): String {
    val trimmed = text.trim()
    if (trimmed.length <= 72) return trimmed
    return trimmed.take(72).trimEnd() + "…"
}
