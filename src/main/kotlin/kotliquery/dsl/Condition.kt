package kotliquery.dsl

/**
 * SQL condition tree.
 *
 * Conditions are composable via [and] / [or] infix functions and are
 * rendered to parameterised SQL by [toSql].
 */
sealed interface Condition {
    fun toSql(params: MutableList<Any?>): String
}

data class ComparisonCondition(
    val column: Column<*>,
    val operator: String,
    val value: Any?,
) : Condition {
    override fun toSql(params: MutableList<Any?>): String {
        params.add(value)
        return "${column.name} $operator ?"
    }
}

data class NullCheckCondition(
    val column: Column<*>,
    val isNullCheck: Boolean,
) : Condition {
    override fun toSql(params: MutableList<Any?>): String = if (isNullCheck) "${column.name} IS NULL" else "${column.name} IS NOT NULL"
}

data class InCondition(
    val column: Column<*>,
    val values: List<Any?>,
) : Condition {
    override fun toSql(params: MutableList<Any?>): String {
        params.addAll(values)
        return "${column.name} IN (${values.joinToString(", ") { "?" }})"
    }
}

data class BetweenCondition(
    val column: Column<*>,
    val from: Any?,
    val to: Any?,
) : Condition {
    override fun toSql(params: MutableList<Any?>): String {
        params.add(from)
        params.add(to)
        return "${column.name} BETWEEN ? AND ?"
    }
}

data class CompositeCondition(
    val left: Condition,
    val operator: String,
    val right: Condition,
) : Condition {
    override fun toSql(params: MutableList<Any?>): String = "(${left.toSql(params)} $operator ${right.toSql(params)})"
}

data class NotCondition(
    val inner: Condition,
) : Condition {
    override fun toSql(params: MutableList<Any?>): String = "NOT (${inner.toSql(params)})"
}

infix fun <T : Any> Column<T>.eq(value: T?): Condition = ComparisonCondition(this, "=", value)

infix fun <T : Any> Column<T>.neq(value: T?): Condition = ComparisonCondition(this, "!=", value)

infix fun <T : Comparable<T>> Column<T>.gt(value: T): Condition = ComparisonCondition(this, ">", value)

infix fun <T : Comparable<T>> Column<T>.lt(value: T): Condition = ComparisonCondition(this, "<", value)

infix fun <T : Comparable<T>> Column<T>.gte(value: T): Condition = ComparisonCondition(this, ">=", value)

infix fun <T : Comparable<T>> Column<T>.lte(value: T): Condition = ComparisonCondition(this, "<=", value)

infix fun Column<String>.like(pattern: String): Condition = ComparisonCondition(this, "LIKE", pattern)

fun <T : Any> Column<T>.isNull(): Condition = NullCheckCondition(this, isNullCheck = true)

fun <T : Any> Column<T>.isNotNull(): Condition = NullCheckCondition(this, isNullCheck = false)

infix fun <T : Any> Column<T>.within(values: Collection<T>): Condition = InCondition(this, values.toList())

infix fun <T : Comparable<T>> Column<T>.between(range: ClosedRange<T>): Condition = BetweenCondition(this, range.start, range.endInclusive)

infix fun Condition.and(other: Condition): Condition = CompositeCondition(this, "AND", other)

infix fun Condition.or(other: Condition): Condition = CompositeCondition(this, "OR", other)

fun not(condition: Condition): Condition = NotCondition(condition)
