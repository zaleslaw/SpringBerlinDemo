package com.zaleslaw.berlinprotesttracker.normalizer

import com.zaleslaw.berlinprotesttracker.domain.NormalizedDemonstration
import com.zaleslaw.berlinprotesttracker.parser.RawDemonstrationRow
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

sealed interface NormalizationResult {
    data class Success(val value: NormalizedDemonstration) : NormalizationResult
    data class Rejected(val row: RawDemonstrationRow, val reason: String) : NormalizationResult
}

@Component
class DemonstrationNormalizer {

    private val log = LoggerFactory.getLogger(javaClass)

    fun normalize(row: RawDemonstrationRow): NormalizationResult {
        val title = TextCleaning.clean(row.titleRaw)
            ?: TextCleaning.clean(row.rawText)?.take(120)
            ?: return NormalizationResult.Rejected(row, "Missing title and raw text is blank")

        val date = DateTextParser.parse(row.dateRaw)
            ?: return NormalizationResult.Rejected(row, "Could not parse date: '${row.dateRaw}'")

        val rawText = TextCleaning.cleanRequired(row.rawText)
        val timeRange = TimeRangeParser.parse(row.timeRaw)
        val postalCode = DistrictNormalizer.extractPostalCode(rawText)
        val district = DistrictNormalizer.extractDistrictFromPlz(postalCode)
            ?: DistrictNormalizer.extractDistrict(row.districtRaw, rawText)
        val participantCount = DistrictNormalizer.extractParticipantCount(row.participantCountRaw)
        val sourceHash = TextCleaning.sourceHash(row.dateRaw, title, row.locationRaw, row.districtRaw)

        return NormalizationResult.Success(
            NormalizedDemonstration(
                sourceHash = sourceHash,
                title = title,
                description = null,
                rawText = rawText,
                date = date,
                startTime = timeRange.startTime,
                endTime = timeRange.endTime,
                timeText = timeRange.timeText,
                district = district,
                postalCode = postalCode,
                locationText = TextCleaning.clean(row.locationRaw),
                routeText = TextCleaning.clean(row.routeRaw),
                participantCount = participantCount
            )
        )
    }

    fun normalizeAll(rows: List<RawDemonstrationRow>): Pair<List<NormalizedDemonstration>, List<NormalizationResult.Rejected>> {
        val successes = mutableListOf<NormalizedDemonstration>()
        val rejected = mutableListOf<NormalizationResult.Rejected>()

        for (row in rows) {
            when (val result = normalize(row)) {
                is NormalizationResult.Success -> successes += result.value
                is NormalizationResult.Rejected -> {
                    rejected += result
                    log.warn("Row {} rejected: {}", row.rowIndex, result.reason)
                }
            }
        }
        log.info("Rows normalized: {}, rejected: {}", successes.size, rejected.size)
        return successes to rejected
    }
}
