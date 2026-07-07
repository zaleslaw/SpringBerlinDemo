package com.zaleslaw.berlinprotesttracker.infra

import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

sealed interface ParserRunHandle {
    data class Persisted(val id: UUID) : ParserRunHandle
    data object NotPersisted : ParserRunHandle
}

@Repository
class ParserRunRepository(private val jdbc: JdbcClient) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun start(sourceUrl: String): ParserRunHandle = try {
        val id = UUID.randomUUID()
        jdbc.sql(
            "insert into parser_run (id, source_url, status) values (:id, :url, 'RUNNING')"
        )
            .param("id", id)
            .param("url", sourceUrl)
            .update()
        ParserRunHandle.Persisted(id)
    } catch (e: DataAccessException) {
        log.warn("Could not persist parser_run start: {}", e.message)
        ParserRunHandle.NotPersisted
    }

    fun complete(
        handle: ParserRunHandle,
        rawRows: Int,
        normalizedRows: Int,
        geocodedRows: Int,
        eventCount: Int,
        sourceHash: String?
    ) {
        if (handle !is ParserRunHandle.Persisted) return
        try {
            jdbc.sql(
                """
                update parser_run set
                    finished_at = :fin, status = 'SUCCESS',
                    raw_rows = :raw, normalized_rows = :norm,
                    geocoded_rows = :geo, event_count = :ec,
                    source_hash = :hash
                where id = :id
                """
            )
                .param("fin", OffsetDateTime.now(ZoneOffset.UTC))
                .param("raw", rawRows)
                .param("norm", normalizedRows)
                .param("geo", geocodedRows)
                .param("ec", eventCount)
                .param("hash", sourceHash)
                .param("id", handle.id)
                .update()
        } catch (e: DataAccessException) {
            log.warn("Could not persist parser_run completion: {}", e.message)
        }
    }

    fun fail(handle: ParserRunHandle, errorMessage: String) {
        if (handle !is ParserRunHandle.Persisted) return
        try {
            jdbc.sql(
                "update parser_run set finished_at = :fin, status = 'FAILED', error_message = :err where id = :id"
            )
                .param("fin", OffsetDateTime.now(ZoneOffset.UTC))
                .param("err", errorMessage.take(1000))
                .param("id", handle.id)
                .update()
        } catch (e: DataAccessException) {
            log.warn("Could not persist parser_run failure: {}", e.message)
        }
    }
}
