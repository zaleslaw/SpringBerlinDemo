package com.zaleslaw.berlinprotesttracker.domain

data class ImpactAssessment(
    val category: DemonstrationCategory,
    val publicLabel: String,
    val level: ImpactLevel,
    val score: ImpactScore,
    val reasons: List<String>,
    val evidence: List<String>
)
