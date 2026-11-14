package com.example.esp32test.data.model

import kotlinx.serialization.Serializable

/**
 * Known Nordic Runes mapped to integer scan patterns.
 *
 * @param runeName Name of the Nordic Rune (e.g. "Othala", "Ehwaz").
 * @param symbol Big Unicode symbol representation of the rune (e.g. "ᛟ", "ᛖ").
 * @param pattern The sequence of scanned integers representing this rune.
 * @param description Brief description or meaning of the rune.
 */
@Serializable
enum class NordicRune(
    val runeName: String,
    val symbol: String,
    val pattern: List<Int>,
    val description: String
) {
    OTHALA(
        runeName = "Othala",
        symbol = "ᛟ",
        pattern = listOf(11, 5, 2, 4, 13),
        description = "Heritage, Ancestral Home, Estate"
    ),
    EHWAZ(
        runeName = "Ehwaz",
        symbol = "ᛖ",
        pattern = listOf(11, 6, 1, 7, 3, 8, 13),
        description = "Movement, Progress, Partnership"
    );

    companion object {
        /**
         * Compares a completed scan array against known rune patterns.
         * Returns matching [NordicRune] if found, or `null` otherwise.
         */
        fun matchPattern(scanArray: List<Int>): NordicRune? {
            return entries.firstOrNull { it.pattern == scanArray }
        }
    }
}
