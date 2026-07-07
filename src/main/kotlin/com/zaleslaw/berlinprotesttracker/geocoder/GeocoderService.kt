package com.zaleslaw.berlinprotesttracker.geocoder

import com.zaleslaw.berlinprotesttracker.domain.EventGeometry
import com.zaleslaw.berlinprotesttracker.domain.NormalizedDemonstration
import com.zaleslaw.berlinprotesttracker.config.AppProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class GeocoderService(
    private val nominatimClient: NominatimClient,
    private val cache: GeocodeCacheRepository,
    private val plzCentroids: PlzCentroidService,
    props: AppProperties
) {

    private val cacheEnabled = props.geocoder.cacheEnabled
    private val externalEnabled = props.geocoder.externalCallsEnabled

    private val log = LoggerFactory.getLogger(javaClass)

    private val cacheHits   = java.util.concurrent.atomic.AtomicLong(0)
    private val cacheMisses = java.util.concurrent.atomic.AtomicLong(0)

    fun resetCacheStats() { cacheHits.set(0); cacheMisses.set(0) }
    fun logCacheStats() {
        val hits   = cacheHits.get()
        val misses = cacheMisses.get()
        val total  = hits + misses
        val missRate = if (total > 0) misses * 100 / total else 0
        log.info("Geocode cache stats: {} hits, {} Nominatim calls ({}% miss rate)", hits, misses, missRate)
    }

    // Reject strings that are clearly not addresses (time patterns, bare PLZ, long descriptions)
    private val notAnAddress = Regex("""^\d{2}:\d{2}|^\d{5}$|^.{150,}""", RegexOption.DOT_MATCHES_ALL)

    private val abbreviations = listOf(
        // Berlin OSM format: "S Treptower Park", "U Hermannplatz" (no dash, no full word)
        Regex("""(?i)\bS-Bhf\.\s*""")  to "S ",
        Regex("""(?i)\bU-Bhf\.\s*""")  to "U ",
        Regex("""(?i)\bBhf\.\s*""")    to "Bahnhof ",
        Regex("""(?i)\bBf\.\s*""")     to "Bahnhof ",
        Regex("""(?i)\bStr\.\b""")     to "Straße",
        Regex("""(?i)\bPl\.\b""")      to "Platz"
    )

    private fun expandAbbreviations(query: String): String =
        abbreviations.fold(query) { acc, (pattern, replacement) -> pattern.replace(acc, replacement) }.trim()

    fun geocode(demo: NormalizedDemonstration): Pair<EventGeometry, List<String>> {
        val warnings = mutableListOf<String>()

        val locationText = demo.locationText

        if (!locationText.isNullOrBlank() && !notAnAddress.containsMatchIn(locationText)) {
            val query = if (!demo.postalCode.isNullOrBlank() && !locationText.contains(demo.postalCode))
                "$locationText, ${demo.postalCode}"
            else
                locationText
            val geometry = geocodeAddress(query, warnings)
            if (geometry !is EventGeometry.Unknown) return geometry to warnings
        }

        // PLZ centroid fallback — at least place event at postal code center
        val plzGeometry = demo.postalCode?.let { plzCentroids.lookup(it) }
        if (plzGeometry != null) {
            warnings += "Geocoding failed, using PLZ centroid for ${demo.postalCode}"
            return plzGeometry to warnings
        }

        return EventGeometry.Unknown to warnings
    }

    private fun geocodeAddress(address: String, warnings: MutableList<String>): EventGeometry {
        val result = lookupWithCache(address, warnings) ?: return EventGeometry.Unknown
        return EventGeometry.Point(result.lat, result.lon, result.confidence)
    }

    private fun lookupWithCache(rawQuery: String, warnings: MutableList<String>): GeocodeResult? {
        val query = expandAbbreviations(rawQuery)
        if (cacheEnabled) {
            val cached = cache.findByQuery(query)
            if (cached != null) {
                cacheHits.incrementAndGet()
                if (cached.confidence < 0) {
                    log.debug("Cache hit (not-found): '{}'", query)
                    return null
                }
                log.debug("Cache hit: '{}'", query)
                return cached
            }
        }

        if (!externalEnabled) {
            log.debug("External geocoding disabled, skipping '{}'", query)
            return null
        }

        cacheMisses.incrementAndGet()
        log.info("Nominatim lookup: '{}'", query)
        val result = nominatimClient.geocode(query)
        if (result == null) {
            warnings += "Could not geocode: $query"
            if (cacheEnabled) {
                cache.save(query, NOT_FOUND_SENTINEL)
            }
            return null
        }

        if (cacheEnabled) {
            cache.save(query, result)
        }
        return result
    }

    companion object {
        // confidence < 0 marks a cached "not found" — prevents re-querying Nominatim on next import
        private val NOT_FOUND_SENTINEL = GeocodeResult(lat = 0.0, lon = 0.0, confidence = -1.0, provider = "none")
    }
}
