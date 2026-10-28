package app.diary

import app.diary.journal.JournalUrls
import app.diary.journal.LocalRow
import app.diary.journal.TimelineItem
import app.diary.journal.companionLines
import app.diary.journal.imageDataUrl
import app.diary.journal.journalErrorMessage
import app.diary.journal.parseJournalDay
import app.diary.journal.parseJournalReply
import app.diary.journal.timeline
import app.diary.journal.uploadParts
import app.diary.journal.visibleLocalKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JournalLogicTest {
    @Test
    fun defaultBaseIsTheCleartextJournalHostAndNotASecret() {
        assertEquals("http://39.96.68.27:10002", JournalUrls.DEFAULT_BASE)
        assertTrue(JournalUrls.DEFAULT_BASE.startsWith("http://"))
        assertFalse(JournalUrls.DEFAULT_BASE.contains("token", ignoreCase = true))
        assertFalse(JournalUrls.DEFAULT_BASE.contains("Bearer"))
    }

    @Test
    fun baseUrlTrimsSlashAndRejectsOtherSchemes() {
        assertEquals(JournalUrls.DEFAULT_BASE, JournalUrls.normalizeBase("  "))
        assertEquals(
            "http://39.96.68.27:10002",
            JournalUrls.normalizeBase("http://39.96.68.27:10002/"),
        )
        assertEquals(
            "http://39.96.68.27:10002/media/2026/10/a.jpg",
            JournalUrls.resolve("http://39.96.68.27:10002/", "/media/2026/10/a.jpg"),
        )
        try {
            JournalUrls.normalizeBase("ftp://example.com")
            throw AssertionError("应该拒绝 ftp")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message!!.contains("http"))
        }
    }

    @Test
    fun stateKeepsCompanionAndMoodLineAndDropsJsonNull() {
        val day = parseJournalDay(
            """
            {
              "day": "2026-10-28",
              "today": "2026-10-28",
              "card": {
                "entry_count": 1,
                "image_count": 0,
                "mood": null,
                "mood_label": null,
                "headline": null,
                "keywords": [],
                "companion": null,
                "finalized": 0
              },
              "entries": [
                {"id": 7, "ts": "2026-10-28T21:04:05", "kind": "text", "content": "今天真累", "image_path": null, "media_url": null}
              ],
              "utterances": [
                {"id": 3, "ts": "2026-10-28T21:10:00", "trigger": "signal:checkin", "content": "你提到累了。是有点撑着，还是我读重了？"}
              ],
              "mood": {"tone": "tired", "line": "听着有点累。先歇一会儿也行，我在。", "heard": ["累"], "source": "live"},
              "onboarding": {"done": true, "question": null},
              "configured": true
            }
            """.trimIndent(),
        )
        assertNull(day.card?.companion)
        assertNull(day.card?.mood)
        assertEquals(listOf("听着有点累。先歇一会儿也行，我在。"), day.companionLines())
        assertEquals("tired", day.mood.tone)
        assertEquals(1, day.utterances.size)
        assertEquals("signal:checkin", day.utterances[0].trigger)
        assertFalse(day.companionLines().any { it == "null" || it.contains("抑郁") })
        val beats = day.timeline()
        assertTrue(beats[0] is TimelineItem.Mine)
        assertTrue(beats[1] is TimelineItem.Theirs)
    }

    @Test
    fun sameCompanionAndMoodLineShowOnce() {
        assertEquals(
            listOf("我在。"),
            companionLines("我在。", " 我在。 "),
        )
        assertEquals(
            listOf("卡片上的一句", "白天先接住"),
            companionLines("卡片上的一句", "白天先接住"),
        )
        assertTrue(companionLines("  ", null).isEmpty())
        assertTrue(companionLines(null, "").isEmpty())
    }

    @Test
    fun askReplyCarriesMoodLine() {
        val reply = parseJournalReply(
            """
            {"reply":"先歇一会儿。","mood":{"tone":"tired","line":"听着有点累。","heard":[],"source":"ask"}}
            """.trimIndent(),
        )
        assertEquals("先歇一会儿。", reply.reply)
        assertEquals("听着有点累。", reply.mood.line)
        assertEquals("ask", reply.mood.source)
    }

    @Test
    fun errorDetailPrefersServerText() {
        assertTrue(journalErrorMessage(401, """{"detail":"未登录"}""").contains("口令"))
        assertEquals("空的话就不记了。", journalErrorMessage(400, """{"detail":"空的话就不记了。"}"""))
        assertEquals("Field required", journalErrorMessage(422, """{"detail":[{"msg":"Field required"}]}"""))
    }

    @Test
    fun uploadSkipsPartsThatAlreadyHaveServerIds() {
        val fresh = uploadParts("今天真累", null, listOf(1L to null, 2L to null))
        assertTrue(fresh.sendText)
        assertEquals(listOf(1L, 2L), fresh.pendingPhotoIds)
        val resumed = uploadParts("今天真累", 9L, listOf(1L to 11L, 2L to null))
        assertFalse(resumed.sendText)
        assertEquals(listOf(2L), resumed.pendingPhotoIds)
        assertTrue(uploadParts("  ", 9L, listOf(1L to 11L)).finished)
        assertTrue(uploadParts("", null, emptyList()).finished)
    }

    @Test
    fun syncedOriginalStaysOnItsOwnDayAndDoesNotDuplicateToday() {
        val rows = listOf(
            LocalRow(1, cached = false, synced = true, syncedServerDay = "2026-10-28", serverIds = setOf(9)),
            LocalRow(2, cached = true, synced = false, syncedServerDay = null, serverIds = setOf(9)),
            LocalRow(3, cached = false, synced = false, syncedServerDay = null, serverIds = emptySet()),
        )
        assertEquals(listOf(3L), visibleLocalKeys(rows, serverPageLoaded = true, viewingDay = "2026-10-28"))
        assertEquals(listOf(1L, 3L), visibleLocalKeys(rows, serverPageLoaded = true, viewingDay = "2026-10-01"))
        assertEquals(listOf(1L, 3L), visibleLocalKeys(rows, serverPageLoaded = false, viewingDay = "2026-10-28"))
    }

    @Test
    fun imageDataUrlUsesJpegMagic() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
        val url = imageDataUrl(bytes)
        assertTrue(url.startsWith("data:image/jpeg;base64,"))
    }
}
