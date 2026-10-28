package app.diary

import app.diary.domain.CalendarMath
import app.diary.domain.ExportDay
import app.diary.domain.ExportDocument
import app.diary.domain.ExportEntry
import app.diary.domain.ExportPhoto
import app.diary.domain.ExportSummary
import app.diary.domain.RemoteRelease
import app.diary.domain.SummarySourceEntry
import app.diary.domain.UpdateDecision
import app.diary.domain.VersionManifest
import app.diary.domain.buildSummaryPrompt
import app.diary.domain.diarySourceHash
import app.diary.domain.DayClock
import app.diary.domain.summaryMaterialExists
import app.diary.domain.toJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.YearMonth

class DiaryLogicTest {
    @Test
    fun shanghaiDayChangesAtMidnightNotUtc() {
        val before = Instant.parse("2026-10-27T15:59:00Z").toEpochMilli()
        val after = Instant.parse("2026-10-27T16:00:00Z").toEpochMilli()
        assertEquals("2026-10-27", DayClock.dayKey(before))
        assertEquals("2026-10-28", DayClock.dayKey(after))
        assertTrue(DayClock.isToday("2026-10-28", after))
        assertFalse(DayClock.isToday("2026-10-28", before))
    }

    @Test
    fun october2026StartsOnThursday() {
        val cells = CalendarMath.cells(YearMonth.of(2026, 10))
        assertEquals(null, cells[0])
        assertEquals(null, cells[1])
        assertEquals(null, cells[2])
        assertEquals(1, cells[3]?.dayOfMonth)
        assertEquals(31, cells.last { it != null }?.dayOfMonth)
        assertEquals(0, cells.size % 7)
    }

    @Test
    fun versionManifestParsesAndCompares() {
        val raw = """
            {
              "versionCode": 2,
              "versionName": "1.1.0",
              "apkUrl": "https://example.com/diary.apk",
              "changelog": "第一行\n第二行"
            }
        """.trimIndent()
        val release = VersionManifest.parse(raw)
        assertEquals(2, release.versionCode)
        assertEquals("1.1.0", release.versionName)
        assertEquals("第一行\n第二行", release.changelog)
        assertTrue(VersionManifest.decide(release, 1) is UpdateDecision.Available)
        assertTrue(VersionManifest.decide(release, 2) is UpdateDecision.UpToDate)
        val bad = VersionManifest.decide(release.copy(apkUrl = "ftp://nope"), 1)
        assertTrue(bad is UpdateDecision.Invalid)
    }

    @Test
    fun missingVersionFieldFails() {
        try {
            VersionManifest.parse("""{"versionName":"1"}""")
            throw AssertionError("应该拒绝缺字段的清单")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message!!.contains("versionCode"))
        }
    }

    @Test
    fun exportJsonEscapesAndKeepsSchema() {
        val document = ExportDocument(
            exportedAt = "2026-10-28T08:00:00+08:00",
            photosIncluded = false,
            versionName = "1.0.0",
            versionCode = 1,
            days = listOf(
                ExportDay(
                    day = "2026-10-28",
                    entries = listOf(
                        ExportEntry(
                            id = 3,
                            body = "他说\"好\"\n然后走了",
                            createdAt = "2026-10-28T08:00:00+08:00",
                            updatedAt = "2026-10-28T08:00:00+08:00",
                            photos = listOf(
                                ExportPhoto(1, "a.jpg", "窗边", "2026-10-28T08:01:00+08:00"),
                            ),
                        ),
                    ),
                    summary = ExportSummary("短摘要", "2026-10-28T09:00:00+08:00", "abc"),
                ),
            ),
        )
        val json = document.toJson()
        assertTrue(json.contains("\"schema\": \"diary.export.v1\""))
        assertTrue(json.contains("Asia/Shanghai"))
        assertTrue(json.contains("他说\\\"好\\\"\\n然后走了"))
        assertTrue(json.contains("\"fileName\": \"a.jpg\""))
        assertFalse(json.contains("photosIncluded\": true"))
    }

    @Test
    fun summaryPromptUsesOnlyProvidedText() {
        val entries = listOf(
            SummarySourceEntry("在衡山路散步", listOf("梧桐", "")),
        )
        assertTrue(summaryMaterialExists(entries))
        assertFalse(summaryMaterialExists(listOf(SummarySourceEntry("  ", listOf(" ")))))
        val prompt = buildSummaryPrompt("2026-10-28", entries)
        assertTrue(prompt.contains("2026-10-28"))
        assertTrue(prompt.contains("在衡山路散步"))
        assertTrue(prompt.contains("梧桐"))
        val changed = diarySourceHash(listOf(SummarySourceEntry("在衡山路散步", listOf("雨"))))
        assertTrue(changed != diarySourceHash(entries))
    }

    @Test
    fun blankApkUrlIsInvalid() {
        val decision = VersionManifest.decide(
            RemoteRelease(3, "1.2.0", "  ", ""),
            1,
        )
        assertTrue(decision is UpdateDecision.Invalid)
    }
}
