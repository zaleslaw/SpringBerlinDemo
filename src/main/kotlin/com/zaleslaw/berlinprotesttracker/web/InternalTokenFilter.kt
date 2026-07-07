package com.zaleslaw.berlinprotesttracker.web

import com.zaleslaw.berlinprotesttracker.config.AppProperties
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Guards the internal maintenance endpoints with a shared token, replacing the per-handler checks that
 * were duplicated in each controller method. Uses a constant-time comparison to avoid leaking the
 * token via response timing.
 */
@Component
class InternalTokenFilter(props: AppProperties) : OncePerRequestFilter() {

    private val expectedToken = props.internal.token

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        !request.requestURI.startsWith("/internal/")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val provided = request.getHeader(HEADER)
        if (provided == null || !constantTimeEquals(provided, expectedToken)) {
            response.status = HttpStatus.UNAUTHORIZED.value()
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.writer.write("""{"error":"Unauthorized"}""")
            return
        }
        filterChain.doFilter(request, response)
    }

    private fun constantTimeEquals(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(StandardCharsets.UTF_8), b.toByteArray(StandardCharsets.UTF_8))

    companion object {
        const val HEADER = "X-Internal-Token"
    }
}
