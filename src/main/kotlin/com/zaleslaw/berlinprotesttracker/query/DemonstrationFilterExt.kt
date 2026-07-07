package com.zaleslaw.berlinprotesttracker.query

import com.zaleslaw.berlinprotesttracker.domain.Demonstration

/**
 * The single place that knows how a [DemonstrationFilter] narrows a set of events.
 * Shared by the query, timeline, and GeoJSON services so the predicate list lives in one spot.
 */
fun Sequence<Demonstration>.applyFilter(f: DemonstrationFilter): Sequence<Demonstration> =
    filter { f.dateFrom == null || it.date >= f.dateFrom }
        .filter { f.dateTo == null || it.date <= f.dateTo }
        .filter { f.district == null || it.district == f.district }
        .filter { f.category == null || it.category == f.category }
        .filter { f.impactLevel == null || it.impact.level == f.impactLevel }
        .filter { f.minImpactScore == null || it.impact.score.value >= f.minImpactScore }
