package ir.tafakoritech.tpcall

import ir.tafakoritech.tpcall.util.CallHistoryFormatters
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class CallHistoryFormattersTest {

    @Test
    fun `duration formats seconds as MM SS`() {
        assertEquals("00:00", CallHistoryFormatters.formatDuration(0))
        assertEquals("00:43", CallHistoryFormatters.formatDuration(43))
        assertEquals("02:18", CallHistoryFormatters.formatDuration(138))
        assertEquals("59:59", CallHistoryFormatters.formatDuration(3599))
    }

    @Test
    fun `duration includes hours for long calls`() {
        assertEquals("1:00:00", CallHistoryFormatters.formatDuration(3600))
        assertEquals("1:02:05", CallHistoryFormatters.formatDuration(3725))
        assertEquals("25:00:00", CallHistoryFormatters.formatDuration(90_000))
    }

    @Test
    fun `duration clamps negative values`() {
        assertEquals("00:00", CallHistoryFormatters.formatDuration(-5))
    }

    @Test
    fun `today formats as today with persian digits`() {
        val now = Instant.parse("2026-08-19T16:00:00Z")
        val startedAt = Instant.parse("2026-08-19T14:32:00Z")
        assertEquals(
            "امروز، ۱۴:۳۲",
            CallHistoryFormatters.formatCallDateTime(startedAt, now, ZoneId.of("UTC")),
        )
    }

    @Test
    fun `yesterday formats with persian digits`() {
        val now = Instant.parse("2026-08-19T10:00:00Z")
        val startedAt = Instant.parse("2026-08-18T17:45:00Z")
        assertEquals(
            "دیروز، ۱۷:۴۵",
            CallHistoryFormatters.formatCallDateTime(startedAt, now, ZoneId.of("UTC")),
        )
    }

    @Test
    fun `older calls show jalali month and day`() {
        val now = Instant.parse("2026-09-01T10:00:00Z")
        // 2026-08-18 is 27 Mordad 1405.
        val startedAt = Instant.parse("2026-08-18T10:20:00Z")
        assertEquals(
            "۲۷ مرداد، ۱۰:۲۰",
            CallHistoryFormatters.formatCallDateTime(startedAt, now, ZoneId.of("UTC")),
        )
    }

    @Test
    fun `cross midnight still respects device timezone`() {
        // 2026-08-19 23:30 UTC is already 2026-08-20 in Tehran (UTC+3:30).
        val now = Instant.parse("2026-08-20T01:00:00Z")
        val startedAt = Instant.parse("2026-08-19T23:30:00Z")
        assertEquals(
            "امروز، ۰۳:۰۰",
            CallHistoryFormatters.formatCallDateTime(startedAt, now, ZoneId.of("Asia/Tehran")),
        )
    }

    @Test
    fun `jalali conversion matches known dates`() {
        assertEquals(Triple(1400, 1, 1), CallHistoryFormatters.toJalali(2021, 3, 21))
        assertEquals(Triple(1405, 1, 1), CallHistoryFormatters.toJalali(2026, 3, 21))
        assertEquals(Triple(1405, 5, 28), CallHistoryFormatters.toJalali(2026, 8, 19))
        assertEquals(Triple(1402, 7, 1), CallHistoryFormatters.toJalali(2023, 9, 23))
    }
}
