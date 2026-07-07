package com.zaleslaw.berlinprotesttracker.infra

import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

@Repository
class DistrictRepository(private val jdbc: JdbcClient) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun findAllNames(): List<String> = try {
        jdbc.sql("select name from district order by name")
            .query { rs, _ -> rs.getString("name") }
            .list()
    } catch (e: DataAccessException) {
        log.warn("Could not load districts: {}", e.message)
        emptyList()
    }
}
