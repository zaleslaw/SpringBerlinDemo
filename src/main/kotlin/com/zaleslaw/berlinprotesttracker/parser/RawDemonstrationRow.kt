package com.zaleslaw.berlinprotesttracker.parser

data class RawDemonstrationRow(
    val rowIndex: Int,
    val rawText: String,
    val titleRaw: String?,
    val dateRaw: String?,
    val timeRaw: String?,
    val locationRaw: String?,
    val routeRaw: String?,
    val districtRaw: String?,
    val participantCountRaw: String?
)
