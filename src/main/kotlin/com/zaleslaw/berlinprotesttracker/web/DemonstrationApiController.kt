package com.zaleslaw.berlinprotesttracker.web

import com.zaleslaw.berlinprotesttracker.domain.Demonstration
import com.zaleslaw.berlinprotesttracker.domain.DemonstrationCategory
import com.zaleslaw.berlinprotesttracker.domain.ImpactLevel
import com.zaleslaw.berlinprotesttracker.export.DemonstrationCsvExporter
import com.zaleslaw.berlinprotesttracker.infra.DistrictRepository
import com.zaleslaw.berlinprotesttracker.query.DemonstrationFilter
import com.zaleslaw.berlinprotesttracker.query.DemonstrationQueryService
import com.zaleslaw.berlinprotesttracker.snapshot.DemonstrationReadModel
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate

data class DateRangeResponse(val from: String?, val to: String?)

@RestController
@RequestMapping("/api")
class DemonstrationApiController(
    private val queryService: DemonstrationQueryService,
    private val csvExporter: DemonstrationCsvExporter,
    private val districtRepository: DistrictRepository,
    private val readModel: DemonstrationReadModel
) {

    @GetMapping("/demonstrations")
    fun getDemonstrations(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) dateFrom: LocalDate? = null,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) dateTo: LocalDate? = null,
        @RequestParam district: String? = null,
        @RequestParam category: DemonstrationCategory? = null,
        @RequestParam impactLevel: ImpactLevel? = null,
        @RequestParam @Min(0) @Max(100) minImpactScore: Int? = null
    ): List<Demonstration> {
        requireValidRange(dateFrom, dateTo)
        return queryService.query(
            DemonstrationFilter(
                dateFrom = dateFrom,
                dateTo = dateTo,
                district = district,
                category = category,
                impactLevel = impactLevel,
                minImpactScore = minImpactScore
            )
        )
    }

    @GetMapping("/date-range")
    fun getDateRange(): DateRangeResponse {
        val events = readModel.current()?.events ?: return DateRangeResponse(null, null)
        val dates = events.map { it.date }
        return DateRangeResponse(
            from = dates.minOrNull()?.toString(),
            to = dates.maxOrNull()?.toString()
        )
    }

    @GetMapping("/districts")
    fun getDistricts(): List<String> = districtRepository.findAllNames()

    @GetMapping("/demonstrations.csv", produces = ["text/csv"])
    fun getDemonstrationsCsv(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) dateFrom: LocalDate? = null,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) dateTo: LocalDate? = null,
        @RequestParam district: String? = null,
        @RequestParam category: DemonstrationCategory? = null,
        @RequestParam impactLevel: ImpactLevel? = null,
        @RequestParam @Min(0) @Max(100) minImpactScore: Int? = null
    ): ResponseEntity<String> {
        requireValidRange(dateFrom, dateTo)
        val filter = DemonstrationFilter(
            dateFrom = dateFrom,
            dateTo = dateTo,
            district = district,
            category = category,
            impactLevel = impactLevel,
            minImpactScore = minImpactScore
        )
        val demonstrations = queryService.query(filter)
        val csv = csvExporter.formatCsv(demonstrations)
        val filename = "demonstrations-${LocalDate.now()}.csv"
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
            .body(csv)
    }

    private fun requireValidRange(dateFrom: LocalDate?, dateTo: LocalDate?) {
        if (dateFrom != null && dateTo != null && dateFrom > dateTo) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "dateFrom must not be after dateTo")
        }
    }
}
