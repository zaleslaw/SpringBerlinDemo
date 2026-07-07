package com.zaleslaw.berlinprotesttracker.parser

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

class ParserStructureException(message: String) : RuntimeException(message)

@Component
class BerlinPolicePageParser {

    private val log = LoggerFactory.getLogger(javaClass)

    fun parse(html: String): List<RawDemonstrationRow> {
        val doc = Jsoup.parse(html)

        val table = doc.selectFirst("table.table, table[summary], table")
            ?: throw ParserStructureException(
                "Could not find demonstrations table in source HTML. " +
                "The page structure may have changed."
            )

        val tbody = table.selectFirst("tbody")
        val rows = (tbody ?: table).select("tr").filter { it.select("td").isNotEmpty() }
        if (rows.isEmpty()) {
            log.info("Source page returned empty demonstrations table")
            return emptyList()
        }

        log.info("Parsing {} table rows from source page", rows.size)
        val result = mutableListOf<RawDemonstrationRow>()
        for ((index, row) in rows.withIndex()) {
            val cells = row.select("td")
            if (cells.isEmpty()) continue

            result += parseRow(index, row, cells)

            if ((result.size) % 100 == 0) {
                log.info("Parsing progress: {}/{} rows processed", result.size, rows.size)
            }
        }

        log.info("Raw rows parsed: {} total", result.size)
        return result
    }

    private fun parseRow(index: Int, row: Element, cells: List<Element>): RawDemonstrationRow {
        val rawText = row.text()
        return when {
            cells.size >= 7 -> {
                // Columns: Datum | Von | Bis | Thema | PLZ | Versammlungsort | Aufzugsstrecke
                val von = cells[1].text().trim()
                val bis = cells[2].text().trim()
                val timeRaw = when {
                    von.isNotBlank() && bis.isNotBlank() -> "$von - $bis"
                    von.isNotBlank() -> von
                    else -> null
                }
                RawDemonstrationRow(
                    rowIndex = index,
                    rawText = rawText,
                    dateRaw = cells[0].text().takeIf { it.isNotBlank() },
                    timeRaw = timeRaw,
                    titleRaw = cells[3].text().takeIf { it.isNotBlank() },
                    districtRaw = cells[4].text().takeIf { it.isNotBlank() },
                    locationRaw = cells[5].text().takeIf { it.isNotBlank() },
                    routeRaw = cells[6].text().takeIf { it.isNotBlank() },
                    participantCountRaw = null
                )
            }
            cells.size >= 5 -> RawDemonstrationRow(
                rowIndex = index,
                rawText = rawText,
                dateRaw = cells[0].text().takeIf { it.isNotBlank() },
                timeRaw = cells[1].text().takeIf { it.isNotBlank() },
                locationRaw = cells[2].text().takeIf { it.isNotBlank() },
                routeRaw = cells[3].text().takeIf { it.isNotBlank() },
                districtRaw = cells[4].text().takeIf { it.isNotBlank() },
                participantCountRaw = null,
                titleRaw = null
            )
            else -> RawDemonstrationRow(
                rowIndex = index,
                rawText = rawText,
                titleRaw = rawText.takeIf { it.isNotBlank() },
                dateRaw = null,
                timeRaw = null,
                locationRaw = null,
                routeRaw = null,
                districtRaw = null,
                participantCountRaw = null
            )
        }
    }
}
