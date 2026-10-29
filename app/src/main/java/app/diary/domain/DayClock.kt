package app.diary.domain

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object DayClock {
    /** 与日记本一致：凌晨 4 点之前仍算前一天。 */
    const val BOUNDARY_HOUR = 4

    val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private val dayFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)
    private val monthFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy年M月", Locale.CHINA)
    private val timeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
    private val dateTimeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.CHINA)

    fun dayKey(epochMillis: Long = System.currentTimeMillis()): String {
        val zoned = Instant.ofEpochMilli(epochMillis).atZone(zone)
        val date = if (zoned.hour < BOUNDARY_HOUR) zoned.toLocalDate().minusDays(1) else zoned.toLocalDate()
        return date.toString()
    }

    fun today(nowMillis: Long = System.currentTimeMillis()): LocalDate = LocalDate.parse(dayKey(nowMillis))

    fun isDayKey(value: String): Boolean = runCatching { LocalDate.parse(value) }.isSuccess

    fun isToday(dayKey: String, nowMillis: Long = System.currentTimeMillis()): Boolean =
        dayKey == dayKey(nowMillis)

    fun isYesterday(dayKey: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val today = dayKey(nowMillis)
        return dayKey == shiftDay(today, -1, today = today)
    }

    /** 往前或往后一天。结果晚于 [today] 时返回 null，不能翻到还没来的日子。 */
    fun shiftDay(dayKey: String, days: Long, today: String = dayKey()): String? {
        if (!isDayKey(dayKey) || !isDayKey(today)) return null
        val next = LocalDate.parse(dayKey).plusDays(days).toString()
        if (next > today) return null
        return next
    }

    fun relativeTitle(dayKey: String, nowMillis: Long = System.currentTimeMillis()): String = when {
        isToday(dayKey, nowMillis) -> "今天"
        isYesterday(dayKey, nowMillis) -> "昨天"
        else -> formatDay(dayKey)
    }

    fun summaryHeading(dayKey: String, nowMillis: Long = System.currentTimeMillis()): String = when {
        isToday(dayKey, nowMillis) -> "今天的小结"
        isYesterday(dayKey, nowMillis) -> "昨天的小结"
        else -> "这一天的小结"
    }

    fun formatDay(dayKey: String): String = LocalDate.parse(dayKey).format(dayFormatter)

    fun formatMonth(month: YearMonth): String = month.format(monthFormatter)

    fun formatTime(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).format(timeFormatter)

    fun formatDateTime(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).format(dateTimeFormatter)

    fun isoOffset(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
}

object CalendarMath {
    /** Monday-first month grid. Nulls are the blank cells before and after the month. */
    fun cells(month: YearMonth): List<LocalDate?> {
        val first = month.atDay(1)
        val leading = (first.dayOfWeek.value + 6) % 7
        val cells = ArrayList<LocalDate?>(42)
        repeat(leading) { cells.add(null) }
        for (day in 1..month.lengthOfMonth()) {
            cells.add(month.atDay(day))
        }
        while (cells.size % 7 != 0) cells.add(null)
        return cells
    }
}
