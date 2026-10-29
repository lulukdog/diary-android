package app.diary

import app.diary.domain.SummarySourceEntry
import app.diary.domain.dayBrief
import app.diary.domain.extractiveDayBrief
import app.diary.domain.summaryCardVisible
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalBriefTest {
    @Test
    fun extractiveBriefQuotesTheirWordsAndKeepsThreeLines() {
        val brief = extractiveDayBrief(
            listOf(
                SummarySourceEntry("今天买了菜。", emptyList()),
                SummarySourceEntry("晚上开了个会。", listOf("白板")),
                SummarySourceEntry("回家看书。", emptyList()),
                SummarySourceEntry("第四则不该出现。", emptyList()),
            ),
        )
        assertEquals("今天买了菜。\n晚上开了个会。\n白板", brief)
        assertFalse(brief!!.contains("难过"))
        assertFalse(brief.contains("我在"))
        assertFalse(brief.contains("第四"))
    }

    @Test
    fun oneEntryBecomesUpToThreeSentences() {
        val brief = extractiveDayBrief(
            listOf(SummarySourceEntry("早上开会。中午吃饭。下午写完。晚上才回家。", emptyList())),
        )
        assertEquals("早上开会。\n中午吃饭。\n下午写完。", brief)
    }

    @Test
    fun blankDayStaysEmpty() {
        assertNull(extractiveDayBrief(emptyList()))
        assertNull(extractiveDayBrief(listOf(SummarySourceEntry("  ", listOf(" ")))))
    }

    @Test
    fun savedLocalBriefShowsWhenTheServerHasNoHeadline() {
        val brief = dayBrief(
            headline = null,
            moodLabel = null,
            mood = null,
            keywords = emptyList(),
            companion = null,
            moodLine = null,
            localSummary = "今天买了菜。\n晚上开了个会。",
            entryTexts = listOf("今天买了菜。"),
            justSaved = null,
        )
        assertEquals(listOf("今天买了菜。\n晚上开了个会。"), brief.summaryLines)
        assertTrue(summaryCardVisible(brief))
    }
}
