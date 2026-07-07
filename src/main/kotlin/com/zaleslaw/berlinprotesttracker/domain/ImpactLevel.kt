package com.zaleslaw.berlinprotesttracker.domain

enum class ImpactLevel {
    LOW,
    MEDIUM,
    HIGH,
    VERY_HIGH;

    companion object {
        fun fromScore(score: Int): ImpactLevel = when (score) {
            in 0..19 -> LOW
            in 20..39 -> MEDIUM
            in 40..59 -> HIGH
            else -> VERY_HIGH
        }
    }
}
