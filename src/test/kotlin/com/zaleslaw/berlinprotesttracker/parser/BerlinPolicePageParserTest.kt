package com.zaleslaw.berlinprotesttracker.parser

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BerlinPolicePageParserTest {

    private val parser = BerlinPolicePageParser()

    private fun fixture(name: String): String =
        javaClass.getResourceAsStream("/fixtures/$name")
            ?.bufferedReader()?.readText()
            ?: error("Fixture not found: $name")

    @Test
    fun `parses sample fixture and returns correct row count`() {
        val html = fixture("sample_police_page.html")
        val rows = parser.parse(html)
        assertTrue(rows.size >= 4, "Expected at least 4 rows, got ${rows.size}")
    }

    @Test
    fun `extracts date from first row`() {
        val rows = parser.parse(fixture("sample_police_page.html"))
        assertEquals("26.06.2026", rows[0].dateRaw)
    }

    @Test
    fun `extracts title from first row`() {
        val rows = parser.parse(fixture("sample_police_page.html"))
        assertNotNull(rows[0].titleRaw)
        assertTrue(rows[0].titleRaw!!.contains("Klima"))
    }

    @Test
    fun `throws ParserStructureException on empty HTML`() {
        assertFailsWith<ParserStructureException> {
            parser.parse("<html><body>no table here</body></html>")
        }
    }

    @Test
    fun `returns empty list when table is empty`() {
        val html = "<html><body><table><thead><tr><th>Datum</th></tr></thead><tbody></tbody></table></body></html>"
        val rows = parser.parse(html)
        assertTrue(rows.isEmpty())
    }
}
