package com.zaleslaw.berlinprotesttracker.domain

sealed interface EventGeometry {
    data class Point(
        val lat: Double,
        val lon: Double,
        val confidence: Double
    ) : EventGeometry

    data object Unknown : EventGeometry
}
