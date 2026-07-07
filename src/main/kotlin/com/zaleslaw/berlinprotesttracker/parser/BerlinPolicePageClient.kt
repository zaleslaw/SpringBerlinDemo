package com.zaleslaw.berlinprotesttracker.parser

import com.zaleslaw.berlinprotesttracker.config.AppProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.net.URI
import java.nio.charset.StandardCharsets

class SourcePageUnavailableException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

@Component
class BerlinPolicePageClient(
    restClientBuilder: RestClient.Builder,
    props: AppProperties
) {

    private val sourceUrl = props.source.url
    private val log = LoggerFactory.getLogger(javaClass)
    private val restClient = restClientBuilder.build()

    fun fetchHtml(): String {
        log.info("Fetching source page: {}", sourceUrl)

        val bytes = try {
            restClient.get()
                .uri(URI.create(sourceUrl))
                .retrieve()
                .body(ByteArray::class.java)
        } catch (e: RestClientResponseException) {
            throw SourcePageUnavailableException(
                "Source page returned HTTP ${e.statusCode.value()}: $sourceUrl", e
            )
        } catch (e: Exception) {
            throw SourcePageUnavailableException("Failed to connect to source page: $sourceUrl", e)
        }

        if (bytes == null || bytes.isEmpty()) {
            throw SourcePageUnavailableException("Source page returned empty body: $sourceUrl")
        }

        // Force UTF-8: berlin.de declares charset=iso-8859-1 in Content-Type but sends UTF-8 bytes
        val body = String(bytes, StandardCharsets.UTF_8)
        if (body.isBlank()) {
            throw SourcePageUnavailableException("Source page returned empty body: $sourceUrl")
        }

        log.info("Source HTML downloaded: {} bytes", body.length)
        return body
    }
}
