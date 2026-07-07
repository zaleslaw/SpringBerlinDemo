package com.zaleslaw.berlinprotesttracker.classifier

import com.zaleslaw.berlinprotesttracker.domain.*
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImpactClassifierTest {

    private val classifier = ImpactClassifier()

    private fun demo(
        title: String,
        rawText: String = title.lowercase(),
        startTime: LocalTime? = null,
        participantCount: Int? = null,
        routeText: String? = null
    ) = NormalizedDemonstration(
        sourceHash = SourceHash("test"),
        title = title,
        description = null,
        rawText = rawText,
        date = LocalDate.now(),
        startTime = startTime,
        endTime = null,
        timeText = null,
        district = null,
        postalCode = null,
        locationText = null,
        routeText = routeText,
        participantCount = participantCount
    )

    @Test
    fun `classifies climate topic with low impact when no operational signals fire`() {
        val result = classifier.classify(demo("Klima Demo", "klima umwelt fridays"))
        assertEquals(DemonstrationCategory.CLIMATE_ENVIRONMENT, result.category)
        // Topic weight alone is low → LOW impact
        assertTrue(result.score.value < 25, "Expected low impact for a static topic-only assembly")
        assertEquals(ImpactLevel.LOW, result.level)
    }

    @Test
    fun `moving route is the dominant operational signal`() {
        val static = classifier.classify(demo("Demo", "klima umwelt"))
        val marching = classifier.classify(demo("Demo", "klima umwelt", routeText = "von Mitte bis Kreuzberg"))
        assertTrue(marching.score.value > static.score.value, "A moving route should raise city impact")
        assertTrue(marching.evidence.any { it == "signal: moving route" })
    }

    @Test
    fun `large expected crowd raises impact`() {
        val base = classifier.classify(demo("Klima Demo", "klima umwelt"))
        val large = classifier.classify(demo("Klima Demo", "klima umwelt", participantCount = 5_000))
        assertTrue(large.score.value > base.score.value, "Large crowd should increase impact")
        assertTrue(large.evidence.any { it == "signal: large crowd" })
    }

    @Test
    fun `evening timing adds a small impact`() {
        val day = classifier.classify(demo("Labor demo", "arbeit lohn", startTime = LocalTime.of(10, 0)))
        val evening = classifier.classify(demo("Labor demo", "arbeit lohn", startTime = LocalTime.of(19, 0)))
        assertTrue(evening.score.value > day.score.value)
    }

    @Test
    fun `counter-event wording is picked up as an operational signal`() {
        val result = classifier.classify(demo("Kundgebung", "gegendemo blockade"))
        assertTrue(result.evidence.any { it == "signal: possible counter-event context" })
        assertTrue(result.reasons.any { it.contains("counter-event") })
    }

    @Test
    fun `public label is a neutral operational label, never an enum name`() {
        listOf("Gaza demo", "Ukraine demo", "AFD demo", "Klima demo").forEach { title ->
            val result = classifier.classify(demo(title, title.lowercase()))
            // Public label reads as an operational assembly label
            assertTrue(
                result.publicLabel.contains("assembly", ignoreCase = true),
                "Public label should be an operational label, was: ${result.publicLabel}"
            )
            // Never leak internal enum names to the public label
            assertTrue(result.publicLabel.none { it == '_' })
            // No judgmental / identity-based wording in the reasons
            result.reasons.forEach { reason ->
                val lower = reason.lowercase()
                assertTrue(!lower.contains("dangerous"), "Reason should not say 'dangerous': $reason")
                assertTrue(!lower.contains("nationality"), "Reason should not mention nationality: $reason")
                assertTrue(!lower.contains("ethnicity"), "Reason should not mention ethnicity: $reason")
                assertTrue(!lower.contains("threat"), "Reason should not frame people as a threat: $reason")
            }
        }
    }

    @Test
    fun `every assessment returns explainable reasons and evidence`() {
        val result = classifier.classify(demo("Klima Demo", "klima umwelt", routeText = "von A bis B"))
        assertTrue(result.reasons.isNotEmpty())
        assertTrue(result.evidence.isNotEmpty())
    }

    @Test
    fun `high impact is explained by stacked operational signals`() {
        val result = classifier.classify(
            demo(
                title = "Demokratie Kundgebung",
                rawText = "demokratie gegendemo",
                participantCount = 5_000,
                routeText = "von Alexanderplatz bis Brandenburger Tor"
            )
        )
        assertTrue(result.score.value >= 50, "Stacked signals should produce HIGH or VERY_HIGH impact")
        assertTrue(result.level == ImpactLevel.HIGH || result.level == ImpactLevel.VERY_HIGH)
        assertTrue(result.evidence.any { it == "signal: moving route" })
        assertTrue(result.evidence.any { it == "signal: large crowd" })
        assertTrue(result.evidence.any { it == "signal: possible counter-event context" })
    }
}
