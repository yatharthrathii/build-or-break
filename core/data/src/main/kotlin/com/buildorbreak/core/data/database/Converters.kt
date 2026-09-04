package com.buildorbreak.core.data.database

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * How time is stored.
 *
 * Every choice here is about what a query needs to be able to do, not about what
 * reads nicely:
 *
 * - `LocalDate` as an epoch day, because every "between these dates" query is
 *   then an integer comparison and an index works on it
 * - `LocalTime` as a second of day, for the same reason
 * - `LocalDateTime` as an ISO string, because it is a wall clock reading with no
 *   zone attached. Turning it into a number would need a zone this class does
 *   not have, and ISO strings sort in the right order anyway
 * - `Instant` as epoch milliseconds, since it is a real moment and a number is
 *   the honest representation of one
 *
 * **Enums are deliberately not here.** They are stored as text on the entity and
 * converted in the mapper. A converter that fails on an unknown value crashes
 * inside Room, at read time, with no context; the mapper can fall back to a
 * documented default and keep the day rendering.
 */
object Converters {

    private val localDateTime: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    @TypeConverter
    fun dateToEpochDay(value: LocalDate?): Long? = value?.toEpochDay()

    @TypeConverter
    fun epochDayToDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun timeToSecondOfDay(value: LocalTime?): Int? = value?.toSecondOfDay()

    @TypeConverter
    fun secondOfDayToTime(value: Int?): LocalTime? = value?.let { LocalTime.ofSecondOfDay(it.toLong()) }

    @TypeConverter
    fun dateTimeToText(value: LocalDateTime?): String? = value?.format(localDateTime)

    @TypeConverter
    fun textToDateTime(value: String?): LocalDateTime? = value?.let { LocalDateTime.parse(it, localDateTime) }

    @TypeConverter
    fun instantToMillis(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun millisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun zoneToText(value: ZoneId?): String? = value?.id

    @TypeConverter
    fun textToZone(value: String?): ZoneId? = value?.let(ZoneId::of)
}
