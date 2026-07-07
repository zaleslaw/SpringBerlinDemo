package com.zaleslaw.berlinprotesttracker.geocoder

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

@Repository
class GeocodeCacheRepository(private val jdbc: JdbcClient) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val memCache = ConcurrentHashMap<String, GeocodeResult>()

    @PostConstruct
    fun warmUp() {
        try {
            val rows = jdbc.sql(
                "select query, lat, lon, confidence, provider from geocode_cache"
            )
                .query { rs, _ ->
                    rs.getString("query") to GeocodeResult(
                        lat = rs.getDouble("lat"),
                        lon = rs.getDouble("lon"),
                        confidence = rs.getDouble("confidence"),
                        provider = rs.getString("provider")
                    )
                }
                .list()
            rows.forEach { (q, r) -> memCache[q] = r }
            log.info("Geocode cache warmed: {} entries loaded from DB", memCache.size)
        } catch (e: DataAccessException) {
            log.warn("Geocode cache warm-up failed (DB may be empty): {}", e.message)
        }
    }

    fun findByQuery(query: String): GeocodeResult? = memCache[query]

    fun save(query: String, result: GeocodeResult) {
        // Write the durable store first, then the in-memory cache — so the mem cache never holds
        // an entry the DB rejected (which would silently vanish on the next restart).
        try {
            jdbc.sql(
                """
                insert into geocode_cache (query, provider, lat, lon, confidence)
                values (:q, :provider, :lat, :lon, :conf)
                on conflict (query) do update
                  set provider = excluded.provider,
                      lat = excluded.lat, lon = excluded.lon,
                      confidence = excluded.confidence, updated_at = now()
                """
            )
                .param("q", query)
                .param("provider", result.provider)
                .param("lat", result.lat)
                .param("lon", result.lon)
                .param("conf", result.confidence)
                .update()
            memCache[query] = result
        } catch (e: DataAccessException) {
            // Offline / DB-less mode: keep the result in memory so the current import still benefits,
            // but it is explicitly ephemeral (lost on restart) since it was never persisted.
            memCache[query] = result
            log.warn("Geocode cache DB write failed for '{}' (kept in-memory only): {}", query, e.message)
        }
    }
}
