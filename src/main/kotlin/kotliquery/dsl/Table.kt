package kotliquery.dsl

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.util.UUID

/**
 * Typed reference to a database column.
 *
 * The type parameter [T] carries the Kotlin type of the column so that
 * condition operators (eq, gt, …) can enforce type-safety at compile time.
 */
data class Column<T : Any>(
    val name: String,
    val table: Table,
)

/**
 * Base class for table definitions.
 *
 * Declare a table as a Kotlin object that extends [Table]:
 * ```
 * object Members : Table("members") {
 *     val id = integer("id")
 *     val name = varchar("name")
 *     val createdAt = timestamp("created_at")
 * }
 * ```
 */
abstract class Table(
    val tableName: String,
) {
    private val _columns = mutableListOf<Column<*>>()

    /** All columns registered on this table, in declaration order. */
    val columns: List<Column<*>> get() = _columns.toList()

    protected fun integer(name: String): Column<Int> = register(name)

    protected fun long(name: String): Column<Long> = register(name)

    protected fun short(name: String): Column<Short> = register(name)

    protected fun float(name: String): Column<Float> = register(name)

    protected fun double(name: String): Column<Double> = register(name)

    protected fun boolean(name: String): Column<Boolean> = register(name)

    protected fun varchar(name: String): Column<String> = register(name)

    protected fun text(name: String): Column<String> = register(name)

    protected fun bigDecimal(name: String): Column<BigDecimal> = register(name)

    protected fun timestamp(name: String): Column<ZonedDateTime> = register(name)

    protected fun instant(name: String): Column<Instant> = register(name)

    protected fun localDateTime(name: String): Column<LocalDateTime> = register(name)

    protected fun localDate(name: String): Column<LocalDate> = register(name)

    protected fun localTime(name: String): Column<LocalTime> = register(name)

    protected fun offsetDateTime(name: String): Column<OffsetDateTime> = register(name)

    protected fun uuid(name: String): Column<UUID> = register(name)

    protected fun byteArray(name: String): Column<ByteArray> = register(name)

    /** Generic column when none of the typed factories fit. */
    protected fun <T : Any> column(name: String): Column<T> = register(name)

    private fun <T : Any> register(name: String): Column<T> = Column<T>(name, this).also { _columns.add(it) }
}
