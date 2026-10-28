package app.diary.domain

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object DayClock {
    val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private val dayFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)
    private val monthFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy年M月", Locale.CHINA)
    private val timeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
    private val dateTimeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.CHINA)

    fun dayKey(epochMillis: Long = System.currentTimeMillis()): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().toString()

    fun isDayKey(value: String): Boolean = runCatching { LocalDate.parse(value) }.isSuccess

    fun isToday(dayKey: String, nowMillis: Long = System.currentTimeMillis()): Boolean =
        dayKey == dayKey(nowMillis)

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
