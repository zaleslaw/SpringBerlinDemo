package com.zaleslaw.berlinprotesttracker.snapshot

import com.zaleslaw.berlinprotesttracker.domain.Demonstration
import com.zaleslaw.berlinprotesttracker.domain.SourceHash
import java.time.Instant

data class DemonstrationSnapshot(
    val events: List<Demonstration>,
    val sourceUrl: String,
    val sourceHash: SourceHash,
    val fetchedAt: Instant,
    val diagnostics: ImportDiagnostics
)
