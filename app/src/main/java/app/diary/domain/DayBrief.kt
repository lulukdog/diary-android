package app.diary.domain

import app.diary.journal.companionLines

data class DayBrief(
    val presence: String?,
    val summaryLines: List<String>,
    val keywords: List<String>,
    val mood: Int?,
)

fun dayBrief(
    headline: String?,
    moodLabel: String?,
    mood: Int?,
    keywords: List<String>,
    companion: String?,
    moodLine: String?,
    localSummary: String?,
    entryTexts: List<String>,
    justSaved: String?,
): DayBrief {
    val serverLines = companionLines(companion, moodLine)
    val local = CompanionVoice.lineFor(justSaved.orEmpty())
        ?: entryTexts.asReversed().firstNotNullOfOrNull { CompanionVoice.lineFor(it) }
    val presence = serverLines.firstOrNull() ?: local
    val summary = buildList {
        fun addDistinct(value: String?) {
            val text = value?.trim().orEmpty()
            if (text.isEmpty() || text == presence || text in this) return
            add(text)
        }
        addDistinct(headline)
        val label = moodLabel?.trim().orEmpty()
        if (label.isNotEmpty()) addDistinct("「$label」")
        serverLines.drop(1).forEach { addDistinct(it) }
        addDistinct(localSummary)
    }
    return DayBrief(
        presence = presence,
        summaryLines = summary,
        keywords = keywords.map { it.trim() }.filter { it.isNotEmpty() },
        mood = mood,
    )
}

fun summaryCardVisible(brief: DayBrief): Boolean =
    brief.summaryLines.isNotEmpty() || brief.keywords.isNotEmpty() || brief.mood != null

fun summaryEmptyMessage(
    today: Boolean,
    connected: Boolean,
    finalized: Boolean,
    hasPresence: Boolean,
): String {
    if (hasPresence) {
        return if (today) {
            "今天的小结还没写好。上面那句先留在这儿。"
        } else {
            "这一天的小结还没单独写成一段。上面那句先留在这儿。"
        }
    }
    if (finalized) return "这一天没有留下小结。"
    return when {
        today && connected -> "今天还没有小结。日记本通常在凌晨 4 点后收口，也可以现在生成。"
        today -> "今天还没有小结。连上日记本后，每天的小结会出现在这里。"
        connected -> "这一天还没有小结。同步一下，如果日记本已经收口，会显示出来。"
        else -> "这一天没有留下小结。"
    }
}

fun summaryActionLabel(today: Boolean, connected: Boolean, canDraftLocally: Boolean): String? = when {
    today && (connected || canDraftLocally) -> "生成今日小结"
    !today && connected -> "同步这一天"
    else -> null
}
