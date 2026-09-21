package com.sitelens.photos

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

enum class DatePreset(val label: String) {
    ALL("All dates"), YESTERDAY("Yesterday"), LAST_WEEK("Last week"), LAST_MONTH("Last month"), CUSTOM("Custom range");
    fun range(today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): PhotoDateRange {
        if (this == ALL) return PhotoDateRange(this)
        val end = when (this) {
            YESTERDAY -> today
            LAST_WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            LAST_MONTH -> today.withDayOfMonth(1)
            ALL, CUSTOM -> error("Choose start and end dates for a custom range")
        }
        val start = when (this) {
            YESTERDAY -> end.minusDays(1)
            LAST_WEEK -> end.minusWeeks(1)
            LAST_MONTH -> end.minusMonths(1)
            ALL, CUSTOM -> error("Choose start and end dates for a custom range")
        }
        val format = DateTimeFormatter.ofPattern("MMM d, yyyy")
        val label = if (this == YESTERDAY) start.format(format) else "${start.format(format)} – ${end.minusDays(1).format(format)}"
        return PhotoDateRange(this, start.atStartOfDay(zone).toInstant().toEpochMilli(), end.atStartOfDay(zone).toInstant().toEpochMilli(), "$label · ${zone.id}")
    }
}

data class PhotoDateRange(val preset: DatePreset, val start: Long? = null, val end: Long? = null, val description: String = "All photo dates") {
    companion object {
        fun custom(first: LocalDate, last: LocalDate, zone: ZoneId = ZoneId.systemDefault()): PhotoDateRange {
            require(!last.isBefore(first)) { "End date must be on or after start date." }
            val format = DateTimeFormatter.ofPattern("MMM d, yyyy")
            return PhotoDateRange(DatePreset.CUSTOM, first.atStartOfDay(zone).toInstant().toEpochMilli(),
                last.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
                "${first.format(format)} – ${last.format(format)} · ${zone.id}")
        }
    }
    fun contains(photo: Photo): Boolean = if (preset == DatePreset.ALL) true else
        photo.takenAtMillis?.let { it >= start!! && it < end!! } == true
}

data class RemotePhoto(val id: String, val name: String, val hash: String, val version: String = "", val mime: String = "image/jpeg")
data class UploadConflict(val photo: Photo, val matches: List<RemotePhoto>)
sealed interface ConflictDecision {
    data class Replace(val id: String) : ConflictDecision
    data object Skip : ConflictDecision
    data object Cancel : ConflictDecision
}
object UploadConflicts {
    fun matches(photo: Photo, remote: List<RemotePhoto>): List<RemotePhoto> = remote.filter {
        it.name == photo.name || (photo.hash.isNotEmpty() && it.hash == photo.hash)
    }
}
