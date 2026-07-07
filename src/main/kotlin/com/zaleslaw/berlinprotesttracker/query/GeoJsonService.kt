package com.zaleslaw.berlinprotesttracker.query

import com.zaleslaw.berlinprotesttracker.domain.Demonstration
import com.zaleslaw.berlinprotesttracker.domain.DemonstrationCategory
import com.zaleslaw.berlinprotesttracker.domain.EventGeometry
import com.zaleslaw.berlinprotesttracker.domain.ImpactLevel
import com.zaleslaw.berlinprotesttracker.snapshot.DemonstrationReadModel
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.time.LocalDate

@Service
class GeoJsonService(
    private val readModel: DemonstrationReadModel,
    private val mapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private lateinit var plzFeatureNodes: List<JsonNode>

    @PostConstruct
    fun loadPlzPolygons() {
        try {
            val resource = ClassPathResource("static/berlin_plz.geojson")
            val root = mapper.readTree(resource.inputStream)
            plzFeatureNodes = root["features"].toList()
            log.info("Loaded {} PLZ polygon features", plzFeatureNodes.size)
        } catch (e: Exception) {
            log.warn("Could not load PLZ polygons: {}", e.message)
            plzFeatureNodes = emptyList()
        }
    }

    private fun filteredEvents(
        dateFrom: LocalDate?, dateTo: LocalDate?,
        district: String?, category: DemonstrationCategory?, impactLevel: ImpactLevel?
    ): List<Demonstration> {
        val events = readModel.current()?.events ?: return emptyList()
        val filter = DemonstrationFilter(dateFrom, dateTo, district, category, impactLevel)
        return events.asSequence().applyFilter(filter).toList()
    }

    fun getDemonstrationsGeoJson(
        dateFrom: LocalDate? = null, dateTo: LocalDate? = null, district: String? = null,
        category: DemonstrationCategory? = null, impactLevel: ImpactLevel? = null
    ): Map<String, Any> {
        val events = filteredEvents(dateFrom, dateTo, district, category, impactLevel)

        val features = events.mapNotNull { demo ->
            when (val g = demo.geometry) {
                is EventGeometry.Point -> mapOf(
                    "type" to "Feature",
                    "id" to demo.id.value,
                    "geometry" to mapOf(
                        "type" to "Point",
                        "coordinates" to listOf(g.lon, g.lat)
                    ),
                    "properties" to featureProperties(demo)
                )
                EventGeometry.Unknown -> null
            }
        }

        return mapOf("type" to "FeatureCollection", "features" to features)
    }

    fun getImpactZonesGeoJson(
        dateFrom: LocalDate? = null, dateTo: LocalDate? = null, district: String? = null,
        category: DemonstrationCategory? = null, impactLevel: ImpactLevel? = null
    ): Map<String, Any> {
        val events = filteredEvents(dateFrom, dateTo, district, category, impactLevel)

        val features = events.mapNotNull { demo ->
            val center = when (val g = demo.geometry) {
                is EventGeometry.Point -> listOf(g.lon, g.lat)
                EventGeometry.Unknown -> return@mapNotNull null
            }

            val radius = impactRadiusKm(demo.impact.level)
            val circle = approximateCircle(center[1], center[0], radius)

            mapOf(
                "type" to "Feature",
                "id" to "${demo.id.value}-zone",
                "geometry" to mapOf("type" to "Polygon", "coordinates" to listOf(circle)),
                "properties" to mapOf(
                    "id" to demo.id.value,
                    "title" to demo.title,
                    "impactLevel" to demo.impact.level.name,
                    "impactScore" to demo.impact.score.value,
                    "category" to demo.category.name
                )
            )
        }

        return mapOf("type" to "FeatureCollection", "features" to features)
    }

    fun getPlzHeatmapGeoJson(
        dateFrom: LocalDate? = null, dateTo: LocalDate? = null, district: String? = null,
        category: DemonstrationCategory? = null, impactLevel: ImpactLevel? = null
    ): Map<String, Any> {
        val events = filteredEvents(dateFrom, dateTo, district, category, impactLevel)
        val counts = events.filter { it.postalCode != null }
            .groupingBy { it.postalCode!! }
            .eachCount()

        val features = plzFeatureNodes.map { node ->
            @Suppress("UNCHECKED_CAST")
            val featureMap = mapper.convertValue(node, MutableMap::class.java) as MutableMap<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val props = (featureMap["properties"] as? MutableMap<String, Any?>) ?: mutableMapOf()
            val plz = props["plz"]?.toString()
            props["eventCount"] = counts[plz] ?: 0
            featureMap["properties"] = props
            featureMap
        }
        return mapOf("type" to "FeatureCollection", "features" to features)
    }

    private fun featureProperties(demo: com.zaleslaw.berlinprotesttracker.domain.Demonstration) = mapOf(
        "id" to demo.id.value,
        "title" to demo.title,
        "date" to demo.date.toString(),
        "timeText" to (demo.timeText ?: ""),
        "district" to (demo.district ?: ""),
        "locationText" to (demo.locationText ?: ""),
        "category" to demo.category.name,
        "impactLevel" to demo.impact.level.name,
        "impactScore" to demo.impact.score.value,
        "participantCount" to (demo.participantCount ?: 0),
        "reasons" to demo.impact.reasons
    )

    private fun impactRadiusKm(level: ImpactLevel) = when (level) {
        ImpactLevel.LOW -> 0.1
        ImpactLevel.MEDIUM -> 0.17
        ImpactLevel.HIGH -> 0.27
        ImpactLevel.VERY_HIGH -> 0.4
    }

    private fun approximateCircle(lat: Double, lon: Double, radiusKm: Double): List<List<Double>> {
        val points = 32
        val latRad = Math.toRadians(lat)
        val latDeg = radiusKm / 111.0
        val lonDeg = radiusKm / (111.0 * Math.cos(latRad))
        return (0..points).map { i ->
            val angle = 2 * Math.PI * i / points
            listOf(lon + lonDeg * Math.cos(angle), lat + latDeg * Math.sin(angle))
        }
    }
}
