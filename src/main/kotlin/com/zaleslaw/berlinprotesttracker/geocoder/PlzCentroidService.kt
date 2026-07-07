package com.zaleslaw.berlinprotesttracker.geocoder

import com.zaleslaw.berlinprotesttracker.domain.EventGeometry
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper

@Service
class PlzCentroidService(private val mapper: ObjectMapper) {

    private val log = LoggerFactory.getLogger(javaClass)

    // plz -> (lat, lon)
    private val centroids = mutableMapOf<String, Pair<Double, Double>>()

    @PostConstruct
    fun load() {
        try {
            val resource = ClassPathResource("static/berlin-plz-centroids.geojson")
            val root = mapper.readTree(resource.inputStream)
            root["features"].forEach { f ->
                val plz = f["properties"]["plz"]?.asText() ?: return@forEach
                val coords = f["geometry"]["coordinates"]
                val lon = coords[0].asDouble()
                val lat = coords[1].asDouble()
                centroids[plz] = lat to lon
            }
            log.info("Loaded {} PLZ centroids", centroids.size)
        } catch (e: Exception) {
            log.warn("Could not load PLZ centroids: {}", e.message)
        }
    }

    fun lookup(plz: String): EventGeometry.Point? {
        val (lat, lon) = centroids[plz] ?: return null
        return EventGeometry.Point(lat, lon, confidence = 0.3)
    }
}
