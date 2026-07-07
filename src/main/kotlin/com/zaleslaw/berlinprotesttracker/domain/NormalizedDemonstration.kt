package com.zaleslaw.berlinprotesttracker.domain

import java.time.LocalDate
import java.time.LocalTime

data class NormalizedDemonstration(
    val sourceHash: SourceHash,
    val title: String,
    val description: String?,
    val rawText: String,
    val date: LocalDate,
    val startTime: LocalTime?,
    val endTime: LocalTime?,
    val timeText: String?,
    val district: String?,
    val postalCode: String?,
    val locationText: String?,
    val routeText: String?,
    val participantCount: Int?
)
