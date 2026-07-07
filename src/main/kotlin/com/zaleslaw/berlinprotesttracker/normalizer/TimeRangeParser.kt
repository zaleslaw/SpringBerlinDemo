package com.zaleslaw.berlinprotesttracker.normalizer

import java.time.LocalTime
import java.time.format.DateTimeParseException

data class ParsedTimeRange(
    val startTime: LocalTime?,
    val endTime: LocalTime?,
    val timeText: String?
)

object TimeRangeParser {

    private val rangePattern = Regex("""(\d{1,2})[.:](\d{2})\s*[-–]\s*(\d{1,2})[.:](\d{2})""")
    private val singlePattern = Regex("""(\d{1,2})[.:](\d{2})""")

    fun parse(raw: String?): ParsedTimeRange {
        if (raw == null) return ParsedTimeRange(null, null, null)
        val cleaned = raw.trim().replace('\u00a0', ' ')

        val range = rangePattern.find(cleaned)
        if (range != null) {
            val (h1, m1, h2, m2) = range.destructured
            val start = safeTime(h1.toInt(), m1.toInt())
            val end = safeTime(h2.toInt(), m2.toInt())
            return ParsedTimeRange(start, end, cleaned)
        }

        val single = singlePattern.find(cleaned)
        if (single != null) {
            val (h, m) = single.destructured
            val start = safeTime(h.toInt(), m.toInt())
            return ParsedTimeRange(start, null, cleaned)
        }

        return ParsedTimeRange(null, null, cleaned.takeIf { it.isNotBlank() })
    }

    private fun safeTime(hour: Int, minute: Int): LocalTime? = try {
        LocalTime.of(hour, minute)
    } catch (_: DateTimeParseException) {
        null
    } catch (_: Exception) {
        null
    }
}
