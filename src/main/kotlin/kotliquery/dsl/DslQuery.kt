package kotliquery.dsl

import kotliquery.Query
import kotliquery.Row
import kotliquery.action.ExecuteQueryAction
import kotliquery.action.ResultQueryActionBuilder
import kotliquery.action.UpdateQueryAction
import kotliquery.queryOf

object All

enum class SortOrder { ASC, DESC }

fun <T : Any> Column<T>.asc(): Pair<Column<T>, SortOrder> = this to SortOrder.ASC

fun <T : Any> Column<T>.desc(): Pair<Column<T>, SortOrder> = this to SortOrder.DESC

class ColumnSelection(
    val columns: List<Column<*>>?,
)

fun select(marker: All): ColumnSelection = ColumnSelection(null)

fun select(vararg columns: Column<*>): ColumnSelection = ColumnSelection(columns.toList())

infix fun ColumnSelection.from(table: Table): SelectQuery = SelectQuery(columns, table)

data class SelectQuery(
    val columns: List<Column<*>>?,
    val table: Table,
    val condition: Condition? = null,
    val ordering: List<Pair<Column<*>, SortOrder>>? = null,
    val limitCount: Int? = null,
) {
    infix fun where(condition: Condition): SelectQuery = copy(condition = condition)

    infix fun orderBy(order: Pair<Column<*>, SortOrder>): SelectQuery = copy(ordering = (ordering ?: emptyList()) + order)

    infix fun limit(count: Int): SelectQuery = copy(limitCount = count)

    fun toQuery(): Query {
        val params = mutableListOf<Any?>()
        val sql =
            buildString {
                val cols = columns?.joinToString(", ") { it.name } ?: "*"
                append("SELECT $cols FROM ${table.tableName}")
                condition?.let {
                    append(" WHERE ")
                    append(it.toSql(params))
                }
                ordering?.let { orders ->
                    append(" ORDER BY ")
                    append(orders.joinToString(", ") { (col, order) -> "${col.name} ${order.name}" })
                }
                limitCount?.let { append(" LIMIT $it") }
            }
        return queryOf(sql, *params.toTypedArray())
    }

    fun <A> map(extractor: (Row) -> A?): ResultQueryActionBuilder<A> = toQuery().map(extractor)
}

class InsertTarget(
    val table: Table,
)

fun insertInto(table: Table): InsertTarget = InsertTarget(table)

data class InsertQuery(
    val table: Table,
    val columnNames: List<String>,
    val paramValues: List<Any?>,
) {
    fun toQuery(): Query {
        val cols = columnNames.joinToString(", ")
        val placeholders = columnNames.joinToString(", ") { "?" }
        val sql = "INSERT INTO ${table.tableName} ($cols) VALUES ($placeholders)"
        return queryOf(sql, *paramValues.toTypedArray())
    }

    val asUpdate: UpdateQueryAction get() = toQuery().asUpdate

    val asExecute: ExecuteQueryAction get() = toQuery().asExecute
}

fun InsertTarget.values(vararg assignments: Pair<Column<*>, Any?>): InsertQuery =
    InsertQuery(
        table = table,
        columnNames = assignments.map { it.first.name },
        paramValues = assignments.map { it.second },
    )

infix fun InsertTarget.values(assignment: Pair<Column<*>, Any?>): InsertQuery =
    InsertQuery(
        table = table,
        columnNames = listOf(assignment.first.name),
        paramValues = listOf(assignment.second),
    )

class UpdateTarget(
    val table: Table,
)

fun update(table: Table): UpdateTarget = UpdateTarget(table)

data class UpdateSetClause(
    val table: Table,
    val assignments: List<Pair<String, Any?>>,
)

fun UpdateTarget.set(vararg assignments: Pair<Column<*>, Any?>): UpdateSetClause =
    UpdateSetClause(
        table = table,
        assignments = assignments.map { it.first.name to it.second },
    )

infix fun UpdateTarget.set(assignment: Pair<Column<*>, Any?>): UpdateSetClause =
    UpdateSetClause(
        table = table,
        assignments = listOf(assignment.first.name to assignment.second),
    )

data class UpdateQuery(
    val table: Table,
    val assignments: List<Pair<String, Any?>>,
    val condition: Condition? = null,
) {
    fun toQuery(): Query {
        val params = mutableListOf<Any?>()
        val sql =
            buildString {
                append("UPDATE ${table.tableName} SET ")
                append(assignments.joinToString(", ") { (col, _) -> "$col = ?" })
                params.addAll(assignments.map { it.second })
                condition?.let {
                    append(" WHERE ")
                    append(it.toSql(params))
                }
            }
        return queryOf(sql, *params.toTypedArray())
    }

    val asUpdate: UpdateQueryAction get() = toQuery().asUpdate

    val asExecute: ExecuteQueryAction get() = toQuery().asExecute
}

infix fun UpdateSetClause.where(condition: Condition): UpdateQuery = UpdateQuery(table, assignments, condition)

fun UpdateSetClause.toQuery(): Query = UpdateQuery(table, assignments).toQuery()

class DeleteTarget(
    val table: Table,
)

fun deleteFrom(table: Table): DeleteTarget = DeleteTarget(table)

data class DeleteQuery(
    val table: Table,
    val condition: Condition? = null,
) {
    fun toQuery(): Query {
        val params = mutableListOf<Any?>()
        val sql =
            buildString {
                append("DELETE FROM ${table.tableName}")
                condition?.let {
                    append(" WHERE ")
                    append(it.toSql(params))
                }
            }
        return queryOf(sql, *params.toTypedArray())
    }

    val asUpdate: UpdateQueryAction get() = toQuery().asUpdate

    val asExecute: ExecuteQueryAction get() = toQuery().asExecute
}

infix fun DeleteTarget.where(condition: Condition): DeleteQuery = DeleteQuery(table, condition)
