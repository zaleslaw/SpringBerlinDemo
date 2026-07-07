package com.zaleslaw.berlinprotesttracker.importjob

import com.zaleslaw.berlinprotesttracker.classifier.ImpactClassifier
import com.zaleslaw.berlinprotesttracker.config.AppProperties
import com.zaleslaw.berlinprotesttracker.domain.NormalizedDemonstration
import com.zaleslaw.berlinprotesttracker.domain.SourceHash
import com.zaleslaw.berlinprotesttracker.geocoder.GeocoderService
import com.zaleslaw.berlinprotesttracker.infra.ParserRunHandle
import com.zaleslaw.berlinprotesttracker.infra.ParserRunRepository
import com.zaleslaw.berlinprotesttracker.normalizer.DemonstrationNormalizer
import com.zaleslaw.berlinprotesttracker.normalizer.NormalizationResult
import com.zaleslaw.berlinprotesttracker.parser.BerlinPolicePageClient
import com.zaleslaw.berlinprotesttracker.parser.BerlinPolicePageParser
import com.zaleslaw.berlinprotesttracker.parser.RawDemonstrationRow
import com.zaleslaw.berlinprotesttracker.snapshot.DemonstrationReadModel
import com.zaleslaw.berlinprotesttracker.snapshot.DemonstrationSnapshot
import com.zaleslaw.berlinprotesttracker.snapshot.ImportDiagnostics
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DemonstrationImportServiceTest {

    private val pageClient = mock(BerlinPolicePageClient::class.java)
    private val parser = mock(BerlinPolicePageParser::class.java)
    private val normalizer = mock(DemonstrationNormalizer::class.java)
    private val geocoder = mock(GeocoderService::class.java)
    private val parserRunRepository = mock(ParserRunRepository::class.java)
    private val classifier = ImpactClassifier()
    private val readModel = DemonstrationReadModel()

    private val goodSnapshot = DemonstrationSnapshot(
        events = emptyList(),
        sourceUrl = "u",
        sourceHash = SourceHash("last-good"),
        fetchedAt = Instant.now(),
        diagnostics = ImportDiagnostics(
            rawRows = 3, normalizedRows = 3, geocodedRows = 3,
            geocodingComplete = true, rejectedRows = emptyList(), warnings = emptyList()
        )
    )

    private val normDemo = NormalizedDemonstration(
        sourceHash = SourceHash("test"),
        title = "Klima Demo",
        description = null,
        rawText = "klima umwelt",
        date = LocalDate.now(),
        startTime = null,
        endTime = null,
        timeText = null,
        district = null,
        postalCode = null,
        locationText = "Alexanderplatz",
        routeText = null,
        participantCount = null
    )

    @Test
    fun `a failed import restores the last good snapshot`() {
        readModel.replace(goodSnapshot)

        `when`(pageClient.fetchHtml()).thenReturn("<html/>")
        `when`(parser.parse(anyString())).thenReturn(emptyList<RawDemonstrationRow>())
        `when`(normalizer.normalizeAll(anyList())).thenReturn(
            Pair(listOf(normDemo), emptyList<NormalizationResult.Rejected>())
        )
        `when`(parserRunRepository.start(anyString())).thenReturn(ParserRunHandle.NotPersisted)
        // Geocoding blows up mid-import, after the skeleton snapshot has already been published.
        `when`(geocoder.geocode(normDemo)).thenThrow(RuntimeException("nominatim down"))

        val props = AppProperties(
            source = AppProperties.Source("u"),
            importJob = AppProperties.ImportJob("0 0 * * * *"),
            internal = AppProperties.Internal("t"),
            geocoder = AppProperties.Geocoder(baseUrl = "x", userAgent = "x")
        )
        val service = DemonstrationImportService(
            pageClient, parser, normalizer, classifier, geocoder, readModel, parserRunRepository,
            org.springframework.core.task.SyncTaskExecutor(), props
        )

        val result = service.runImport()

        assertTrue(result is ImportResult.Failure, "Import should report failure")
        assertEquals(goodSnapshot, readModel.current(), "Read model must still hold the last good snapshot")
    }
}
