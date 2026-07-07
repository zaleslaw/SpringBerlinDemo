package com.zaleslaw.berlinprotesttracker.web

import com.zaleslaw.berlinprotesttracker.domain.DemonstrationCategory
import com.zaleslaw.berlinprotesttracker.domain.ImpactLevel
import com.zaleslaw.berlinprotesttracker.query.GeoJsonService
import com.zaleslaw.berlinprotesttracker.snapshot.DemonstrationReadModel
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api")
class GeoJsonController(
    private val geoJsonService: GeoJsonService,
    private val readModel: DemonstrationReadModel
) {

    @GetMapping("/demonstrations.geojson", produces = ["application/geo+json", "application/json"])
    fun getDemonstrationsGeoJson(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) dateFrom: LocalDate? = null,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) dateTo: LocalDate? = null,
        @RequestParam district: String? = null,
        @RequestParam category: DemonstrationCategory? = null,
        @RequestParam impactLevel: ImpactLevel? = null
    ): Map<String, Any> = geoJsonService.getDemonstrationsGeoJson(dateFrom, dateTo, district, category, impactLevel)

    @GetMapping("/demonstrations.impact-zones.geojson", produces = ["application/geo+json", "application/json"])
    fun getImpactZonesGeoJson(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) dateFrom: LocalDate? = null,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) dateTo: LocalDate? = null,
        @RequestParam district: String? = null,
        @RequestParam category: DemonstrationCategory? = null,
        @RequestParam impactLevel: ImpactLevel? = null
    ): Map<String, Any> = geoJsonService.getImpactZonesGeoJson(dateFrom, dateTo, district, category, impactLevel)

    @GetMapping("/demonstrations.plz-heatmap.geojson", produces = ["application/geo+json", "application/json"])
    fun getPlzHeatmapGeoJson(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) dateFrom: LocalDate? = null,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) dateTo: LocalDate? = null,
        @RequestParam district: String? = null,
        @RequestParam category: DemonstrationCategory? = null,
        @RequestParam impactLevel: ImpactLevel? = null
    ): Map<String, Any> = geoJsonService.getPlzHeatmapGeoJson(dateFrom, dateTo, district, category, impactLevel)

    @GetMapping("/snapshot/status")
    fun getSnapshotStatus(): SnapshotStatusResponse {
        val snapshot = readModel.current() ?: return SnapshotStatusResponse(loaded = false)
        val d = snapshot.diagnostics
        return SnapshotStatusResponse(
            loaded = true,
            sourceUrl = snapshot.sourceUrl,
            fetchedAt = snapshot.fetchedAt.toString(),
            eventCount = snapshot.events.size,
            rawRows = d.rawRows,
            normalizedRows = d.normalizedRows,
            geocodedRows = d.geocodedRows,
            geocodingComplete = d.geocodingComplete,
            rejectedRows = d.rejectedRows.size,
            warnings = d.warnings
        )
    }
}

data class SnapshotStatusResponse(
    val loaded: Boolean,
    val sourceUrl: String? = null,
    val fetchedAt: String? = null,
    val eventCount: Int? = null,
    val rawRows: Int? = null,
    val normalizedRows: Int? = null,
    val geocodedRows: Int? = null,
    val geocodingComplete: Boolean? = null,
    val rejectedRows: Int? = null,
    val warnings: List<String>? = null
)
