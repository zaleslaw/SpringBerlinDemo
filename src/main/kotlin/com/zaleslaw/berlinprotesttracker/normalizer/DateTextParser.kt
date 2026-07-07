package com.zaleslaw.berlinprotesttracker.normalizer

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

object DateTextParser {

    private val germanLocale = Locale.GERMAN

    private val formatters = listOf(
        DateTimeFormatter.ofPattern("dd.MM.yyyy", germanLocale),
        DateTimeFormatter.ofPattern("d.MM.yyyy", germanLocale),
        DateTimeFormatter.ofPattern("dd.MM.yy", germanLocale),
        DateTimeFormatter.ofPattern("d.M.yyyy", germanLocale),
        DateTimeFormatter.ofPattern("dd.M.yyyy", germanLocale)
    )

    fun parse(raw: String?): LocalDate? {
        if (raw == null) return null
        val cleaned = raw.trim()
            .replace("\\s+".toRegex(), " ")
        for (fmt in formatters) {
            try {
                return LocalDate.parse(cleaned, fmt)
            } catch (_: DateTimeParseException) {
            }
        }
        return null
    }
}
