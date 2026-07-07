package com.zaleslaw.berlinprotesttracker.geocoder

import com.zaleslaw.berlinprotesttracker.config.AppProperties
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import tools.jackson.databind.ObjectMapper
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NominatimClientTest {

    private val builder = RestClient.builder()
    private val server = MockRestServiceServer.bindTo(builder).build()

    private val props = AppProperties(
        source = AppProperties.Source("u"),
        importJob = AppProperties.ImportJob("0 0 * * * *"),
        internal = AppProperties.Internal("t"),
        geocoder = AppProperties.Geocoder(
            baseUrl = "https://nominatim.test",
            userAgent = "berlin-demo-map-test",
            minDelayMs = 1
        )
    )

    // bindTo() customizes `builder` before the client builds its RestClient from it.
    private val client = NominatimClient(ObjectMapper(), builder, props)

    @Test
    fun `parses the first geocode result`() {
        server.expect(requestTo(containsString("/search")))
            .andRespond(withSuccess("""[{"lat":"52.5219","lon":"13.4132"}]""", MediaType.APPLICATION_JSON))

        val result = client.geocode("Alexanderplatz")

        assertNotNull(result)
        assertEquals(52.5219, result.lat)
        assertEquals(13.4132, result.lon)
        assertEquals("nominatim", result.provider)
    }

    @Test
    fun `returns null when Nominatim has no results`() {
        server.expect(requestTo(containsString("/search")))
            .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON))

        assertNull(client.geocode("Nowhere"))
    }
}
