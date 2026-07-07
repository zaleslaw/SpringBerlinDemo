package com.zaleslaw.berlinprotesttracker.domain

@JvmInline
value class DemonstrationId(val value: String)

@JvmInline
value class SourceHash(val value: String)

@JvmInline
value class ImpactScore(val value: Int) {
    init {
        require(value in 0..100) { "Impact score must be in 0..100, got $value" }
    }
}
