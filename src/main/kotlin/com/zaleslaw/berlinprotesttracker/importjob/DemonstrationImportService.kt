package com.zaleslaw.berlinprotesttracker.importjob

import com.zaleslaw.berlinprotesttracker.classifier.ImpactClassifier
import com.zaleslaw.berlinprotesttracker.config.AppProperties
import com.zaleslaw.berlinprotesttracker.domain.Demonstration
import com.zaleslaw.berlinprotesttracker.domain.DemonstrationId
import com.zaleslaw.berlinprotesttracker.domain.EventGeometry
import com.zaleslaw.berlinprotesttracker.domain.SourceHash
import com.zaleslaw.berlinprotesttracker.domain.NormalizedDemonstration
import com.zaleslaw.berlinprotesttracker.geocoder.GeocoderService
import com.zaleslaw.berlinprotesttracker.infra.ParserRunRepository
import com.zaleslaw.berlinprotesttracker.normalizer.DemonstrationNormalizer
import com.zaleslaw.berlinprotesttracker.parser.BerlinPolicePageClient
import com.zaleslaw.berlinprotesttracker.parser.BerlinPolicePageParser
import com.zaleslaw.berlinprotesttracker.snapshot.DemonstrationReadModel
import com.zaleslaw.berlinprotesttracker.snapshot.DemonstrationSnapshot
import com.zaleslaw.berlinprotesttracker.snapshot.ImportDiagnostics
import com.zaleslaw.berlinprotesttracker.snapshot.RejectedRow
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.task.TaskExecutor
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

private const val GEOCODE_BATCH_SIZE = 50

