package com.gymtracker.app.data.importer

/**
 * RFC-4180 compliant CSV parser that properly supports:
 * - UTF-8 BOM stripping
 * - CRLF / LF line endings
 * - Quoted fields containing commas and embedded newlines (such as JEFIT routine descriptions)
 * - Escaped double quotes ("") inside quoted fields
 */
object Rfc4180CsvParser {

    fun parse(input: String): List<List<String>> {
        if (input.isBlank()) return emptyList()
        val text = input.removePrefix("\uFEFF")
        val rows = mutableListOf<List<String>>()
        val currentRow = mutableListOf<String>()
        val field = StringBuilder()

        var inQuotes = false
        var fieldStart = true
        var i = 0
        val n = text.length

        while (i < n) {
            val ch = text[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < n && text[i + 1] == '"') {
                        field.append('"')
                        i += 2
                        continue
                    } else {
                        inQuotes = false
                        i++
                        continue
                    }
                } else {
                    field.append(ch)
                    i++
                    continue
                }
            } else {
                when (ch) {
                    '"' -> {
                        if (fieldStart) {
                            inQuotes = true
                            fieldStart = false
                        } else {
                            field.append(ch)
                        }
                    }
                    ',' -> {
                        currentRow += field.toString()
                        field.setLength(0)
                        fieldStart = true
                    }
                    '\r' -> {
                        if (i + 1 < n && text[i + 1] == '\n') {
                            i++
                        }
                        finishRow(currentRow, field, rows)
                        fieldStart = true
                    }
                    '\n' -> {
                        finishRow(currentRow, field, rows)
                        fieldStart = true
                    }
                    else -> {
                        field.append(ch)
                        fieldStart = false
                    }
                }
                i++
            }
        }

        if (field.isNotEmpty() || currentRow.isNotEmpty()) {
            finishRow(currentRow, field, rows)
        }
        return rows
    }

    private fun finishRow(
        currentRow: MutableList<String>,
        field: StringBuilder,
        rows: MutableList<List<String>>
    ) {
        currentRow += field.toString()
        field.setLength(0)
        // Skip blank lines between sub-tables or trailing newlines
        if (!(currentRow.size == 1 && currentRow[0].isBlank())) {
            rows += currentRow.toList()
        }
        currentRow.clear()
    }
}
