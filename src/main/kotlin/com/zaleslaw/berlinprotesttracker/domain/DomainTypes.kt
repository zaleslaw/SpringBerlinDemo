package com.zaleslaw.berlinprotesttracker.domain

/**
 * Kotlin value classes give us stronger types without usually paying for wrapper objects at runtime.
 * A demonstration id and a source hash may both be strings physically,
 * but they are different concepts for the compiler.
 * That means fewer accidental mix-ups and more readable method signatures.
 */

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
