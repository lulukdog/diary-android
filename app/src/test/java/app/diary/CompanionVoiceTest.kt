package app.diary

import app.diary.domain.CompanionVoice
import app.diary.domain.DayClock
import app.diary.domain.UpdateUrls
import app.diary.domain.dayBrief
import app.diary.domain.summaryActionLabel
import app.diary.domain.summaryCardVisible
import app.diary.domain.summaryEmptyMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CompanionVoiceTest {
    @Test
    fun practicalNotesStayQuiet() {
        assertNull(CompanionVoice.lineFor("今天买了菜，开了个会。"))
        assertNull(CompanionVoice.lineFor("项目进度正常"))
        assertNull(CompanionVoice.lineFor("真好吃"))
        assertNull(CompanionVoice.lineFor("今天不累"))
        assertNull(CompanionVoice.lineFor("retired early"))
        assertNull(CompanionVoice.lineFor(""))
        assertNull(CompanionVoice.lineFor("   "))
    }

    @Test
    fun emotionalCuesGetAShortLineAndNegationDoesNotFlipHappy() {
        assertEquals("听着有点累。先歇一会儿也行，我在。", CompanionVoice.lineFor("今天好累，不想动"))
        assertEquals("挺撑的。不用一次扛完，我在。", CompanionVoice.lineFor("压力好大，快崩溃了"))
        assertEquals("难过就先放这儿。我在。", CompanionVoice.lineFor("有点难过"))
        assertEquals("难过就先放这儿。我在。", CompanionVoice.lineFor("不开心"))
        assertEquals("一个人的时候，也可以跟我说。我在。", CompanionVoice.lineFor("好孤独"))
        assertEquals("悬着的话先放下一点。我在。", CompanionVoice.lineFor("有点焦虑，怕搞砸"))
        assertEquals("听着你挺高兴的。真好。", CompanionVoice.lineFor("今天好开心"))
        assertEquals("听着你挺高兴的。真好。", CompanionVoice.lineFor("虽然好累，但是好开心"))
        assertFalse(CompanionVoice.lineFor("不开心")!!.contains("高兴"))
        assertFalse(CompanionVoice.lineFor("好累")!!.contains("抑郁"))
    }

    @Test
    fun dayNavigationStopsAtToday() {
        val atBoundary = Instant.parse("2026-10-27T20:00:00Z").toEpochMilli()
        assertEquals("今天", DayClock.relativeTitle("2026-10-28", atBoundary))
        assertEquals("昨天", DayClock.relativeTitle("2026-10-27", atBoundary))
        assertEquals("2026-10-27", DayClock.shiftDay("2026-10-28", -1, today = "2026-10-28"))
        assertEquals("2026-09-30", DayClock.shiftDay("2026-10-01", -1, today = "2026-10-28"))
        assertNull(DayClock.shiftDay("2026-10-28", 1, today = "2026-10-28"))
        assertEquals("昨天的小结", DayClock.summaryHeading("2026-10-27", atBoundary))
        assertTrue(DayClock.isYesterday("2026-10-27", atBoundary))
        assertFalse(DayClock.isYesterday("2026-10-28", atBoundary))
    }

    @Test
    fun serverLineWinsAndHeadlineStaysInTheSummary() {
        val brief = dayBrief(
            headline = "这一天过得很沉",
            moodLabel = "有点撑",
            mood = -1,
            keywords = listOf("加班"),
            companion = "我在。",
            moodLine = "听着有点累。先歇一会儿也行，我在。",
            localSummary = "本地的一段",
            entryTexts = listOf("今天好开心"),
            justSaved = "今天好开心",
        )
        assertEquals("我在。", brief.presence)
        assertTrue(brief.summaryLines.contains("这一天过得很沉"))
        assertTrue(brief.summaryLines.contains("「有点撑」"))
        assertTrue(brief.summaryLines.contains("听着有点累。先歇一会儿也行，我在。"))
        assertFalse(brief.summaryLines.contains("本地的一段"))
        assertEquals(listOf("加班"), brief.keywords)
        assertTrue(summaryCardVisible(brief))
    }

    @Test
    fun localLineShowsWhenTheServerIsQuiet() {
        val brief = dayBrief(
            headline = null,
            moodLabel = null,
            mood = null,
            keywords = emptyList(),
            companion = null,
            moodLine = "  ",
            localSummary = null,
            entryTexts = listOf("买了菜", "今天好累"),
            justSaved = null,
        )
        assertEquals("听着有点累。先歇一会儿也行，我在。", brief.presence)
        assertFalse(summaryCardVisible(brief))
        assertEquals(
            "今天的小结还没写好。上面那句先留在这儿。",
            summaryEmptyMessage(today = true, connected = true, finalized = false, hasPresence = true),
        )
        assertEquals("生成今日小结", summaryActionLabel(today = true))
        assertEquals("整理这一天", summaryActionLabel(today = false))
    }

    @Test
    fun updateManifestFallsBackToTheGithubRelease() {
        assertEquals(
            "https://raw.githubusercontent.com/lulukdog/diary-android/main/release/version.json",
            UpdateUrls.resolve("  "),
        )
        assertEquals("https://example.com/version.json", UpdateUrls.resolve(" https://example.com/version.json "))
    }
}
