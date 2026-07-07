package com.zaleslaw.berlinprotesttracker.web

import com.zaleslaw.berlinprotesttracker.domain.DemonstrationCategory
import com.zaleslaw.berlinprotesttracker.domain.ImpactLevel
import com.zaleslaw.berlinprotesttracker.query.TimelineItem
import com.zaleslaw.berlinprotesttracker.query.TimelineService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api")
class TimelineController(private val timelineService: TimelineService) {

    @GetMapping("/timeline")
    fun getTimeline(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) dateFrom: LocalDate? = null,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) dateTo: LocalDate? = null,
        @RequestParam district: String? = null,
        @RequestParam category: DemonstrationCategory? = null,
        @RequestParam impactLevel: ImpactLevel? = null
    ): List<TimelineItem> = timelineService.getTimeline(dateFrom, dateTo, district, category, impactLevel)
}
