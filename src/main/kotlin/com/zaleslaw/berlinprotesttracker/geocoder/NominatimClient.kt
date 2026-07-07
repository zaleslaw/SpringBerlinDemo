package com.zaleslaw.berlinprotesttracker.geocoder

import com.zaleslaw.berlinprotesttracker.config.AppProperties
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

@Component
class NominatimClient(
    private val mapper: ObjectMapper,
    restClientBuilder: RestClient.Builder,
    props: AppProperties
) {

    private val minDelayMs = props.geocoder.minDelayMs
    private val log = LoggerFactory.getLogger(javaClass)

    private val restClient = restClientBuilder
        .baseUrl(props.geocoder.baseUrl)
        .defaultHeader(HttpHeaders.USER_AGENT, props.geocoder.userAgent)
        .build()

    // Nominatim's usage policy is max 1 request/second. Serialize the rate-limit + request so
    // concurrent callers can never bypass the delay, and lastCallMs is only ever touched here.
    private val rateLimitLock = Any()
    private var lastCallMs = 0L

    fun geocode(query: String, city: String = "Berlin"): GeocodeResult? = synchronized(rateLimitLock) {
        respectRateLimit()
        val fullQuery = "$query, $city, Germany"

        try {
            val json = restClient.get()
                .uri { b ->
                    b.path("/search")
                        .queryParam("q", fullQuery)
                        .queryParam("format", "json")
                        .queryParam("limit", "1")
                        .queryParam("countrycodes", "de")
                        .build()
                }
                .retrieve()
                .body(String::class.java)
            lastCallMs = System.currentTimeMillis()

            if (json.isNullOrBlank()) {
                log.debug("Nominatim returned an empty body for '{}'", query)
                return@synchronized null
            }

            val body: JsonNode = mapper.readTree(json)
            if (!body.isArray || body.isEmpty) {
                log.debug("Nominatim returned no results for '{}'", query)
                return@synchronized null
            }

            val first = body[0]
            GeocodeResult(
                lat = first["lat"].asDouble(),
                lon = first["lon"].asDouble(),
                confidence = 0.8,
                provider = "nominatim"
            )
        } catch (e: Exception) {
            log.warn("Nominatim request failed for '{}': {}", query, e.message)
            null
        }
    }

    private fun respectRateLimit() {
        val elapsed = System.currentTimeMillis() - lastCallMs
        if (elapsed < minDelayMs) {
            Thread.sleep(minDelayMs - elapsed)
        }
    }
}
