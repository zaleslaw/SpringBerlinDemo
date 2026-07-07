package com.zaleslaw.berlinprotesttracker.config

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * Typed, validated binding for the `app.*` configuration tree. Replaces scattered @Value
 * injection and fails fast at startup (via @Validated) if a required value is missing.
 */
@Validated
@ConfigurationProperties(prefix = "app")
data class AppProperties(
    val source: Source,
    val importJob: ImportJob,
    val internal: Internal,
    val geocoder: Geocoder
) {
    data class Source(
        @field:NotBlank val url: String
    )

    data class ImportJob(
        @field:NotBlank val cron: String
    )

    data class Internal(
        @field:NotBlank val token: String
    )

    data class Geocoder(
        @field:NotBlank val baseUrl: String,
        @field:NotBlank val userAgent: String,
        @field:Positive val minDelayMs: Long = 1100,
        val externalCallsEnabled: Boolean = true,
        val cacheEnabled: Boolean = true
    )
}
