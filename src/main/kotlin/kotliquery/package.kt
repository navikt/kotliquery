package kotliquery

import java.sql.DriverManager
import javax.sql.DataSource

/**
 * Builds Query object.
 */
fun queryOf(
    statement: String,
    vararg params: Any?,
): Query = Query(statement, params = params.toList())

fun queryOf(
    statement: String,
    paramMap: Map<String, Any?>,
): Query = Query(statement, paramMap = paramMap)

/**
 * Builds Session object.
 */
fun sessionOf(
    url: String,
    user: String,
    password: String,
    returnGeneratedKey: Boolean = false,
    strict: Boolean = false,
    queryTimeout: Int? = null,
): Session {
    val conn = DriverManager.getConnection(url, user, password)
    return Session(Connection(conn), returnGeneratedKey, strict = strict, queryTimeout = queryTimeout)
}

fun sessionOf(
    dataSource: DataSource,
    returnGeneratedKey: Boolean = false,
    strict: Boolean = false,
    queryTimeout: Int? = null,
): Session = Session(Connection(dataSource.connection), returnGeneratedKey, strict = strict, queryTimeout = queryTimeout)

fun <A : AutoCloseable, R> using(
    closeable: A?,
    f: (A) -> R,
): R = LoanPattern.using(closeable, f)

/**
 * Opens a [Session] from this [DataSource], executes [block] with the session as receiver,
 * and closes the session afterwards.
 *
 * ```kotlin
 * dataSource.withSession {
 *     run(queryOf("select id from members").map { row -> row.int("id") }.asList)
 * }
 * ```
 */
fun <A> DataSource.withSession(
    returnGeneratedKey: Boolean = false,
    strict: Boolean = false,
    queryTimeout: Int? = null,
    block: Session.() -> A,
): A = sessionOf(this, returnGeneratedKey, strict, queryTimeout).use(block)

/**
 * Opens a [Session], starts a transaction, executes [block] with the [TransactionalSession]
 * as receiver, commits on success, rolls back on exception, and closes the session afterwards.
 *
 * Combines session creation and transaction handling into a single call, reducing nesting.
 *
 * The [TransactionalSession] can be passed to other classes to share the transaction:
 * ```kotlin
 * dataSource.transaction {
 *     userRepo.save(this, user)
 *     orderRepo.save(this, order)
 * }
 * ```
 */
fun <A> DataSource.transaction(
    returnGeneratedKey: Boolean = false,
    strict: Boolean = false,
    queryTimeout: Int? = null,
    block: TransactionalSession.() -> A,
): A =
    sessionOf(this, returnGeneratedKey, strict, queryTimeout).use { session ->
        session.transaction { tx -> tx.block() }
    }
