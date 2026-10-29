package app.diary.domain

/**
 * 本地陪伴句。只在听得出情绪时开口，平常的记事保持安静。
 * 不判断病情，也不讲道理。
 */
object CompanionVoice {
    fun lineFor(text: String): String? {
        val raw = text.trim()
        if (raw.length < 2) return null
        val folded = raw.lowercase()
        var bestAt = -1
        var best: String? = null
        for (cue in CUES) {
            val at = latestHit(raw, folded, cue.words)
            if (at > bestAt) {
                bestAt = at
                best = cue.line
            }
        }
        return best
    }

    private fun latestHit(raw: String, folded: String, words: List<String>): Int {
        var best = -1
        for (word in words) {
            val ascii = word.all { it.code < 128 }
            val hay = if (ascii) folded else raw
            val needle = if (ascii) word.lowercase() else word
            var start = 0
            while (start <= hay.length - needle.length) {
                val index = hay.indexOf(needle, start)
                if (index < 0) break
                if (accepted(hay, index, needle)) best = maxOf(best, index)
                start = index + needle.length
            }
        }
        return best
    }

    private fun accepted(hay: String, index: Int, needle: String): Boolean {
        val before = hay.getOrNull(index - 1)
        if (before == '不' || before == '没') return false
        if (needle.any { it.code >= 128 }) return true
        if (index >= 4 && hay.regionMatches(index - 4, "not ", 0, 4)) return false
        val prev = hay.getOrNull(index - 1)
        val after = hay.getOrNull(index + needle.length)
        if (prev != null && prev.isLetter()) return false
        if (after != null && after.isLetter()) return false
        return true
    }
}

private class Cue(val words: List<String>, val line: String)

private val CUES = listOf(
    Cue(
        words = listOf(
            "好累", "很累", "太累", "累了", "累死", "疲惫", "疲倦", "筋疲力尽",
            "没力气", "撑不住", "好困", "困死", "睡不够", "没睡好", "困了",
            "tired", "exhausted",
        ),
        line = "听着有点累。先歇一会儿也行，我在。",
    ),
    Cue(
        words = listOf(
            "压力大", "压力好", "压力山", "喘不过气", "崩溃", "扛不住", "忙死",
            "受不了", "心累", "好烦", "烦死", "心烦",
            "stressed", "overwhelmed",
        ),
        line = "挺撑的。不用一次扛完，我在。",
    ),
    Cue(
        words = listOf(
            "难过", "伤心", "想哭", "哭了", "好难受", "心里难受", "失落", "低落",
            "郁闷", "委屈", "心酸", "不开心", "不高兴",
            "sad", "heartbroken",
        ),
        line = "难过就先放这儿。我在。",
    ),
    Cue(
        words = listOf(
            "孤独", "孤单", "寂寞", "没人陪", "没人理", "没有人陪", "没人说话",
            "lonely",
        ),
        line = "一个人的时候，也可以跟我说。我在。",
    ),
    Cue(
        words = listOf(
            "焦虑", "好慌", "心慌", "不安", "担心", "害怕", "紧张", "慌了", "睡不着",
            "anxious", "worried",
        ),
        line = "悬着的话先放下一点。我在。",
    ),
    Cue(
        words = listOf(
            "开心", "高兴", "快乐", "幸福", "太好了", "好爽", "哈哈", "嘿嘿", "顺利",
            "happy", "glad",
        ),
        line = "听着你挺高兴的。真好。",
    ),
)
