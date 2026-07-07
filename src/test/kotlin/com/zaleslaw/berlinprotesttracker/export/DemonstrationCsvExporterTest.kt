package com.zaleslaw.berlinprotesttracker.export

import com.zaleslaw.berlinprotesttracker.domain.Demonstration
import com.zaleslaw.berlinprotesttracker.domain.DemonstrationCategory
import com.zaleslaw.berlinprotesttracker.domain.DemonstrationId
import com.zaleslaw.berlinprotesttracker.domain.EventGeometry
import com.zaleslaw.berlinprotesttracker.domain.ImpactAssessment
import com.zaleslaw.berlinprotesttracker.domain.ImpactLevel
import com.zaleslaw.berlinprotesttracker.domain.ImpactScore
import com.zaleslaw.berlinprotesttracker.domain.SourceHash
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DemonstrationCsvExporterTest {

    private val exporter = DemonstrationCsvExporter()

    private fun impact(
        category: DemonstrationCategory,
        publicLabel: String,
        level: ImpactLevel,
        score: Int,
        reasons: List<String> = emptyList()
    ) = ImpactAssessment(
        category = category,
        publicLabel = publicLabel,
        level = level,
        score = ImpactScore(score),
        reasons = reasons,
        evidence = emptyList()
    )

    @Test
    fun `should format CSV with headers`() {
        val csv = exporter.formatCsv(emptyList())
        assertEquals("id,title,date,startTime,endTime,timeText,district,postalCode,locationText,category,impactLevel,impactScore,participantCount,lat,lon,geocodingConfidence,routeText,reasons\n", csv)
    }

    @Test
    fun `should format CSV with Point geometry`() {
        val demo = Demonstration(
            id = DemonstrationId("test-1"),
            sourceHash = SourceHash("hash1"),
            title = "Test Demo",
            description = "A test",
            rawText = "test demo",
            date = LocalDate.of(2026, 1, 15),
            startTime = LocalTime.of(14, 30),
            endTime = LocalTime.of(17, 0),
            timeText = "2:30 PM - 5:00 PM",
            district = "Mitte",
            postalCode = "10115",
            locationText = "Alexanderplatz",
            routeText = null,
            participantCount = 500,
            category = DemonstrationCategory.CLIMATE_ENVIRONMENT,
            impact = impact(
                category = DemonstrationCategory.CLIMATE_ENVIRONMENT,
                publicLabel = "Climate or environment assembly",
                level = ImpactLevel.MEDIUM,
                score = 35,
                reasons = listOf("Climate protest", "500+ participants")
            ),
            geometry = EventGeometry.Point(lat = 52.521, lon = 13.414, confidence = 0.95)
        )

        val csv = exporter.formatCsv(listOf(demo))
        val lines = csv.split("\n")
        assertEquals(3, lines.size) // header + data + trailing newline
        assertTrue(lines[1].contains("test-1"))
        assertTrue(lines[1].contains("Test Demo"))
        assertTrue(lines[1].contains("2026-01-15"))
        assertTrue(lines[1].contains("14:30:00"))
        assertTrue(lines[1].contains("17:00:00"))
        assertTrue(lines[1].contains("Mitte"))
        assertTrue(lines[1].contains("10115"))
        assertTrue(lines[1].contains("52.521"))
        assertTrue(lines[1].contains("13.414"))
        assertTrue(lines[1].contains("0.95"))
    }

    @Test
    fun `should escape fields with commas`() {
        val demo = Demonstration(
            id = DemonstrationId("test-2"),
            sourceHash = SourceHash("hash2"),
            title = "Demo with, comma",
            description = null,
            rawText = "test",
            date = LocalDate.of(2026, 1, 15),
            startTime = null,
            endTime = null,
            timeText = null,
            district = "Test, District",
            postalCode = null,
            locationText = null,
            routeText = null,
            participantCount = null,
            category = DemonstrationCategory.OTHER,
            impact = impact(
                category = DemonstrationCategory.OTHER,
                publicLabel = "Civic assembly",
                level = ImpactLevel.LOW,
                score = 10
            ),
            geometry = EventGeometry.Unknown
        )

        val csv = exporter.formatCsv(listOf(demo))
        val lines = csv.split("\n")
        assertTrue(lines[1].contains("\"Demo with, comma\""))
        assertTrue(lines[1].contains("\"Test, District\""))
    }

    @Test
    fun `should escape fields with quotes`() {
        val demo = Demonstration(
            id = DemonstrationId("test-3"),
            sourceHash = SourceHash("hash3"),
            title = "Demo with \"quotes\"",
            description = null,
            rawText = "test",
            date = LocalDate.of(2026, 1, 15),
            startTime = null,
            endTime = null,
            timeText = null,
            district = null,
            postalCode = null,
            locationText = null,
            routeText = null,
            participantCount = null,
            category = DemonstrationCategory.OTHER,
            impact = impact(
                category = DemonstrationCategory.OTHER,
                publicLabel = "Civic assembly",
                level = ImpactLevel.LOW,
                score = 10
            ),
            geometry = EventGeometry.Unknown
        )

        val csv = exporter.formatCsv(listOf(demo))
        val lines = csv.split("\n")
        assertTrue(lines[1].contains("\"Demo with \"\"quotes\"\"\""))
    }

    @Test
    fun `should join reasons with semicolon`() {
        val demo = Demonstration(
            id = DemonstrationId("test-5"),
            sourceHash = SourceHash("hash5"),
            title = "Multi-reason Demo",
            description = null,
            rawText = "test",
            date = LocalDate.of(2026, 1, 15),
            startTime = null,
            endTime = null,
            timeText = null,
            district = null,
            postalCode = null,
            locationText = null,
            routeText = null,
            participantCount = null,
            category = DemonstrationCategory.CLIMATE_ENVIRONMENT,
            impact = impact(
                category = DemonstrationCategory.CLIMATE_ENVIRONMENT,
                publicLabel = "Climate or environment assembly",
                level = ImpactLevel.HIGH,
                score = 55,
                reasons = listOf("Climate or environment-related topic", "Large expected crowd", "Event takes place in the evening")
            ),
            geometry = EventGeometry.Unknown
        )

        val csv = exporter.formatCsv(listOf(demo))
        val lines = csv.split("\n")
        assertTrue(lines[1].contains("Climate or environment-related topic; Large expected crowd; Event takes place in the evening"))
    }

    @Test
    fun `should handle empty fields`() {
        val demo = Demonstration(
            id = DemonstrationId("test-6"),
            sourceHash = SourceHash("hash6"),
            title = "Minimal Demo",
            description = null,
            rawText = "minimal",
            date = LocalDate.of(2026, 1, 15),
            startTime = null,
            endTime = null,
            timeText = null,
            district = null,
            postalCode = null,
            locationText = null,
            routeText = null,
            participantCount = null,
            category = DemonstrationCategory.OTHER,
            impact = impact(
                category = DemonstrationCategory.OTHER,
                publicLabel = "Civic assembly",
                level = ImpactLevel.LOW,
                score = 5
            ),
            geometry = EventGeometry.Unknown
        )

        val csv = exporter.formatCsv(listOf(demo))
        val lines = csv.split("\n")
        // Count empty fields - district, postalCode, locationText, etc. should be empty
        val dataLine = lines[1]
        assertTrue(dataLine.contains(",,")) // consecutive commas for empty fields
    }
}
