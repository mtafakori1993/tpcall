package ir.tafakoritech.tpcall.util

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Small pure formatting helpers for call history. All date/time formatting is
 * done here (never stored server-side) so the unit tests stay fast and the UI
 * only ever renders Persian labels.
 */
object CallHistoryFormatters {

    /** "02:18" for 138 seconds, "1:02:05" for 3725 seconds. */
    fun formatDuration(totalSeconds: Long): String {
        val safe = totalSeconds.coerceAtLeast(0)
        val hours = safe / 3600
        val minutes = (safe % 3600) / 60
        val seconds = safe % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%02d:%02d".format(minutes, seconds)
        }
    }

    /**
     * Human-friendly Persian date/time label:
     *   امروز، ۱۴:۳۲
     *   دیروز، ۱۷:۴۵
     *   ۲۷ مرداد، ۱۰:۲۰
     * [now] and [zone] are injected for deterministic tests.
     */
    fun formatCallDateTime(
        startedAt: Instant,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val local = ZonedDateTime.ofInstant(startedAt, zone)
        val nowLocal = ZonedDateTime.ofInstant(now, zone)
        val time = toPersianDigits("%02d:%02d".format(local.hour, local.minute))
        return when {
            isSameDay(local, nowLocal) -> "امروز، $time"
            isYesterday(local, nowLocal) -> "دیروز، $time"
            else -> {
                val (_, jm, jd) = toJalali(local.year, local.monthValue, local.dayOfMonth)
                "${toPersianDigits(jd.toString())} ${persianMonths[jm - 1]}، $time"
            }
        }
    }

    private fun isSameDay(a: ZonedDateTime, b: ZonedDateTime): Boolean =
        a.toLocalDate() == b.toLocalDate()

    private fun isYesterday(a: ZonedDateTime, b: ZonedDateTime): Boolean =
        a.toLocalDate() == b.toLocalDate().minusDays(1)

    private fun toPersianDigits(input: String): String =
        input.map { c -> if (c in '0'..'9') ('۰' + (c - '0')) else c }.joinToString("")

    private val persianMonths = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
    )

    /**
     * Gregorian -> Jalali (Persian) calendar conversion using the standard
     * arithmetic algorithm. Returns (year, month, day).
     */
    internal fun toJalali(gy: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
        val gdm = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + 365 * gy + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400 + gd + gdm[gm - 1]
        var jy = -1595 + 33 * (days / 12053)
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm: Int
        val jd: Int
        if (days < 186) {
            jm = 1 + days / 31
            jd = 1 + days % 31
        } else {
            jm = 7 + (days - 186) / 30
            jd = 1 + (days - 186) % 30
        }
        return Triple(jy, jm, jd)
    }
}
