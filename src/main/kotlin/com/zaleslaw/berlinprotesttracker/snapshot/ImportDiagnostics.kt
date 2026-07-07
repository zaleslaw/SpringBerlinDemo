package com.zaleslaw.berlinprotesttracker.snapshot

data class ImportDiagnostics(
    val rawRows: Int,
    val normalizedRows: Int,
    val geocodedRows: Int,
    val geocodingComplete: Boolean = false,
    val rejectedRows: List<RejectedRow>,
    val warnings: List<String>
)

data class RejectedRow(
    val rowIndex: Int,
    val reason: String,
    val rawText: String
)
