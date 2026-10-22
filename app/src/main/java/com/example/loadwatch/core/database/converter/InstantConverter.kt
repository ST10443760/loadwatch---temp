package com.example.loadwatch.core.database.converter

import androidx.room.TypeConverter
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// Stores Instant values as ISO-8601 UTC text (Table 8).
// The format is fixed-width with millisecond precision, e.g. 2026-08-23T20:00:00.000Z, so that SQLite's
// text comparison orders instants correctly in range queries. Instant.toString() would drop trailing zeros
// and make "...:00Z" sort after "...:00.500Z".
class InstantConverter {

    @TypeConverter
    fun fromInstant(value: Instant?): String? = value?.let(FORMATTER::format)

    @TypeConverter
    fun toInstant(value: String?): Instant? = value?.let(Instant::parse)

    companion object {
        val FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)
    }
}
