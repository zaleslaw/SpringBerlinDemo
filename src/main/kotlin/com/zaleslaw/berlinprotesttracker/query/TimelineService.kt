package com.zaleslaw.berlinprotesttracker.query

import com.zaleslaw.berlinprotesttracker.domain.Demonstration
import com.zaleslaw.berlinprotesttracker.domain.DemonstrationCategory
import com.zaleslaw.berlinprotesttracker.domain.EventGeometry
import com.zaleslaw.berlinprotesttracker.domain.ImpactLevel
import com.zaleslaw.berlinprotesttracker.snapshot.DemonstrationReadModel
import org.springframework.stereotype.Service
import java.time.LocalDate

data class TimelineItem(
    val id: String,
    val title: String,
    val date: String,
    val timeText: String?,
    val district: String?,
    val locationText: String?,
    val category: String,
    val impactLevel: String,
    val impactScore: Int,
    val reasons: List<String>,
    val participantCount: Int?,
    val geocoded: Boolean,
    val geometryType: String
)

@Service
class TimelineService(private val readModel: DemonstrationReadModel) {

    fun getTimeline(
        dateFrom: LocalDate? = null, dateTo: LocalDate? = null, district: String? = null,
        category: DemonstrationCategory? = null, impactLevel: ImpactLevel? = null
    ): List<TimelineItem> {
        val snapshot = readModel.current() ?: return emptyList()

        val filter = DemonstrationFilter(dateFrom, dateTo, district, category, impactLevel)
        return snapshot.events
            .asSequence()
            .applyFilter(filter)
            .sortedWith(compareBy<Demonstration> { it.date }.thenBy { it.startTime })
            .map { it.toTimelineItem() }
            .toList()
    }

    private fun Demonstration.toTimelineItem() = TimelineItem(
        id = id.value,
        title = title,
        date = date.toString(),
        timeText = timeText,
        district = district,
        locationText = locationText,
        category = category.name,
        impactLevel = impact.level.name,
        impactScore = impact.score.value,
        reasons = impact.reasons,
        participantCount = participantCount,
        geocoded = geometry !is EventGeometry.Unknown,
        geometryType = when (geometry) {
            is EventGeometry.Point -> "Point"
            EventGeometry.Unknown -> "Unknown"
        }
    )
}
