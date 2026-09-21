package com.sitelens.photos

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class PhotoScanQueryTest {
    @Test fun startupIsLimitedToLastWeek() {
        assertEquals(DatePreset.LAST_WEEK, ScreenState().dateRange.preset)
        assertEquals(6, PhotoScanQuery.forRange(ScreenState().dateRange).args.size)
    }

    @Test fun queryUsesLocalWeekBoundariesInEachColumnsUnits() {
        val range = DatePreset.LAST_WEEK.range(LocalDate.parse("2026-03-09"), ZoneId.of("America/Los_Angeles"))
        // This week includes the spring daylight-saving transition (167 hours).
        assertEquals(167 * 60 * 60 * 1000L, range.end!! - range.start!!)
        assertEquals(listOf(range.start.toString(), range.end.toString(),
            (range.start / 1000).toString(), (range.end / 1000).toString(),
            (range.start / 1000).toString(), (range.end / 1000).toString()), PhotoScanQuery.forRange(range).args)
    }

    @Test fun onlyExplicitAllDatesRemovesDateBounds() {
        val query = PhotoScanQuery.forRange(DatePreset.ALL.range())
        assertEquals("is_pending=0", query.selection)
        assertTrue(query.args.isEmpty())
        DatePreset.entries.filter { it != DatePreset.ALL && it != DatePreset.CUSTOM }.forEach {
            assertEquals(6, PhotoScanQuery.forRange(it.range()).args.size)
        }
    }

    @Test fun customRangeIncludesWholeEndDayAcrossDaylightSaving() {
        val range = PhotoDateRange.custom(LocalDate.parse("2026-03-08"), LocalDate.parse("2026-03-08"), ZoneId.of("America/Los_Angeles"))
        assertEquals(23 * 60 * 60 * 1000L, range.end!! - range.start!!)
        val photo = Photo("k", "uri", "photo.jpg", "image/jpeg", "hash", true, "Construction")
        assertTrue(range.contains(photo.copy(takenAtMillis = range.start)))
        assertTrue(range.contains(photo.copy(takenAtMillis = range.end - 1)))
        assertFalse(range.contains(photo.copy(takenAtMillis = range.end)))
        assertEquals(6, PhotoScanQuery.forRange(range).args.size)
    }

    @Test(expected = IllegalArgumentException::class) fun reversedCustomDatesAreRejected() {
        PhotoDateRange.custom(LocalDate.parse("2026-09-20"), LocalDate.parse("2026-09-19"))
    }
}
