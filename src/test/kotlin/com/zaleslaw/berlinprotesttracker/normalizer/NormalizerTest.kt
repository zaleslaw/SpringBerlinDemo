package com.zaleslaw.berlinprotesttracker.normalizer

import com.zaleslaw.berlinprotesttracker.parser.RawDemonstrationRow
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NormalizerTest {

    private val normalizer = DemonstrationNormalizer()

    private fun row(
        date: String? = "26.06.2026",
        time: String? = null,
        title: String? = "Test Demo",
        location: String? = "Mitte",
        route: String? = null,
        district: String? = "Mitte",
        participants: String? = null
    ) = RawDemonstrationRow(
        rowIndex = 0,
        rawText = "$title $location",
        titleRaw = title,
        dateRaw = date,
        timeRaw = time,
        locationRaw = location,
        routeRaw = route,
        districtRaw = district,
        participantCountRaw = participants
    )

    @Test
    fun `normalizes valid row successfully`() {
        val result = normalizer.normalize(row())
        assertIs<NormalizationResult.Success>(result)
        assertEquals(LocalDate.of(2026, 6, 26), result.value.date)
    }

    @Test
    fun `rejects row with missing date`() {
        val result = normalizer.normalize(row(date = null))
        assertIs<NormalizationResult.Rejected>(result)
    }

    @Test
    fun `rejects row with unparseable date`() {
        val result = normalizer.normalize(row(date = "not-a-date"))
        assertIs<NormalizationResult.Rejected>(result)
    }

    @Test
    fun `parses time range correctly`() {
        val result = normalizer.normalize(row(time = "14:00 - 18:00"))
        assertIs<NormalizationResult.Success>(result)
        assertEquals(LocalTime.of(14, 0), result.value.startTime)
        assertEquals(LocalTime.of(18, 0), result.value.endTime)
    }

    @Test
    fun `parses participant count`() {
        val result = normalizer.normalize(row(participants = "2.500"))
        assertIs<NormalizationResult.Success>(result)
        assertEquals(2500, result.value.participantCount)
    }

    @Test
    fun `extracts postal code from raw text`() {
        val r = row().copy(rawText = "Mitte 10117 Berlin Demo")
        val result = normalizer.normalize(r)
        assertIs<NormalizationResult.Success>(result)
        assertEquals("10117", result.value.postalCode)
    }
}