@Service
class DemonstrationImportService(
    private val pageClient: BerlinPolicePageClient,
    private val parser: BerlinPolicePageParser,
    private val normalizer: DemonstrationNormalizer,
    private val classifier: ImpactClassifier,
    private val geocoder: GeocoderService,
    private val readModel: DemonstrationReadModel,
    private val parserRunRepository: ParserRunRepository,
    @Qualifier("importExecutor") private val importExecutor: TaskExecutor,
    props: AppProperties
) {

    private val sourceUrl = props.source.url
    private val log = LoggerFactory.getLogger(javaClass)
    private val running = AtomicBoolean(false)

    /**
     * Claims the run guard synchronously and returns whether *this* call started the import, so the
     * caller can honestly answer 202 vs 409 with no check-then-act race. The work runs on the
     * import executor (called directly — no @Async self-invocation concern).
     */
    fun tryStartImportAsync(): Boolean {
        if (!running.compareAndSet(false, true)) return false
        importExecutor.execute {
            try {
                doImport()
            } finally {
                running.set(false)
            }
        }
        return true
    }

    fun runImport(): ImportResult {
        if (!running.compareAndSet(false, true)) {
            log.info("Import already running, skipping")
            return ImportResult.AlreadyRunning
        }
        try {
            return doImport()
        } finally {
            running.set(false)
        }
    }

    private fun doImport(): ImportResult {
        val importStart = System.currentTimeMillis()
        fun elapsed() = System.currentTimeMillis() - importStart

        log.info("=== Import started ===")
        val handle = parserRunRepository.start(sourceUrl)

        // Remember the last good snapshot so a mid-import failure never leaves the read model
        // holding a partial/un-geocoded result (progressive publishing overwrites it below).
        val previousSnapshot = readModel.current()

        return try {
            log.info("Phase 1/4: Fetching source HTML from {}", sourceUrl)
            val html = pageClient.fetchHtml()
            val pageHash = sha256(html).take(16)
            log.info("Phase 1/4: Fetch complete ({} chars, hash={}) [{}ms]", html.length, pageHash, elapsed())

            log.info("Phase 2/4: Parsing HTML table rows")
            val rawRows = parser.parse(html)
            log.info("Phase 2/4: Parse complete — {} rows [{}ms]", rawRows.size, elapsed())

            log.info("Phase 3/4: Normalizing {} rows", rawRows.size)
            val (normalized, rejected) = normalizer.normalizeAll(rawRows)
            log.info("Phase 3/4: Normalize complete — {} ok, {} rejected [{}ms]", normalized.size, rejected.size, elapsed())

            if (normalized.isEmpty() && rawRows.isNotEmpty()) {
                parserRunRepository.fail(handle, "All rows rejected during normalization")
                return ImportResult.Failure("All rows were rejected during normalization")
            }

            val rejectedRows = rejected.map { RejectedRow(it.row.rowIndex, it.reason, it.row.rawText) }

            // Phase 4a: classify all events immediately, publish skeleton snapshot with Unknown geometry
            val demonstrations = normalized.mapIndexed { i, norm ->
                val impact = classifier.classify(norm)
                buildDemonstration(pageHash, i, norm, impact, EventGeometry.Unknown)
            }.toMutableList()

            readModel.replace(buildSnapshot(pageHash, rawRows.size, normalized.size, 0, rejectedRows, emptyList(), demonstrations))
            log.info("Phase 4/4: Skeleton snapshot published: {} events [{}ms]", demonstrations.size, elapsed())

            // Phase 4b: geocode progressively, publish every GEOCODE_BATCH_SIZE events
            log.info("Phase 4/4: Geocoding {} events (batch size {})", normalized.size, GEOCODE_BATCH_SIZE)
            geocoder.resetCacheStats()
            val warnings = mutableListOf<String>()
            var geocodedCount = 0

            for ((i, norm) in normalized.withIndex()) {
                val (geometry, geoWarnings) = geocoder.geocode(norm)
                warnings.addAll(geoWarnings)
                if (geometry !is EventGeometry.Unknown) {
                    geocodedCount++
                    demonstrations[i] = demonstrations[i].copy(geometry = geometry)
                }

                if ((i + 1) % GEOCODE_BATCH_SIZE == 0) {
                    readModel.replace(buildSnapshot(pageHash, rawRows.size, normalized.size, geocodedCount, rejectedRows, warnings, demonstrations))
                    log.info("Geocoding progress: {}/{} events, geocoded: {} [{}ms]", i + 1, normalized.size, geocodedCount, elapsed())
                }
            }

            log.info("Phase 4/4: Geocoding complete — {}/{} geocoded [{}ms]", geocodedCount, normalized.size, elapsed())
            geocoder.logCacheStats()

            val finalSnapshot = buildSnapshot(pageHash, rawRows.size, normalized.size, geocodedCount, rejectedRows, warnings, demonstrations, geocodingComplete = true)
            readModel.replace(finalSnapshot)
            parserRunRepository.complete(handle, rawRows.size, normalized.size, geocodedCount, demonstrations.size, pageHash)
            log.info("=== Import finished: {} events, {}/{} geocoded, total time {}ms ===",
                demonstrations.size, geocodedCount, normalized.size, elapsed())
            ImportResult.Success(demonstrations.size)

        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            log.error("Import failed after {}ms: {}", elapsed(), msg, e)
            // Roll back to the last successful snapshot — a failed import must not degrade the map.
            if (previousSnapshot != null) {
                readModel.replace(previousSnapshot)
                log.info("Restored previous snapshot ({} events) after failed import", previousSnapshot.events.size)
            }
            parserRunRepository.fail(handle, msg)
            ImportResult.Failure(msg)
        }
    }

    private fun buildDemonstration(
        pageHash: String, index: Int, norm: NormalizedDemonstration,
        impact: com.zaleslaw.berlinprotesttracker.domain.ImpactAssessment,
        geometry: EventGeometry
    ) = Demonstration(
        id = DemonstrationId("${pageHash}-${index}"),
        sourceHash = norm.sourceHash,
        title = norm.title,
        description = norm.description,
        rawText = norm.rawText,
        date = norm.date,
        startTime = norm.startTime,
        endTime = norm.endTime,
        timeText = norm.timeText,
        district = norm.district,
        postalCode = norm.postalCode,
        locationText = norm.locationText,
        routeText = norm.routeText,
        participantCount = norm.participantCount,
        category = impact.category,
        impact = impact,
        geometry = geometry
    )

    private fun buildSnapshot(
        pageHash: String, rawCount: Int, normalizedCount: Int, geocodedCount: Int,
        rejectedRows: List<RejectedRow>, warnings: List<String>, events: List<Demonstration>,
        geocodingComplete: Boolean = false
    ) = DemonstrationSnapshot(
        events = events.toList(),
        sourceUrl = sourceUrl,
        sourceHash = SourceHash(pageHash),
        fetchedAt = Instant.now(),
        diagnostics = ImportDiagnostics(
            rawRows = rawCount,
            normalizedRows = normalizedCount,
            geocodedRows = geocodedCount,
            geocodingComplete = geocodingComplete,
            rejectedRows = rejectedRows,
            warnings = warnings.toList()
        )
    )

    private fun sha256(text: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
