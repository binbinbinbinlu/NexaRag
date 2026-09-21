package com.sitelens.photos

/** Filter metadata before opening originals, hashing, or running image labeling. */
data class PhotoScanQuery(val selection: String, val args: List<String>) {
    companion object {
        fun forRange(range: PhotoDateRange): PhotoScanQuery {
            if (range.preset == DatePreset.ALL) return PhotoScanQuery("is_pending=0", emptyList())
            val start = requireNotNull(range.start)
            val end = requireNotNull(range.end)
            // DATE_TAKEN uses milliseconds; DATE_ADDED / DATE_MODIFIED use seconds.
            // Match the same fallback priority used when constructing each Photo.
            val missingTaken = "(datetaken IS NULL OR datetaken<=0)"
            val missingAdded = "(date_added IS NULL OR date_added<=0)"
            val startSeconds = Math.floorDiv(start + 999, 1000)
            val endSeconds = Math.floorDiv(end + 999, 1000)
            return PhotoScanQuery(
                "is_pending=0 AND ((datetaken>0 AND datetaken>=? AND datetaken<?) OR " +
                    "($missingTaken AND date_added>0 AND date_added>=? AND date_added<?) OR " +
                    "($missingTaken AND $missingAdded AND date_modified>0 AND date_modified>=? AND date_modified<?))",
                listOf(start, end, startSeconds, endSeconds, startSeconds, endSeconds).map { it.toString() }
            )
        }
    }
}
