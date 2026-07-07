package com.zaleslaw.berlinprotesttracker.geocoder

data class GeocodeResult(
    val lat: Double,
    val lon: Double,
    val confidence: Double,
    val provider: String
)
