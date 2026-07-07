package com.zaleslaw.berlinprotesttracker.infra

import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

data class Hotspot(val name: String, val category: String, val weight: Int)

@Repository
class HotspotRepository(private val jdbc: JdbcClient) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun findAll(): List<Hotspot> = try {
        jdbc.sql("select name, category, weight from hotspot order by weight desc")
            .query { rs, _ -> Hotspot(rs.getString("name"), rs.getString("category"), rs.getInt("weight")) }
            .list()
    } catch (e: DataAccessException) {
        log.warn("Could not load hotspots: {}", e.message)
        emptyList()
    }
}
