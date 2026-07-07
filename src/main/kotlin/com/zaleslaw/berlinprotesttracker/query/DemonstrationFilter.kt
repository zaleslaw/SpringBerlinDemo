package com.zaleslaw.berlinprotesttracker.query

import com.zaleslaw.berlinprotesttracker.domain.DemonstrationCategory
import com.zaleslaw.berlinprotesttracker.domain.ImpactLevel
import java.time.LocalDate

data class DemonstrationFilter(
    val dateFrom: LocalDate? = null,
    val dateTo: LocalDate? = null,
    val district: String? = null,
    val category: DemonstrationCategory? = null,
    val impactLevel: ImpactLevel? = null,
    val minImpactScore: Int? = null
)
