package com.sitelens.photos

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class UploadOptionsTest {
    private val zone = ZoneId.of("America/Los_Angeles")
    private val base = Photo("k", "uri", "photo.jpg", "image/jpeg", "hash", true, "Construction")
    private fun millis(date: String) = LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli()

    @Test fun yesterdayIncludesStartButExcludesToday() {
        val range = DatePreset.YESTERDAY.range(LocalDate.parse("2026-09-20"), zone)
        assertTrue(range.contains(base.copy(takenAtMillis = millis("2026-09-19"))))
        assertFalse(range.contains(base.copy(takenAtMillis = millis("2026-09-20"))))
        assertFalse(range.contains(base.copy(takenAtMillis = millis("2026-09-19") - 1)))
    }
    @Test fun previousWeekUsesMondayThroughSunday() {
        val range = DatePreset.LAST_WEEK.range(LocalDate.parse("2026-09-20"), zone)
        assertEquals(millis("2026-09-07"), range.start)
        assertEquals(millis("2026-09-14"), range.end)
        assertTrue(range.contains(base.copy(takenAtMillis = range.end!! - 1)))
    }
    @Test fun lastMonthHandlesLeapYearsAndYearBoundary() {
        val february = DatePreset.LAST_MONTH.range(LocalDate.parse("2024-03-31"), zone)
        assertEquals(millis("2024-02-01"), february.start)
        assertEquals(millis("2024-03-01"), february.end)
        assertTrue(february.contains(base.copy(takenAtMillis = millis("2024-02-29"))))
        assertEquals(millis("2025-12-01"), DatePreset.LAST_MONTH.range(LocalDate.parse("2026-01-01"), zone).start)
    }
    @Test fun yesterdayRespectsDaylightSavingInsteadOfFixed24Hours() {
        val spring = DatePreset.YESTERDAY.range(LocalDate.parse("2026-03-09"), zone)
        assertEquals(23 * 60 * 60 * 1000L, spring.end!! - spring.start!!)
        val autumn = DatePreset.YESTERDAY.range(LocalDate.parse("2026-11-02"), zone)
        assertEquals(25 * 60 * 60 * 1000L, autumn.end!! - autumn.start!!)
    }
    @Test fun undatedPhotosAreOnlyInAllDates() {
        assertTrue(DatePreset.ALL.range().contains(base))
        assertFalse(DatePreset.YESTERDAY.range().contains(base))
    }
    @Test fun uploadQueueExcludesHiddenDatesAndUncheckedPhotos() {
        val range = DatePreset.YESTERDAY.range(LocalDate.parse("2026-09-20"), zone)
        val inside = base.copy(takenAtMillis = millis("2026-09-19"))
        val state = ScreenState(dateRange = range, photos = listOf(inside, inside.copy(key = "unchecked", override = false), base.copy(key = "outside", takenAtMillis = millis("2026-09-18"))))
        assertEquals(listOf(inside), state.uploadSelection)
    }
    @Test fun uploadedPhotosRequireExplicitReselection() {
        assertFalse(base.copy(uploaded = true, override = true).selected)
        assertTrue(base.copy(uploaded = true, reupload = true).selected)
    }
    @Test fun conflictsIncludeSameNameDifferentBytesAndSameBytesDifferentName() {
        val byName = RemotePhoto("one", "photo.jpg", "other")
        val byHash = RemotePhoto("two", "renamed.jpg", "hash")
        val unrelated = RemotePhoto("three", "different.jpg", "other")
        assertEquals(listOf(byName, byHash), UploadConflicts.matches(base, listOf(byName, byHash, unrelated)))
    }
    @Test fun emptyHashesDoNotCreateFalseConflicts() {
        assertTrue(UploadConflicts.matches(base.copy(hash = ""), listOf(RemotePhoto("one", "different.jpg", ""))).isEmpty())
    }
}
