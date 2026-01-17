package kotliquery

import java.io.InputStream
import java.io.Reader
import java.math.BigDecimal
import java.net.URL
import java.sql.Blob
import java.sql.NClob
import java.sql.Ref
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import java.sql.SQLWarning
import java.sql.Statement
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Represents ResultSet and its each row.
 */
data class Row(
    val underlying: ResultSet,
    val cursor: Int = 0,
) : Sequence<Row> {
    private class RowIterator(
        val rs: ResultSet,
        val position: Int,
    ) : Iterator<Row> {
        override fun next(): Row = Row(rs, position + 1)

        override fun hasNext(): Boolean = rs.isClosed == false && rs.next()
    }

    override fun iterator(): Iterator<Row> = RowIterator(underlying, cursor)

    /**
     * Surely fetches nullable value from ResultSet.
     */
    private fun <A> nullable(v: A): A? = if (underlying.wasNull()) null else v

    fun statementOrNull(): Statement? = nullable(underlying.statement)

    fun warningsOrNull(): SQLWarning? = underlying.warnings

    fun next(): Boolean = underlying.next()

    fun close() = underlying.close()

    fun string(columnIndex: Int): String = stringOrNull(columnIndex)!!

    fun stringOrNull(columnIndex: Int): String? = nullable(underlying.getString(columnIndex))

    fun string(columnLabel: String): String = stringOrNull(columnLabel)!!

    fun stringOrNull(columnLabel: String): String? = nullable(underlying.getString(columnLabel))

    fun any(columnIndex: Int): Any = anyOrNull(columnIndex)!!

    fun anyOrNull(columnIndex: Int): Any? = nullable(underlying.getObject(columnIndex))

    fun any(columnLabel: String): Any = anyOrNull(columnLabel)!!

    fun anyOrNull(columnLabel: String): Any? = nullable(underlying.getObject(columnLabel))

    fun long(columnIndex: Int): Long = longOrNull(columnIndex)!!

    fun longOrNull(columnIndex: Int): Long? = nullable(underlying.getLong(columnIndex))

    fun long(columnLabel: String): Long = longOrNull(columnLabel)!!

    fun longOrNull(columnLabel: String): Long? = nullable(underlying.getLong(columnLabel))

    fun bytes(columnIndex: Int): ByteArray = bytesOrNull(columnIndex)!!

    fun bytesOrNull(columnIndex: Int): ByteArray? = nullable(underlying.getBytes(columnIndex))

    fun bytes(columnLabel: String): ByteArray = bytesOrNull(columnLabel)!!

    fun bytesOrNull(columnLabel: String): ByteArray? = nullable(underlying.getBytes(columnLabel))

    fun float(columnIndex: Int): Float = floatOrNull(columnIndex)!!

    fun floatOrNull(columnIndex: Int): Float? = nullable(underlying.getFloat(columnIndex))

    fun float(columnLabel: String): Float = floatOrNull(columnLabel)!!

    fun floatOrNull(columnLabel: String): Float? = nullable(underlying.getFloat(columnLabel))

    fun short(columnIndex: Int): Short = shortOrNull(columnIndex)!!

    fun shortOrNull(columnIndex: Int): Short? = nullable(underlying.getShort(columnIndex))

    fun short(columnLabel: String): Short = shortOrNull(columnLabel)!!

    fun shortOrNull(columnLabel: String): Short? = nullable(underlying.getShort(columnLabel))

    fun double(columnIndex: Int): Double = doubleOrNull(columnIndex)!!

    fun doubleOrNull(columnIndex: Int): Double? = nullable(underlying.getDouble(columnIndex))

    fun double(columnLabel: String): Double = doubleOrNull(columnLabel)!!

    fun doubleOrNull(columnLabel: String): Double? = nullable(underlying.getDouble(columnLabel))

    fun int(columnIndex: Int): Int = intOrNull(columnIndex)!!

    fun intOrNull(columnIndex: Int): Int? = nullable(underlying.getInt(columnIndex))

    fun int(columnLabel: String): Int = intOrNull(columnLabel)!!

    fun intOrNull(columnLabel: String): Int? = nullable(underlying.getInt(columnLabel))

    fun zonedDateTime(columnIndex: Int): ZonedDateTime = zonedDateTimeOrNull(columnIndex)!!

    fun zonedDateTimeOrNull(columnIndex: Int): ZonedDateTime? =
        nullable(
            sqlTimestampOrNull(columnIndex)?.toInstant()?.let {
                ZonedDateTime.ofInstant(it, ZoneId.systemDefault())
            },
        )

    fun zonedDateTime(columnLabel: String): ZonedDateTime = zonedDateTimeOrNull(columnLabel)!!

    fun zonedDateTimeOrNull(columnLabel: String): ZonedDateTime? =
        nullable(
            sqlTimestampOrNull(columnLabel)?.toInstant()?.let {
                ZonedDateTime.ofInstant(it, ZoneId.systemDefault())
            },
        )

    fun offsetDateTime(columnIndex: Int): OffsetDateTime = offsetDateTimeOrNull(columnIndex)!!

    fun offsetDateTimeOrNull(columnIndex: Int): OffsetDateTime? =
        nullable(
            sqlTimestampOrNull(columnIndex)?.toInstant()?.let {
                OffsetDateTime.ofInstant(it, ZoneId.systemDefault())
            },
        )

    fun offsetDateTime(columnLabel: String): OffsetDateTime = offsetDateTimeOrNull(columnLabel)!!

    fun offsetDateTimeOrNull(columnLabel: String): OffsetDateTime? =
        nullable(
            sqlTimestampOrNull(columnLabel)?.toInstant()?.let {
                OffsetDateTime.ofInstant(it, ZoneId.systemDefault())
            },
        )

    fun instant(columnIndex: Int): Instant = instantOrNull(columnIndex)!!

    fun instantOrNull(columnIndex: Int): Instant? = nullable(sqlTimestampOrNull(columnIndex)?.toInstant())

    fun instant(columnLabel: String): Instant = instantOrNull(columnLabel)!!

    fun instantOrNull(columnLabel: String): Instant? = nullable(sqlTimestampOrNull(columnLabel)?.toInstant())

    fun localDateTime(columnIndex: Int): LocalDateTime = localDateTimeOrNull(columnIndex)!!

    fun localDateTimeOrNull(columnIndex: Int): LocalDateTime? = sqlTimestampOrNull(columnIndex)?.toLocalDateTime()

    fun localDateTime(columnLabel: String): LocalDateTime = localDateTimeOrNull(columnLabel)!!

    fun localDateTimeOrNull(columnLabel: String): LocalDateTime? = sqlTimestampOrNull(columnLabel)?.toLocalDateTime()

    fun localDate(columnIndex: Int): LocalDate = localDateOrNull(columnIndex)!!

    fun localDateOrNull(columnIndex: Int): LocalDate? = sqlTimestampOrNull(columnIndex)?.toLocalDateTime()?.toLocalDate()

    fun localDate(columnLabel: String): LocalDate = localDateOrNull(columnLabel)!!

    fun localDateOrNull(columnLabel: String): LocalDate? = sqlTimestampOrNull(columnLabel)?.toLocalDateTime()?.toLocalDate()

    fun localTime(columnIndex: Int): LocalTime = localTimeOrNull(columnIndex)!!

    fun localTimeOrNull(columnIndex: Int): LocalTime? = sqlTimestampOrNull(columnIndex)?.toLocalDateTime()?.toLocalTime()

    fun localTime(columnLabel: String): LocalTime = localTimeOrNull(columnLabel)!!

    fun localTimeOrNull(columnLabel: String): LocalTime? = sqlTimestampOrNull(columnLabel)?.toLocalDateTime()?.toLocalTime()

    fun sqlDate(columnIndex: Int): java.sql.Date = sqlDateOrNull(columnIndex)!!

    fun sqlDateOrNull(columnIndex: Int): java.sql.Date? = nullable(underlying.getDate(columnIndex))

    fun sqlDate(columnLabel: String): java.sql.Date = sqlDateOrNull(columnLabel)!!

    fun sqlDateOrNull(columnLabel: String): java.sql.Date? = nullable(underlying.getDate(columnLabel))

    fun sqlDate(
        columnIndex: Int,
        cal: java.util.Calendar,
    ): java.sql.Date = sqlDateOrNull(columnIndex, cal)!!

    fun sqlDateOrNull(
        columnIndex: Int,
        cal: java.util.Calendar,
    ): java.sql.Date? = nullable(underlying.getDate(columnIndex, cal))

    fun sqlDate(
        columnLabel: String,
        cal: java.util.Calendar,
    ): java.sql.Date = sqlDateOrNull(columnLabel, cal)!!

    fun sqlDateOrNull(
        columnLabel: String,
        cal: java.util.Calendar,
    ): java.sql.Date? = nullable(underlying.getDate(columnLabel, cal))

    fun boolean(columnIndex: Int): Boolean = underlying.getBoolean(columnIndex)

    fun boolean(columnLabel: String): Boolean = underlying.getBoolean(columnLabel)

    fun bigDecimal(columnIndex: Int): BigDecimal = bigDecimalOrNull(columnIndex)!!

    fun bigDecimalOrNull(columnIndex: Int): BigDecimal? = nullable(underlying.getBigDecimal(columnIndex))

    fun bigDecimal(columnLabel: String): BigDecimal = bigDecimalOrNull(columnLabel)!!

    fun bigDecimalOrNull(columnLabel: String): BigDecimal? = nullable(underlying.getBigDecimal(columnLabel))

    fun sqlTime(columnIndex: Int): java.sql.Time = sqlTimeOrNull(columnIndex)!!

    fun sqlTimeOrNull(columnIndex: Int): java.sql.Time? = nullable(underlying.getTime(columnIndex))

    fun sqlTime(columnLabel: String): java.sql.Time = sqlTimeOrNull(columnLabel)!!

    fun sqlTimeOrNull(columnLabel: String): java.sql.Time? = nullable(underlying.getTime(columnLabel))

    fun sqlTime(
        columnIndex: Int,
        cal: java.util.Calendar,
    ): java.sql.Time = sqlTimeOrNull(columnIndex, cal)!!

    fun sqlTimeOrNull(
        columnIndex: Int,
        cal: java.util.Calendar,
    ): java.sql.Time? = nullable(underlying.getTime(columnIndex, cal))

    fun sqlTime(
        columnLabel: String,
        cal: java.util.Calendar,
    ): java.sql.Time = sqlTimeOrNull(columnLabel, cal)!!

    fun sqlTimeOrNull(
        columnLabel: String,
        cal: java.util.Calendar,
    ): java.sql.Time? = nullable(underlying.getTime(columnLabel, cal))

    fun url(columnIndex: Int): URL = urlOrNull(columnIndex)!!

    fun urlOrNull(columnIndex: Int): URL? = nullable(underlying.getURL(columnIndex))

    fun url(columnLabel: String): URL = urlOrNull(columnLabel)!!

    fun urlOrNull(columnLabel: String): URL? = nullable(underlying.getURL(columnLabel))

    fun blob(columnIndex: Int): Blob = blobOrNull(columnIndex)!!

    fun blobOrNull(columnIndex: Int): Blob? = nullable(underlying.getBlob(columnIndex))

    fun blob(columnLabel: String): Blob = blobOrNull(columnLabel)!!

    fun blobOrNull(columnLabel: String): Blob? = nullable(underlying.getBlob(columnLabel))

    fun byte(columnIndex: Int): Byte = byteOrNull(columnIndex)!!

    fun byteOrNull(columnIndex: Int): Byte? = nullable(underlying.getByte(columnIndex))

    fun byte(columnLabel: String): Byte = byteOrNull(columnLabel)!!

    fun byteOrNull(columnLabel: String): Byte? = nullable(underlying.getByte(columnLabel))

    fun clob(columnIndex: Int): java.sql.Clob = clobOrNull(columnIndex)!!

    fun clobOrNull(columnIndex: Int): java.sql.Clob? = nullable(underlying.getClob(columnIndex))

    fun clob(columnLabel: String): java.sql.Clob = clobOrNull(columnLabel)!!

    fun clobOrNull(columnLabel: String): java.sql.Clob? = nullable(underlying.getClob(columnLabel))

    fun nClob(columnIndex: Int): NClob = nClobOrNull(columnIndex)!!

    fun nClobOrNull(columnIndex: Int): NClob? = nullable(underlying.getNClob(columnIndex))

    fun nClob(columnLabel: String): NClob = nClobOrNull(columnLabel)!!

    fun nClobOrNull(columnLabel: String): NClob? = nullable(underlying.getNClob(columnLabel))

    inline fun <reified T> array(columnIndex: Int): Array<T> = arrayOrNull(columnIndex)!!

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T> arrayOrNull(columnIndex: Int): Array<T>? {
        val result = sqlArrayOrNull(columnIndex)?.array as Array<*>?
        return result?.map { it as T }?.toTypedArray()
    }

    inline fun <reified T> array(columnLabel: String): Array<T> = arrayOrNull(columnLabel)!!

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T> arrayOrNull(columnLabel: String): Array<T>? {
        val result = sqlArrayOrNull(columnLabel)?.array as Array<Any>?
        return result?.map { it as T }?.toTypedArray()
    }

    fun sqlArray(columnIndex: Int): java.sql.Array = sqlArrayOrNull(columnIndex)!!

    fun sqlArrayOrNull(columnIndex: Int): java.sql.Array? = nullable(underlying.getArray(columnIndex))

    fun sqlArray(columnLabel: String): java.sql.Array = sqlArrayOrNull(columnLabel)!!

    fun sqlArrayOrNull(columnLabel: String): java.sql.Array? = nullable(underlying.getArray(columnLabel))

    fun asciiStream(columnIndex: Int): InputStream = asciiStreamOrNull(columnIndex)!!

    fun asciiStreamOrNull(columnIndex: Int): InputStream? = nullable(underlying.getAsciiStream(columnIndex))

    fun asciiStream(columnLabel: String): InputStream = asciiStreamOrNull(columnLabel)!!

    fun asciiStreamOrNull(columnLabel: String): InputStream? = nullable(underlying.getAsciiStream(columnLabel))

    fun sqlTimestamp(columnIndex: Int): java.sql.Timestamp = sqlTimestampOrNull(columnIndex)!!

    fun sqlTimestampOrNull(columnIndex: Int): java.sql.Timestamp? = nullable(underlying.getTimestamp(columnIndex))

    fun sqlTimestamp(columnLabel: String): java.sql.Timestamp = sqlTimestampOrNull(columnLabel)!!

    fun sqlTimestampOrNull(columnLabel: String): java.sql.Timestamp? = nullable(underlying.getTimestamp(columnLabel))

    fun sqlTimestamp(
        columnIndex: Int,
        cal: java.util.Calendar,
    ): java.sql.Timestamp = sqlTimestampOrNull(columnIndex, cal)!!

    fun sqlTimestampOrNull(
        columnIndex: Int,
        cal: java.util.Calendar,
    ): java.sql.Timestamp? = nullable(underlying.getTimestamp(columnIndex, cal))

    fun sqlTimestamp(
        columnLabel: String,
        cal: java.util.Calendar,
    ): java.sql.Timestamp = sqlTimestampOrNull(columnLabel, cal)!!

    fun sqlTimestampOrNull(
        columnLabel: String,
        cal: java.util.Calendar,
    ): java.sql.Timestamp? = nullable(underlying.getTimestamp(columnLabel, cal))

    fun uuid(columnLabel: String): java.util.UUID = uuidOrNull(columnLabel)!!

    fun uuidOrNull(columnLabel: String): java.util.UUID? = nullable(underlying.getObject(columnLabel, java.util.UUID::class.java))

    fun ref(columnIndex: Int): Ref = refOrNull(columnIndex)!!

    fun refOrNull(columnIndex: Int): Ref? = nullable(underlying.getRef(columnIndex))

    fun ref(columnLabel: String): Ref = refOrNull(columnLabel)!!

    fun refOrNull(columnLabel: String): Ref? = nullable(underlying.getRef(columnLabel))

    fun nCharacterStream(columnIndex: Int): Reader = nCharacterStreamOrNull(columnIndex)!!

    fun nCharacterStreamOrNull(columnIndex: Int): Reader? = nullable(underlying.getNCharacterStream(columnIndex))

    fun nCharacterStream(columnLabel: String): Reader = nCharacterStreamOrNull(columnLabel)!!

    fun nCharacterStreamOrNull(columnLabel: String): Reader? = nullable(underlying.getNCharacterStream(columnLabel))

    fun metaDataOrNull(): ResultSetMetaData = underlying.metaData

    fun binaryStream(columnIndex: Int): InputStream = binaryStreamOrNull(columnIndex)!!

    fun binaryStreamOrNull(columnIndex: Int): InputStream? = nullable(underlying.getBinaryStream(columnIndex))

    fun binaryStream(columnLabel: String): InputStream = binaryStreamOrNull(columnLabel)!!

    fun binaryStreamOrNull(columnLabel: String): InputStream? = nullable(underlying.getBinaryStream(columnLabel))
}
