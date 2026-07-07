package com.zaleslaw.berlinprotesttracker.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration

@Configuration
class HttpClientConfig {

    /**
     * Shared RestClient.Builder with centralized timeouts and redirect handling, backed by a single
     * JDK HttpClient. Each client (Nominatim, source page, OSRM) builds its own RestClient from this
     * with its own base URL / headers, but they all share the connection pool and timeout policy.
     */
    @Bean
    fun restClientBuilder(): RestClient.Builder {
        val httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
        val requestFactory = JdkClientHttpRequestFactory(httpClient).apply {
            setReadTimeout(Duration.ofSeconds(15))
        }
        return RestClient.builder().requestFactory(requestFactory)
    }
}
