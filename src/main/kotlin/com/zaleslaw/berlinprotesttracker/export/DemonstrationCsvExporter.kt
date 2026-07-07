package com.zaleslaw.berlinprotesttracker.export

import com.zaleslaw.berlinprotesttracker.domain.Demonstration
import com.zaleslaw.berlinprotesttracker.domain.EventGeometry
import org.springframework.stereotype.Service

@Service
class DemonstrationCsvExporter {

    fun formatCsv(demonstrations: List<Demonstration>): String {
        val sb = StringBuilder()
        sb.append("id,title,date,startTime,endTime,timeText,district,postalCode,locationText,category,impactLevel,impactScore,participantCount,lat,lon,geocodingConfidence,routeText,reasons\n")

        for (demo in demonstrations) {
            val (lat, lon, confidence) = when (val geo = demo.geometry) {
                is EventGeometry.Point -> Triple(geo.lat.toString(), geo.lon.toString(), geo.confidence.toString())
                is EventGeometry.Unknown -> Triple("", "", "")
            }

            val row = listOf(
                demo.id.value,
                demo.title,
                demo.date.toString(),
                demo.startTime?.toString() ?: "",
                demo.endTime?.toString() ?: "",
                demo.timeText ?: "",
                demo.district ?: "",
                demo.postalCode ?: "",
                demo.locationText ?: "",
                demo.category.name,
                demo.impact.level.name,
                demo.impact.score.value.toString(),
                demo.participantCount?.toString() ?: "",
                lat,
                lon,
                confidence,
                demo.routeText ?: "",
                demo.impact.reasons.joinToString("; ")
            ).map { escapeCsvField(it) }

            sb.append(row.joinToString(",")).append("\n")
        }

        return sb.toString()
    }

    private fun escapeCsvField(field: String): String {
        if (field.isEmpty()) return "\"\""
        val needsQuoting = field.contains(',') || field.contains('"') || field.contains('\n') || field.contains('\r')
        return if (needsQuoting) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }
    }
}
