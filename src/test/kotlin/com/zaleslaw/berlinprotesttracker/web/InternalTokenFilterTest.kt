package com.zaleslaw.berlinprotesttracker.web

import com.zaleslaw.berlinprotesttracker.config.AppProperties
import com.zaleslaw.berlinprotesttracker.importjob.DemonstrationImportService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder

class InternalTokenFilterTest {

    private val importService = mock(DemonstrationImportService::class.java)

    private val props = AppProperties(
        source = AppProperties.Source("u"),
        importJob = AppProperties.ImportJob("0 0 * * * *"),
        internal = AppProperties.Internal("secret"),
        geocoder = AppProperties.Geocoder(baseUrl = "x", userAgent = "x")
    )

    private val mockMvc = MockMvcBuilders
        .standaloneSetup(InternalImportController(importService))
        .addFilters<StandaloneMockMvcBuilder>(InternalTokenFilter(props))
        .build()

    @Test
    fun `rejects request with no token`() {
        mockMvc.perform(post("/internal/import/run"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `rejects request with wrong token`() {
        mockMvc.perform(post("/internal/import/run").header(InternalTokenFilter.HEADER, "nope"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `accepts request with correct token and returns 202`() {
        `when`(importService.tryStartImportAsync()).thenReturn(true)
        mockMvc.perform(post("/internal/import/run").header(InternalTokenFilter.HEADER, "secret"))
            .andExpect(status().isAccepted)
    }
}
